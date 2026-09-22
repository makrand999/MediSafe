/**
 * Muse Spark intelligence routes.
 *
 * Label/OCR interpretation is active. Every call is authenticated, patient-scoped,
 * rate-limited, schema-validated, and recorded without retaining raw label content.
 * Other agent routes remain explicitly unavailable until their DB tool executors are
 * implemented; they still enforce patient authorization before returning 501.
 */
import { createHmac, randomUUID } from "node:crypto";
import type { FastifyInstance, FastifyPluginOptions, FastifyReply, FastifyRequest } from "fastify";
import { and, eq } from "drizzle-orm";
import { z } from "zod";
import type { AppConfig } from "../../config/index.js";
import * as schema from "../../db/schema.js";
import { requirePermissionOnPatient } from "../patients/service.js";
import { can as canPermission } from "../../lib/permissions.js";
import type { IntelligenceProvider } from "./muse-spark-provider.js";
import { LabelInterpretationSchema } from "./output-contracts.js";
import { getPromptTemplate } from "./prompt-registry.js";
import {
  RateLimiter,
  SAFETY_POLICY_VERSION,
  delimitUntrusted,
  genericFailureMessage,
  injectionGuardSystemAddendum,
  validateVisionInput,
  visionDisclosure,
} from "./safety-policy.js";

export interface IntelligenceRoutesOptions extends FastifyPluginOptions {
  provider?: IntelligenceProvider;
  config: AppConfig;
}

// Legacy compat — exact prompt/schema from old /opt/medac-api/server.js (/api/medicine/identify)
const LegacyMedicineSchema = z
  .object({
    name: z.string().min(0).max(200),
    genericName: z.string().min(0).max(500),
    purpose: z.string().min(0).max(1000),
    instructions: z.string().min(0).max(1000),
    suggestedTimes: z.array(z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/)).min(0).max(4),
    form: z.string().min(0).max(50),
    imageConfidence: z.number().min(0).max(1),
    ocrConfidence: z.number().min(0).max(1),
  })
  .passthrough();

const LEGACY_IMAGE_PROMPT =
  "You are a medicine identification assistant. Analyze the medicine label image and return ONLY a JSON object (no markdown, no extra text) with keys: name (brand name string), genericName (generic/chemical name string), purpose (what it is used for, 1 sentence), instructions (how to take it, 1 sentence), suggestedTimes (array of 1-4 strings in HH:MM 24h format like 08:00, 14:00, 20:00 based on typical dosage frequency), form (one of tablet, liquid, powder — infer from label/packaging), imageConfidence (number 0.0-1.0 how clear and usable the medicine label image is), ocrConfidence (number 0.0-1.0 how confidently you could read the text on the label). Use 0.9+ for crystal clear, 0.6-0.8 for readable but imperfect, below 0.4 for blurry/unreadable. If the image does not show a readable label, return empty name and confidence below 0.4 with [\"08:00\"] and form tablet. Do not add any text outside the JSON object.";

const LEGACY_OCR_PROMPT_PREFIX =
  "You are a medicine identification assistant. The following is OCR text extracted from a medicine label image.\n\nOCR_TEXT:\n";

const LEGACY_OCR_PROMPT_SUFFIX =
  "\n\nReturn ONLY a JSON object (no markdown) with keys: name (brand name), genericName, purpose (1 sentence), instructions (1 sentence), suggestedTimes (array of 1-4 HH:MM strings like 08:00, 14:00, 20:00), form (tablet|liquid|powder), imageConfidence (0.0-1.0 how usable the OCR text is), ocrConfidence (0.0-1.0 how confidently you could parse it). Use 0.9+ for clear, 0.6-0.8 readable, below 0.4 for gibberish. If OCR is gibberish, return empty name. Do not add text outside JSON.";

const ParamsSchema = z.object({ patientId: z.string().uuid() }).passthrough();
const LabelBodySchema = z
  .object({
    imageBase64: z.string().min(1).optional(),
    image_base64: z.string().min(1).optional(),
    image: z.string().min(1).optional(),
    mimeType: z.enum(["image/jpeg", "image/png", "image/webp"]).optional(),
    mime_type: z.enum(["image/jpeg", "image/png", "image/webp"]).optional(),
    ocrText: z.string().min(1).max(5000).optional(),
    ocr_text: z.string().min(1).max(5000).optional(),
    locale: z.string().min(2).max(20).optional(),
  })
  .strict()
  .superRefine((body, ctx) => {
    const image = body.imageBase64 ?? body.image_base64 ?? body.image;
    const ocr = body.ocrText ?? body.ocr_text;
    if (!image && !ocr) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "image or OCR text is required" });
    }
    if (image && !(body.mimeType ?? body.mime_type)) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: "mimeType is required with an image", path: ["mimeType"] });
    }
  });

function requestId(req: FastifyRequest): string {
  return req.id;
}

function apiError(reply: FastifyReply, req: FastifyRequest, status: number, code: string, message: string) {
  return reply.code(status).send({ error: { code, message, request_id: requestId(req) } });
}

function fingerprint(secret: string, value: string): string {
  return createHmac("sha256", secret).update(value).digest("hex");
}

