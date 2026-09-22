/**
 * Pi harness — the assistant turn on Pi's Agent loop.
 *
 * Replaces our custom tool loop for the flagged assistant path while keeping
 * every medac guarantee:
 *  - tools and their permissions come from our registry (tools.ts)
 *  - authorization is enforced in `beforeToolCall`
 *  - the turn budget is enforced in `finishTurn`
 *  - the model allowlist and request timeout are enforced in the stream guard
 *  - facts/proposals are collected from tool results and returned to the route
 *  - audit metrics (turns, tool calls, tokens) are returned to the caller
 */

import { Agent, type AgentEvent, type StreamFn } from "@earendil-works/pi-agent-core";
import { type Message, type Model } from "@earendil-works/pi-ai";
// streamSimple lives in pi-ai's compat entry (api-dispatch over the model's wire format).
import { streamSimple } from "@earendil-works/pi-ai/compat";
import { assertAllowedModel } from "../model-registry.js";
import { buildAgentTools } from "./tools.js";
import type { OrchestratorResponse, PatientAuthorization, ToolExecutor } from "../orchestrator.js";

export interface PiHistoryMessage {
  role: "user" | "assistant";
  content: string;
}

export interface PiHarnessRequest {
  model: Model<any>;
  apiKey: string;
  system: string;
  question: string;
  history?: PiHistoryMessage[];
  authorization: PatientAuthorization;
  executor: ToolExecutor;
  aiRunId: string;
  maxTurns?: number;
  maxOutputTokens?: number;
  timeoutMs?: number;
  /** Injectable for tests; defaults to the guarded gateway stream. */
  streamFn?: StreamFn;
  onEvent?: (event: AgentEvent) => void;
}

export type PiHarnessResult = OrchestratorResponse;

const DEFAULT_MAX_TURNS = 6;

/**
 * Wraps pi-ai's OpenAI-compatible stream with our safety rails: the model must
 * be on the allowlist and the request is bounded by a timeout. Provider errors
 * are surfaced to Pi, which turns them into an error assistant message.
 */
export function createGuardedStreamFn(timeoutMs: number): StreamFn {
  return (model, context, options) => {
    assertAllowedModel(String(model.id));
    const timeout = AbortSignal.timeout(timeoutMs);
    const signal = options?.signal ? AbortSignal.any([options.signal, timeout]) : timeout;
    return streamSimple(model, context, { ...options, signal });
  };
}

/** Convert our stored (decrypted) conversation into Pi's message format. */
export function toAgentMessages(history: PiHistoryMessage[], model: Model<any>): Message[] {
  return history.map((m) =>
    m.role === "user"
      ? { role: "user" as const, content: m.content, timestamp: Date.now() }
      : {
          role: "assistant" as const,
          content: [{ type: "text" as const, text: m.content }],
          api: model.api,
          provider: model.provider,
          model: model.id,
          usage: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, totalTokens: 0, cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, total: 0 } },
          stopReason: "stop" as const,
          timestamp: Date.now(),
        },
  );
}

/** Sums provider usage across the assistant messages of a transcript. */
export function sumUsage(messages: Message[]): { inputTokens: number; outputTokens: number } {
  let inputTokens = 0;
  let outputTokens = 0;
  for (const m of messages) {
    if (m.role !== "assistant" || !m.usage) continue;
    inputTokens += m.usage.input ?? 0;
    outputTokens += m.usage.output ?? 0;
  }
  return { inputTokens, outputTokens };
}

function lastAssistantText(messages: Message[]): string {
  for (let i = messages.length - 1; i >= 0; i--) {
    const m = messages[i];
    if (m.role !== "assistant") continue;
    const text = (m.content ?? [])
      .filter((c): c is { type: "text"; text: string } => c.type === "text")
      .map((c) => c.text)
      .join("")
      .trim();
    if (text) return text;
  }
  return "";
}

export async function runPiAssistantTurn(request: PiHarnessRequest): Promise<PiHarnessResult> {
  const maxTurns = request.maxTurns ?? DEFAULT_MAX_TURNS;
  const bundle = buildAgentTools({
    authorization: request.authorization,
    executor: request.executor,
    aiRunId: request.aiRunId,
  });

  const factsUsed: PiHarnessResult["factsUsed"] = [];
  const proposals: PiHarnessResult["proposals"] = [];
  let turnCount = 0;
  let toolCallCount = 0;

  const agent = new Agent({
    initialState: {
      systemPrompt: request.system,
      model: request.model,
      tools: bundle.tools,
      messages: toAgentMessages(request.history ?? [], request.model),
    },
    streamFn: request.streamFn ?? createGuardedStreamFn(request.timeoutMs ?? 30_000),
    getApiKey: () => request.apiKey,
    toolExecution: "parallel",
    beforeToolCall: async ({ toolCall }) => {
      const authz = bundle.authorize(toolCall.name);
      if (!authz.allowed) return { block: true, reason: authz.reason ?? "NOT_AUTHORIZED" };
      return undefined;
    },
    afterToolCall: async ({ result }) => {
      const details = result.details as { kind?: string; result?: unknown; proposal?: PiHarnessResult["proposals"][number] } | undefined;
      if (details?.kind === "proposal" && details.proposal) proposals.push(details.proposal);
      if (details?.kind === "read" && details.result) collectFacts(details.result, factsUsed);
      return undefined;
    },
    finishTurn: async (turn) => {
      turnCount++;
      if (turnCount >= maxTurns) return { action: "end" };
      // Only ask for another provider request when the turn actually requested
      // tools; a plain assistant reply is already the final answer.
      const requestedTools = (turn.message.content ?? []).some((c) => c.type === "toolCall");
      return requestedTools ? { action: "continue" } : { action: "end" };
    },
  });

  const unsubscribe = agent.subscribe((event) => {
    if (event.type === "tool_execution_start") toolCallCount++;
    request.onEvent?.(event);
  });

  try {
    await agent.prompt(request.question);
  } finally {
    unsubscribe();
  }

  const messages = agent.state.messages as Message[];
  const { inputTokens, outputTokens } = sumUsage(messages);

  const message = lastAssistantText(messages);
  return {
    // Budget exhausted mid-tool-calls (rare: Pi stops naturally in ~2 turns):
    // never fabricate an answer from partial tool data.
    message: message || "I could not finish that request. Please try again or ask a simpler question.",
    factsUsed,
    proposals,
    limitations: message ? [] : ["TURN_BUDGET_EXHAUSTED"],
    turnCount,
    toolCallCount,
    inputTokens,
    outputTokens,
  };
}

/** Mirrors the orchestrator's heuristic: collect ids from tool results. */
function collectFacts(result: unknown, out: PiHarnessResult["factsUsed"]): void {
  if (result === null || typeof result !== "object") return;
  const r = result as Record<string, unknown>;
  if (typeof r.id === "string" && typeof r.type === "string") {
    out.push({ type: r.type, id: r.id, version: r.version as number | undefined });
  }
  if (Array.isArray(r.items)) {
    for (const item of r.items.slice(0, 5)) {
      if (item && typeof item === "object" && typeof (item as Record<string, unknown>).id === "string") {
        out.push({ type: "medication", id: (item as Record<string, unknown>).id as string });
      }
    }
  }
}
