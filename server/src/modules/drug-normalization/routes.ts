/**
 * Drug normalization routes §8.8
 */
import type { FastifyInstance, FastifyRequest, FastifyReply } from "fastify";
import { z } from "zod";
import { getDrugNormalizationProvider } from "./provider.js";

function sendValidationError(req: FastifyRequest, reply: FastifyReply, issues: unknown) {
  return reply.code(400).send({ error: { code: "VALIDATION_ERROR", message: "Request validation failed.", request_id: (req as unknown as { id: string }).id, details: { validation: issues } } });
}

export async function drugNormalizationRoutes(fastify: FastifyInstance): Promise<void> {
  fastify.get("/drug-normalization/search", async (req, reply) => {
    const parsed = z.object({ q: z.string().min(2).max(100) }).safeParse((req as { query: unknown }).query);
    if (!parsed.success) return sendValidationError(req, reply, parsed.error.issues);
    const provider = getDrugNormalizationProvider();
    const result = await provider.searchByName(parsed.data.q);
    return reply.send(result);
  });

  fastify.post("/drug-normalization/resolve-ndc", async (req, reply) => {
    const parsed = z.object({ ndc: z.string().min(8).max(20) }).safeParse(req.body);
    if (!parsed.success) return sendValidationError(req, reply, parsed.error.issues);
    const provider = getDrugNormalizationProvider();
    const result = await provider.resolveNdc(parsed.data.ndc);
    return reply.send(result);
  });

  fastify.get("/drug-normalization/concepts/:rxcui", async (req, reply) => {
    const parsed = z.object({ rxcui: z.string().regex(/^\d+$/) }).safeParse((req as { params: unknown }).params);
    if (!parsed.success) return sendValidationError(req, reply, parsed.error.issues);
    const provider = getDrugNormalizationProvider();
    const concept = await provider.getConcept(parsed.data.rxcui);
    return reply.send(concept);
  });
}