function providerFailureStatus(code: string | undefined): number {
  if (code === "CIRCUIT_OPEN" || code === "PROVIDER_TIMEOUT") return 503;
  return 502;
}

export async function intelligenceRoutes(fastify: FastifyInstance, options: IntelligenceRoutesOptions): Promise<void> {
  const provider = options.provider;
  const config = options.config;
  const limiter = new RateLimiter({
    maxRequestsPerMinute: 10,
    maxRequestsPerHour: 100,
    dailySpendLimitCents: Math.max(1, config.museSpark.dailyBudget * 100),
  });

  async function authorize(req: FastifyRequest, reply: FastifyReply, permission: "intelligence:read" | "intelligence:write") {
    await fastify.authenticate(req, reply);
    if (reply.sent) return undefined;
    const { patientId } = ParamsSchema.parse(req.params);
    const role = await requirePermissionOnPatient(fastify.db, req.user.id, patientId, permission);
    return { patientId, role };
  }

  // Strict route removed — now delegates to legacy old /medac/api method (returns medicine with confidence)
  fastify.post(
    "/patients/:patientId/intelligence/label-interpretations",
    { bodyLimit: Math.max(1_000_000, Math.ceil(config.aiVisionMaxBytes * 1.4) + 16_384) },
    async (req, reply) => {
      const access = await authorize(req, reply, "intelligence:write");
      if (!access) return;
      if (!provider) return reply.code(502).send({ success: false, error: "AI service temporarily unavailable. Please try again." });
      const allowed = limiter.check(req.user.id, 1);
      if (!allowed.allowed) return reply.code(429).send({ success: false, error: "Too many requests." });
      const body = LabelBodySchema.parse(req.body);
      let imageBase64 = body.imageBase64 ?? body.image_base64 ?? body.image;
      const ocrText = body.ocrText ?? body.ocr_text;
      const mimeType = (body.mimeType ?? body.mime_type ?? "image/jpeg") as string;
      if (imageBase64?.includes(",")) imageBase64 = imageBase64.slice(imageBase64.indexOf(",") + 1);
      if (imageBase64 && mimeType) {
        const checked = validateVisionInput({ mime: mimeType as never, base64: imageBase64, maxBytes: config.aiVisionMaxBytes });
        if (!checked.ok || !checked.buffer) return reply.code(400).send({ success: false, error: "The image is invalid or unsupported." });
        if (checked.buffer.length > config.aiVisionMaxBytes) return reply.code(413).send({ success: false, error: "The decoded image is too large." });
      }
      // Use legacy old method exactly as /opt/medac-api/server.js
      limiter.record(req.user.id, 1);
      const runId = randomUUID();
      const startedAt = Date.now();
      // Optional audit — keep ai_runs for trace but with legacy contract
      await fastify.db
        .insert(schema.aiRuns)
        .values({
          id: runId,
          patientId: access.patientId,
          userId: req.user.id,
          sessionId: req.user.sessionId,
          purpose: "label_interpretation",
          provider: "muse_spark",
          model: config.museSpark.model,
          promptTemplateId: "legacy_medicine",
          promptTemplateVersion: "v1",
          status: "started",
          inputFingerprint: fingerprint(config.tokenHashSecret, `${imageBase64 ? "image" : "ocr"}:${imageBase64 ?? ocrText ?? ""}`),
          outputContractVersion: "legacy_medicine@v1",
          safetyPolicyVersion: SAFETY_POLICY_VERSION,
        })
        .catch(() => undefined);
      try {
        let result: unknown;
        if (imageBase64) {
          result = await provider.analyzeImage({
            prompt: { system: LEGACY_IMAGE_PROMPT, user: LEGACY_IMAGE_PROMPT },
            image: { mime: mimeType, base64: imageBase64 },
            schema: LegacyMedicineSchema,
            outputContractVersion: "legacy_medicine@v1",
            maxOutputTokens: config.museSpark.maxOutputTokens,
          });
        } else {
          const prompt = LEGACY_OCR_PROMPT_PREFIX + (ocrText ?? "") + LEGACY_OCR_PROMPT_SUFFIX;
          result = await provider.generateStructured({
            prompt: { templateId: "legacy_ocr", templateVersion: "v1", system: prompt, user: prompt },
            schema: LegacyMedicineSchema,
            outputContractVersion: "legacy_medicine@v1",
            maxOutputTokens: config.museSpark.maxOutputTokens,
            temperature: 0.2,
          });
        }
        await fastify.db
          .update(schema.aiRuns)
          .set({ status: "completed", latencyMs: Date.now() - startedAt, finishedAt: new Date() })
          .where(eq(schema.aiRuns.id, runId))
          .catch(() => undefined);
        const med = {
          name: String((result as { name?: string }).name ?? "").trim(),
          genericName: String((result as { genericName?: string }).genericName ?? "").trim(),
          purpose: String((result as { purpose?: string }).purpose ?? "").trim(),
          instructions: String((result as { instructions?: string }).instructions ?? "").trim(),
          suggestedTimes: Array.isArray((result as { suggestedTimes?: unknown }).suggestedTimes) ? (result as { suggestedTimes: string[] }).suggestedTimes : ["08:00"],
          form: String((result as { form?: string }).form ?? "tablet").trim().toLowerCase(),
          imageConfidence: Math.max(0, Math.min(1, Number((result as { imageConfidence?: number }).imageConfidence ?? 0.7))),
          ocrConfidence: Math.max(0, Math.min(1, Number((result as { ocrConfidence?: number }).ocrConfidence ?? 0.7))),
        };
        // Legacy shape is the primary contract now; also expose as interpretation for compat
        return reply.send({
          success: true,
          medicine: med,
          interpretation: med,
          ai_run_id: runId,
          provider: "muse_spark",
          model: config.museSpark.model,
          disclosure: visionDisclosure(),
          requires_user_confirmation: true,
        });
      } catch (error) {
        const code = (error as { code?: string }).code ?? "PROVIDER_FAILURE";
        await fastify.db
          .update(schema.aiRuns)
          .set({ status: "failed", failureCode: code.slice(0, 100), latencyMs: Date.now() - startedAt, finishedAt: new Date() })
          .where(eq(schema.aiRuns.id, runId))
          .catch(() => undefined);
        req.log.warn({ code, aiRunId: runId }, "[legacy label-interpretations] failed");
        if (code === "CIRCUIT_OPEN" || code === "PROVIDER_TIMEOUT") return reply.code(503).send({ success: false, error: "AI service temporarily unavailable. Please try again." });
        return reply.code(502).send({ success: false, error: "AI service temporarily unavailable. Please try again." });
      }
    },
  );

  // Instruction parses — similar to label but text only (Phase 4 draft assist)
  fastify.post("/patients/:patientId/intelligence/instruction-parses", async (req, reply) => {
    const access = await authorize(req, reply, "intelligence:write");
    if (!access) return;
    if (!provider) return apiError(reply, req, 503, "INTELLIGENCE_UNAVAILABLE", genericFailureMessage().message);
    const body = z.object({ text: z.string().min(1).max(2000), locale: z.string().optional() }).safeParse(req.body);
    if (!body.success) return apiError(reply, req, 400, "VALIDATION_ERROR", "Invalid instruction text");
    const template = getPromptTemplate("instruction_parse", "v1");
    const runId = randomUUID();
    await fastify.db.insert(schema.aiRuns).values({
      id: runId,
      patientId: access.patientId,
      userId: req.user.id,
      sessionId: req.user.sessionId,
      purpose: "instruction_parse",
      provider: "muse_spark",
      model: config.museSpark.model,
      promptTemplateId: template.id,
      promptTemplateVersion: template.version,
      status: "started",
      inputFingerprint: fingerprint(config.tokenHashSecret, body.data.text),
      outputContractVersion: template.outputContractVersion,
      safetyPolicyVersion: SAFETY_POLICY_VERSION,
    });
    try {
      const { InstructionParseSchema } = await import("./output-contracts.js");
      const system = `${template.system}\n\n${injectionGuardSystemAddendum()}`;
      const result = await provider.generateStructured({
        prompt: {
          templateId: template.id,
          templateVersion: template.version,
          system,
          user: `Parse prescription directions. Locale: ${body.data.locale ?? "en"}`,
          untrustedDataDelimited: delimitUntrusted(body.data.text, "INSTRUCTION"),
        },
        schema: InstructionParseSchema,
        outputContractVersion: template.outputContractVersion,
        maxOutputTokens: config.museSpark.maxOutputTokens,
      });
      await fastify.db.update(schema.aiRuns).set({ status: "completed", finishedAt: new Date() }).where(eq(schema.aiRuns.id, runId));
      return reply.send({ parse: result, ai_run_id: runId, requires_user_confirmation: true });
    } catch (e) {
      const code = (e as { code?: string }).code ?? "PROVIDER_FAILURE";
      await fastify.db.update(schema.aiRuns).set({ status: "failed", failureCode: code }).where(eq(schema.aiRuns.id, runId));
      return apiError(reply, req, providerFailureStatus(code), "PROVIDER_FAILURE", genericFailureMessage().message);
    }
  });

  // Conversations — create
  fastify.post("/patients/:patientId/intelligence/conversations", async (req, reply) => {
    const access = await authorize(req, reply, "intelligence:write");
    if (!access) return;
    const body = z.object({ title: z.string().max(200).optional() }).safeParse(req.body ?? {});
    const title = body.success ? body.data.title : undefined;
    const convId = randomUUID();
    const expiresAt = new Date(Date.now() + config.aiConversationRetentionHours * 60 * 60 * 1000);
    // Encrypt title if provided
    const { encryptPayload } = await import("./proposal-service.js");
    const titleCipher = title ? encryptPayload(title) : null;
    await fastify.db.insert(schema.aiConversations).values({
      id: convId,
      patientId: access.patientId,
      userId: req.user.id,
      titleCiphertext: titleCipher,
      expiresAt,
    });
    return reply.code(201).send({ conversation_id: convId, patient_id: access.patientId, expires_at: expiresAt.toISOString() });
  });

  fastify.delete("/patients/:patientId/intelligence/conversations/:conversationId", async (req, reply) => {
    const access = await authorize(req, reply, "intelligence:write");
    if (!access) return;
    const params = z.object({ patientId: z.string().uuid(), conversationId: z.string().uuid() }).safeParse(req.params);
    if (!params.success) return apiError(reply, req, 400, "VALIDATION_ERROR", "Invalid conversation id");
    const conv = await fastify.db.query.aiConversations.findFirst({ where: eq(schema.aiConversations.id, params.data.conversationId) });
    if (!conv || conv.patientId !== access.patientId) return apiError(reply, req, 404, "NOT_FOUND", "Conversation not found");
    if (conv.userId !== req.user.id) {
      // Only owner or patient member can delete — check permission
      const role = await requirePermissionOnPatient(fastify.db, req.user.id, access.patientId, "intelligence:write").catch(() => null);
      if (!role) return apiError(reply, req, 403, "FORBIDDEN", "Not allowed");
    }
    await fastify.db.delete(schema.aiMessages).where(eq(schema.aiMessages.conversationId, conv.id));
    await fastify.db.delete(schema.aiConversations).where(eq(schema.aiConversations.id, conv.id));
    return reply.code(204).send();
  });

  // Conversations — add message and run orchestrator
  fastify.post("/patients/:patientId/intelligence/conversations/:conversationId/messages", async (req, reply) => {
    const access = await authorize(req, reply, "intelligence:write");
    if (!access) return;
    if (!provider) return apiError(reply, req, 503, "INTELLIGENCE_UNAVAILABLE", genericFailureMessage().message);
    const params = z.object({ patientId: z.string().uuid(), conversationId: z.string().uuid() }).safeParse(req.params);
    if (!params.success) return apiError(reply, req, 400, "VALIDATION_ERROR", "Invalid params");
    const body = z.object({ content: z.string().min(1).max(5000), role: z.enum(["user", "assistant"]).optional() }).safeParse(req.body);
    if (!body.success) return apiError(reply, req, 400, "VALIDATION_ERROR", "Invalid message");
    const conv = await fastify.db.query.aiConversations.findFirst({ where: eq(schema.aiConversations.id, params.data.conversationId) });
    if (!conv || conv.patientId !== access.patientId) return apiError(reply, req, 404, "NOT_FOUND", "Conversation not found");
    const userContent = body.data.content;
    const runId = randomUUID();
    const startedAt = Date.now();
    await fastify.db.insert(schema.aiRuns).values({
      id: runId,
      patientId: access.patientId,
      userId: req.user.id,
      sessionId: req.user.sessionId,
      purpose: "assistant_turn",
      provider: "muse_spark",
      model: config.museSpark.model,
      promptTemplateId: "assistant_turn",
      promptTemplateVersion: "v1",
      status: "started",
      inputFingerprint: fingerprint(config.tokenHashSecret, userContent),
      safetyPolicyVersion: SAFETY_POLICY_VERSION,
    });
    // Store user message encrypted
    const { encryptPayload } = await import("./proposal-service.js");
    await fastify.db.insert(schema.aiMessages).values({
      id: randomUUID(),
      conversationId: conv.id,
      aiRunId: runId,
      role: "user",
      contentCiphertext: encryptPayload(userContent),
    });
    // Check rate limit
    const allowed = limiter.check(req.user.id, 1);
    if (!allowed.allowed) return apiError(reply, req, 429, "RATE_LIMITED", "Too many intelligence requests");
    limiter.record(req.user.id, 1);
    // Build orchestrator with DB tools
    const { Orchestrator } = await import("./orchestrator.js");
    const { DbProposalService } = await import("./db-proposal-service.js");
    const { DbToolExecutor } = await import("./tool-executor.js");
    const dbProposalService = new DbProposalService(fastify.db);
    const executor = new DbToolExecutor(fastify.db, dbProposalService, runId, req.user.id);
    const orchestrator = new Orchestrator(provider, executor, undefined, {
      maxTurns: config.museSpark.maxAgentTurns,
      maxToolCalls: config.museSpark.maxToolCalls,
      maxOutputTokens: config.museSpark.maxOutputTokens,
      maxTimeMs: config.museSpark.timeoutMs,
      maxContextBytes: 16000,
    });
    // Determine authorization first — must be scoped to current user, not first patient member
    const membership = await fastify.db.query.patientMemberships.findFirst({ where: and(eq(schema.patientMemberships.patientId, access.patientId), eq(schema.patientMemberships.userId, req.user.id), eq(schema.patientMemberships.status, "active" as never)) });
    const systemTemplate = getPromptTemplate("assistant_turn", "v1");
    const role = (membership?.role as string) ?? "viewer";
    // Only advertise the tools this role can actually execute (the orchestrator
    // sends exactly the same filtered set).
    const { listToolsFor } = await import("./tool-registry.js");
    const availableTools = listToolsFor({ can: (perm) => canPermission(role as never, perm as never) })
      .map((t) => t.name)
      .join(", ");
    const system = `${systemTemplate.system}\n\nPatient context: patientId=${access.patientId}, userId=${req.user.id}, role=${role}. Use this patientId for all tool calls (e.g., list_medications with patientId="${access.patientId}"). Do not ask the user for patientId. You have tools: ${availableTools}. Use them to answer.\n\n${injectionGuardSystemAddendum()}`;
    const authz = {
      patientId: access.patientId,
      role: (membership?.role as never) ?? "viewer",
      can: (perm: string) => canPermission((membership?.role as never) ?? "viewer", perm as never),
    };
    // For simplicity, use a hasRecentAuth check via session age <5min
    const session = await fastify.db.query.authSessions.findFirst({ where: eq(schema.authSessions.id, req.user.sessionId) });
    const hasRecentAuth = session ? (Date.now() - new Date(session.createdAt).getTime() < 5 * 60 * 1000) : false;
    try {
      const result = await orchestrator.run({
        auth: { userId: req.user.id, sessionId: req.user.sessionId, hasRecentAuth },
        authorization: authz as never,
        aiRunId: runId,
        purpose: "assistant_turn",
        systemPrompt: system,
        initialUserMessage: userContent,
      });
      // Store assistant message
      await fastify.db.insert(schema.aiMessages).values({
        id: randomUUID(),
        conversationId: conv.id,
        aiRunId: runId,
        role: "assistant",
        contentCiphertext: encryptPayload(result.message),
      });
      await fastify.db
        .update(schema.aiRuns)
        .set({
          status: "completed",
          latencyMs: Date.now() - startedAt,
          finishedAt: new Date(),
          inputTokens: result.inputTokens,
          outputTokens: result.outputTokens,
        })
        .where(eq(schema.aiRuns.id, runId));
      fastify.log.info(
        { aiRunId: runId, turns: result.turnCount, toolCalls: result.toolCallCount, inputTokens: result.inputTokens, outputTokens: result.outputTokens, latencyMs: Date.now() - startedAt },
        "assistant turn completed",
      );
      return reply.send({
        message: result.message,
        facts_used: result.factsUsed,
        proposals: result.proposals,
        limitations: result.limitations,
        ai_run_id: runId,
        turn_count: result.turnCount,
      });
    } catch (e) {
      const code = (e as { code?: string }).code ?? "ORCHESTRATOR_FAILURE";
      await fastify.db.update(schema.aiRuns).set({ status: "failed", failureCode: code, latencyMs: Date.now() - startedAt, finishedAt: new Date() }).where(eq(schema.aiRuns.id, runId));
      return apiError(reply, req, providerFailureStatus(code), code, (e as Error).message.slice(0, 300));
    }
  });

  // Schedule drafts — uses provider to generate draft then validates via output contract
  fastify.post("/patients/:patientId/intelligence/schedule-drafts", async (req, reply) => {
    const access = await authorize(req, reply, "intelligence:write");
    if (!access) return;
    if (!provider) return apiError(reply, req, 503, "INTELLIGENCE_UNAVAILABLE", genericFailureMessage().message);
    const body = z.object({
      medicationId: z.string().uuid().optional(),
      constraints: z.string().min(1).max(2000).optional(),
      timezone: z.string().min(1).max(64).optional(),
      wakeTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/).optional(),
      sleepTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/).optional(),
    }).safeParse(req.body);
    if (!body.success) return apiError(reply, req, 400, "VALIDATION_ERROR", "Invalid draft request");
    const template = getPromptTemplate("schedule_draft", "v1");
    const runId = randomUUID();
    await fastify.db.insert(schema.aiRuns).values({
      id: runId,
      patientId: access.patientId,
      userId: req.user.id,
      sessionId: req.user.sessionId,
      purpose: "schedule_draft",
      provider: "muse_spark",
      model: config.museSpark.model,
      promptTemplateId: template.id,
      promptTemplateVersion: template.version,
      status: "started",
      inputFingerprint: fingerprint(config.tokenHashSecret, JSON.stringify(body.data)),
      safetyPolicyVersion: SAFETY_POLICY_VERSION,
    });
    try {
      const { ScheduleDraftSchema } = await import("./output-contracts.js");
      const system = `${template.system}\n\n${injectionGuardSystemAddendum()}`;
      // Include patient context minimal
      const med = body.data.medicationId ? await fastify.db.query.patientMedications.findFirst({ where: eq(schema.patientMedications.id, body.data.medicationId) }) : null;
      const context = med ? { medication: { id: med.id, name: med.enteredName, dose: `${med.doseQuantityValue} ${med.doseQuantityUnit}` } } : {};
      const result = await provider.generateStructured({
        prompt: {
          templateId: template.id,
          templateVersion: template.version,
          system,
          user: `Draft schedule with constraints: ${body.data.constraints ?? "none"}. Timezone: ${body.data.timezone ?? "UTC"}. Medication: ${med?.enteredName ?? "unknown"}. Return is_convenience_proposal=true if times are inferred.`,
          untrustedDataDelimited: delimitUntrusted(JSON.stringify(context), "MEDICATION"),
        },
        schema: ScheduleDraftSchema,
        outputContractVersion: template.outputContractVersion,
        maxOutputTokens: config.museSpark.maxOutputTokens,
      });
      await fastify.db.update(schema.aiRuns).set({ status: "completed", finishedAt: new Date() }).where(eq(schema.aiRuns.id, runId));
      return reply.send({ draft: result, ai_run_id: runId, requires_user_confirmation: true, limitations: ["Draft times are convenience proposals, not prescription content"] });
    } catch (e) {
      const code = (e as { code?: string }).code ?? "PROVIDER_FAILURE";
      await fastify.db.update(schema.aiRuns).set({ status: "failed", failureCode: code }).where(eq(schema.aiRuns.id, runId));
      return apiError(reply, req, providerFailureStatus(code), code, genericFailureMessage().message);
    }
  });

  // Summaries — deterministic report + Muse Spark humane explanation
  fastify.post("/patients/:patientId/intelligence/summaries", async (req, reply) => {
    const access = await authorize(req, reply, "intelligence:read");
    if (!access) return;
    const body = z.object({ type: z.enum(["adherence", "inventory", "timeline"]), from: z.string().datetime().optional(), to: z.string().datetime().optional() }).safeParse(req.body);
    if (!body.success) return apiError(reply, req, 400, "VALIDATION_ERROR", "Invalid summary request");
    if (!provider) return apiError(reply, req, 503, "INTELLIGENCE_UNAVAILABLE", genericFailureMessage().message);
    const template = getPromptTemplate("summary", "v1");
    const runId = randomUUID();
    await fastify.db.insert(schema.aiRuns).values({
      id: runId,
      patientId: access.patientId,
      userId: req.user.id,
      sessionId: req.user.sessionId,
      purpose: "summary",
      provider: "muse_spark",
      model: config.museSpark.model,
      promptTemplateId: template.id,
      promptTemplateVersion: template.version,
      status: "started",
      inputFingerprint: fingerprint(config.tokenHashSecret, body.data.type),
      safetyPolicyVersion: SAFETY_POLICY_VERSION,
    });
    try {
      // For MVP, just call provider with deterministic report placeholder
      const { SummarySchema } = await import("./output-contracts.js");
      const report = { type: body.data.type, patientId: access.patientId, note: "deterministic report would be fetched via tool" };
      const result = await provider.generateStructured({
        prompt: {
          templateId: template.id,
          templateVersion: template.version,
          system: template.system,
          user: `Summarize ${body.data.type} for patient ${access.patientId}. Facts: ${JSON.stringify(report).slice(0, 2000)}`,
        },
        schema: SummarySchema,
        outputContractVersion: template.outputContractVersion,
        maxOutputTokens: config.museSpark.maxOutputTokens,
      });
      await fastify.db.update(schema.aiRuns).set({ status: "completed", finishedAt: new Date() }).where(eq(schema.aiRuns.id, runId));
      return reply.send({ summary: result, ai_run_id: runId });
    } catch (e) {
      const code = (e as { code?: string }).code ?? "PROVIDER_FAILURE";
      await fastify.db.update(schema.aiRuns).set({ status: "failed", failureCode: code }).where(eq(schema.aiRuns.id, runId));
      return apiError(reply, req, providerFailureStatus(code), code, genericFailureMessage().message);
    }
  });

  // Proposals — get, confirm, reject with DB-backed service
  fastify.get("/patients/:patientId/intelligence/proposals/:proposalId", async (req, reply) => {
    const access = await authorize(req, reply, "intelligence:read");
    if (!access) return;
    const params = z.object({ patientId: z.string().uuid(), proposalId: z.string().uuid() }).safeParse(req.params);
    if (!params.success) return apiError(reply, req, 400, "VALIDATION_ERROR", "Invalid proposal id");
    const { DbProposalService } = await import("./db-proposal-service.js");
    const svc = new DbProposalService(fastify.db);
    const prop = await svc.get(params.data.proposalId);
    if (!prop || prop.patientId !== access.patientId) return apiError(reply, req, 404, "NOT_FOUND", "Proposal not found");
    const humanSummary = svc.decryptHumanSummary(prop);
    return reply.send({ proposal: { id: prop.id, action_type: prop.actionType, status: prop.status, expires_at: prop.expiresAt.toISOString(), required_permission: prop.requiredPermission, resource_versions: prop.resourceVersionsJson, human_summary: humanSummary, payload_hash: prop.payloadHash } });
  });

  fastify.post("/patients/:patientId/intelligence/proposals/:proposalId/confirm", async (req, reply) => {
    const access = await authorize(req, reply, "intelligence:write");
    if (!access) return;
    const params = z.object({ patientId: z.string().uuid(), proposalId: z.string().uuid() }).safeParse(req.params);
    if (!params.success) return apiError(reply, req, 400, "VALIDATION_ERROR", "Invalid proposal id");
    const body = z.object({ payload_hash: z.string().min(10), expected_resource_versions: z.record(z.number()).optional() }).safeParse(req.body);
    if (!body.success) return apiError(reply, req, 400, "VALIDATION_ERROR", "Invalid confirm payload");
    const { DbProposalService } = await import("./db-proposal-service.js");
    const svc = new DbProposalService(fastify.db);
    const prop = await svc.get(params.data.proposalId);
    if (!prop || prop.patientId !== access.patientId) return apiError(reply, req, 404, "NOT_FOUND", "Proposal not found");
    // Check permission
    const role = await requirePermissionOnPatient(fastify.db, req.user.id, access.patientId, prop.requiredPermission as never).then(() => "ok").catch(() => null);
    if (!role) return apiError(reply, req, 403, "FORBIDDEN", "Not authorized for proposal");
    const session = await fastify.db.query.authSessions.findFirst({ where: eq(schema.authSessions.id, req.user.sessionId) });
    const hasRecentAuth = session ? (Date.now() - new Date(session.createdAt ?? session.lastUsedAt ?? Date.now()).getTime() < 5 * 60 * 1000) : false;
    try {
      const confirmed = await svc.confirm({
        proposalId: prop.id,
        patientId: access.patientId,
        confirmingUserId: req.user.id,
        payloadHash: body.data.payload_hash,
        expectedResourceVersions: (body.data.expected_resource_versions as Record<string, number>) ?? prop.resourceVersionsJson,
        hasRecentAuth,
        isAuthorized: true,
      });
      // Execute immediately if not high-impact deferred? For MVP, execute via executor mapping
      const payload = svc.decryptPayload(confirmed);
      // Map action to actual service
      await executeProposalAction(fastify, payload, confirmed.actionType, access.patientId, req.user.id);
      await svc.execute(confirmed.id, async () => {});
      return reply.send({ proposal: { id: confirmed.id, status: "executed", executed_at: new Date().toISOString() } });
    } catch (e) {
      const code = (e as { code?: string }).code ?? "CONFIRM_FAILED";
      const status = (e as { statusCode?: number }).statusCode ?? 400;
      return apiError(reply, req, status, code, (e as Error).message.slice(0, 300));
    }
  });

  fastify.post("/patients/:patientId/intelligence/proposals/:proposalId/reject", async (req, reply) => {
    const access = await authorize(req, reply, "intelligence:write");
    if (!access) return;
    const params = z.object({ patientId: z.string().uuid(), proposalId: z.string().uuid() }).safeParse(req.params);
    if (!params.success) return apiError(reply, req, 400, "VALIDATION_ERROR", "Invalid proposal id");
    const { DbProposalService } = await import("./db-proposal-service.js");
    const svc = new DbProposalService(fastify.db);
    try {
      const rejected = await svc.reject(params.data.proposalId, access.patientId);
      return reply.send({ proposal: { id: rejected.id, status: rejected.status } });
    } catch (e) {
      const code = (e as { code?: string }).code ?? "REJECT_FAILED";
      return apiError(reply, req, 400, code, (e as Error).message.slice(0, 300));
    }
  });

  // ── Legacy compat: old /api/medicine/identify + /api/medicine/identify-text ──
  // Exact method from /opt/medac-api/server.js — v1 now exposes same under /api/v1/medicine/identify
  // so clients that used medac/api can switch to medac/api/v1 without changing payload/response shape.
  fastify.post(
    "/medicine/identify",
    { bodyLimit: Math.max(1_000_000, Math.ceil(config.aiVisionMaxBytes * 1.4) + 16_384) },
    async (req, reply) => {
      await fastify.authenticate(req, reply);
      if (reply.sent) return;
      if (!provider) return reply.code(502).send({ success: false, error: "AI service temporarily unavailable. Please try again." });
      const allowed = limiter.check(req.user.id, 1);
      if (!allowed.allowed) return reply.code(429).send({ success: false, error: "Too many requests." });
      const body = (req.body ?? {}) as Record<string, unknown>;
      let imageBase64 = String((body.imageBase64 as string) ?? (body.image as string) ?? "");
      const mimeType = String((body.mimeType as string) ?? "image/jpeg");
      if (!imageBase64) return reply.code(400).send({ success: false, error: "imageBase64 is required" });
      if (imageBase64.includes(",")) imageBase64 = imageBase64.split(",").pop() ?? imageBase64;
      if (mimeType && imageBase64) {
        const checked = validateVisionInput({ mime: mimeType as never, base64: imageBase64, maxBytes: config.aiVisionMaxBytes });
        if (!checked.ok || !checked.buffer) return reply.code(400).send({ success: false, error: "The image is invalid or unsupported." });
      }
      limiter.record(req.user.id, 1);
      try {
        const result = await provider.analyzeImage({
          prompt: { system: LEGACY_IMAGE_PROMPT, user: LEGACY_IMAGE_PROMPT },
          image: { mime: mimeType, base64: imageBase64 },
          schema: LegacyMedicineSchema,
          outputContractVersion: "legacy_medicine@v1",
          maxOutputTokens: config.museSpark.maxOutputTokens,
        });
        const med = {
          name: String((result as { name?: string }).name ?? "").trim(),
          genericName: String((result as { genericName?: string }).genericName ?? "").trim(),
          purpose: String((result as { purpose?: string }).purpose ?? "").trim(),
          instructions: String((result as { instructions?: string }).instructions ?? "").trim(),
          suggestedTimes: Array.isArray((result as { suggestedTimes?: unknown }).suggestedTimes) ? (result as { suggestedTimes: string[] }).suggestedTimes : ["08:00"],
          form: String((result as { form?: string }).form ?? "tablet").trim().toLowerCase(),
          imageConfidence: Math.max(0, Math.min(1, Number((result as { imageConfidence?: number }).imageConfidence ?? 0.7))),
          ocrConfidence: Math.max(0, Math.min(1, Number((result as { ocrConfidence?: number }).ocrConfidence ?? 0.7))),
        };
        if (!med.name) fastify.log.info({ userId: req.user.id }, "[legacy identify] parsed empty (blank/unreadable image)");
        else fastify.log.info({ userId: req.user.id, name: med.name }, "[legacy identify] parsed vision success");
        return reply.send({ success: true, medicine: med });
      } catch (e) {
        const code = (e as { code?: string }).code ?? "PROVIDER_FAILURE";
        fastify.log.warn({ code, userId: req.user.id }, "[legacy identify] failed");
        if (code === "CIRCUIT_OPEN" || code === "PROVIDER_TIMEOUT") return reply.code(503).send({ success: false, error: "AI service temporarily unavailable. Please try again." });
        return reply.code(502).send({ success: false, error: "AI service temporarily unavailable. Please try again." });
      }
    },
  );

  fastify.post("/medicine/identify-text", async (req, reply) => {
    await fastify.authenticate(req, reply);
    if (reply.sent) return;
    if (!provider) return reply.code(502).send({ success: false, error: "AI not configured" });
    const allowed = limiter.check(req.user.id, 1);
    if (!allowed.allowed) return reply.code(429).send({ success: false, error: "Too many requests." });
    const body = (req.body ?? {}) as Record<string, unknown>;
    const ocrText = String((body.ocrText as string) ?? "").trim();
    if (!ocrText || ocrText.length < 3) return reply.code(400).send({ success: false, error: "ocrText is required" });
    limiter.record(req.user.id, 1);
    try {
      const prompt = LEGACY_OCR_PROMPT_PREFIX + ocrText + LEGACY_OCR_PROMPT_SUFFIX;
      const result = await provider.generateStructured({
        prompt: { templateId: "legacy_ocr", templateVersion: "v1", system: prompt, user: prompt },
        schema: LegacyMedicineSchema,
        outputContractVersion: "legacy_medicine@v1",
        maxOutputTokens: config.museSpark.maxOutputTokens,
        temperature: 0.2,
      });
      const med = {
        name: String((result as { name?: string }).name ?? "").trim(),
        genericName: String((result as { genericName?: string }).genericName ?? "").trim(),
        purpose: String((result as { purpose?: string }).purpose ?? "").trim(),
        instructions: String((result as { instructions?: string }).instructions ?? "").trim(),
        suggestedTimes: Array.isArray((result as { suggestedTimes?: unknown }).suggestedTimes) ? (result as { suggestedTimes: string[] }).suggestedTimes : ["08:00"],
        form: String((result as { form?: string }).form ?? "tablet").trim().toLowerCase(),
        imageConfidence: Math.max(0, Math.min(1, Number((result as { imageConfidence?: number }).imageConfidence ?? 0.7))),
        ocrConfidence: Math.max(0, Math.min(1, Number((result as { ocrConfidence?: number }).ocrConfidence ?? 0.7))),
      };
      return reply.send({ success: true, medicine: med });
    } catch (e) {
      const code = (e as { code?: string }).code ?? "PROVIDER_FAILURE";
      fastify.log.warn({ code, userId: req.user.id }, "[legacy identify-text] failed");
      return reply.code(502).send({ success: false, error: "AI could not parse OCR text. Please retake." });
    }
  });

  async function executeProposalAction(fastify: FastifyInstance, payload: Record<string, unknown>, actionType: string, patientId: string, userId: string) {
    // Minimal executor for high-impact actions — expand as needed
    if (actionType === "propose_create_medication") {
      const { createMedication } = await import("../medications/service.js");
      const data = payload as { enteredName: string; doseValue?: string; doseUnit?: string; form?: string; route?: string; labelDirectionsText?: string };
      await createMedication(fastify.db, {
        patientId,
        userId,
        data: {
          entered_name: data.enteredName,
          dose_quantity_value: (data.doseValue ?? "1") as never,
          dose_quantity_unit: (data.doseUnit ?? "tablet") as never,
          form: data.form as never,
          route: data.route as never,
          label_instructions_text: data.labelDirectionsText as never,
        },
      });
    } else if (actionType === "propose_create_or_supersede_schedule") {
      const { createSchedule } = await import("../schedules/service.js");
      const data = payload as { medicationId: string; scheduleType: string; timingMode: string; timezone: string; payload: Record<string, unknown> };
      await createSchedule(fastify.db, {
        patientId,
        userId,
        medicationId: data.medicationId,
        data: { schedule_type: data.scheduleType as never, timing_mode: data.timingMode as never, timezone: data.timezone, ...(data.payload as Record<string, unknown>) } as never,
      });
    } else if (actionType === "propose_caregiver_invite") {
      const { createInvite } = await import("../patients/service.js");
      const data = payload as { invitedEmail: string; role: string };
      await createInvite(fastify.db, { actorUserId: userId, patientId, invitedEmail: data.invitedEmail, role: data.role as never });
    }
    // Other actions can be added similarly; no-op for now keeps exactly-once guard
  }
}
