import { describe, it, expect } from "vitest";
import { createFauxCore, fauxAssistantMessage, fauxToolCall } from "@earendil-works/pi-ai/providers/faux";
import type { StreamFn } from "@earendil-works/pi-agent-core";
import { runPiAssistantTurn, toAgentMessages, createGuardedStreamFn, sumUsage } from "../../../src/modules/intelligence/pi/harness.js";
import { buildAgentTools } from "../../../src/modules/intelligence/pi/tools.js";
import { buildGatewayModel } from "../../../src/modules/intelligence/pi/model.js";
import type { PatientAuthorization, ToolExecutor } from "../../../src/modules/intelligence/orchestrator.js";

const MODEL = buildGatewayModel({ model: "gemini-3.8-flash-low", baseUrl: "http://127.0.0.1:8045/v1" });

const VIEWER: PatientAuthorization = {
  patientId: "44444444-4444-4444-8444-444444444444",
  role: "viewer",
  can: (perm) => ["medication:read", "schedule:read", "inventory:read", "adherence:read"].includes(perm),
};

function stubExecutor(results: Record<string, unknown> = {}) {
  const calls: Array<{ name: string; args: unknown }> = [];
  const executor: ToolExecutor = {
    async executeRead(name, args) {
      calls.push({ name, args });
      return results[name] ?? { id: `${name}-1`, type: "medication", items: [{ id: "m1" }] };
    },
    async createProposal(name) {
      calls.push({ name, args: undefined });
      return { proposalId: `proposal-${name}`, expiresAt: new Date().toISOString() };
    },
  };
  return { executor, calls };
}

/** Scripts the model through Pi's faux provider. */
function fauxStream(responses: Parameters<ReturnType<typeof createFauxCore>["setResponses"]>[0]) {
  const faux = createFauxCore({ models: [{ id: MODEL.id }], api: "openai-completions", provider: "medac-gateway" });
  faux.setResponses(responses);
  return { streamFn: faux.streamSimple as StreamFn, faux };
}

function run(streamFn: StreamFn, executor: ToolExecutor, overrides: Partial<Parameters<typeof runPiAssistantTurn>[0]> = {}) {
  return runPiAssistantTurn({
    model: MODEL,
    apiKey: "test-key",
    system: "You are Medac assistant.",
    question: "What do I take today?",
    authorization: VIEWER,
    executor,
    aiRunId: "run-1",
    streamFn,
    ...overrides,
  });
}

describe("Pi harness", () => {
  it("executes a tool call and returns the final answer", async () => {
    const { executor, calls } = stubExecutor();
    const { streamFn } = fauxStream([
      fauxAssistantMessage([fauxToolCall("list_medications", { patientId: VIEWER.patientId })]),
      fauxAssistantMessage("You take Metformin twice daily."),
    ]);

    const result = await run(streamFn, executor);

    expect(result.message).toBe("You take Metformin twice daily.");
    expect(result.turnCount).toBe(2);
    expect(result.toolCallCount).toBe(1);
    expect(calls.map((c) => c.name)).toEqual(["list_medications"]);
    expect(result.factsUsed.map((f) => f.id)).toContain("list_medications-1");
    expect(result.limitations).toEqual([]);
  });

  it("blocks tools the role cannot use and never executes them", async () => {
    const { executor, calls } = stubExecutor();
    const { streamFn } = fauxStream([
      // propose_* is not in a viewer's tool list, so Pi cannot even see it;
      // the harness gate is the second line of defence.
      fauxAssistantMessage([fauxToolCall("propose_create_medication", { enteredName: "Aspirin" })]),
      fauxAssistantMessage("I cannot make changes for you."),
    ]);

    const result = await run(streamFn, executor);
    expect(calls).toHaveLength(0);
    expect(result.message).toBe("I cannot make changes for you.");
  });

  it("stops at the turn budget without fabricating an answer", async () => {
    const { executor } = stubExecutor();
    const { streamFn } = fauxStream([
      fauxAssistantMessage([fauxToolCall("list_medications", {})]),
      fauxAssistantMessage([fauxToolCall("list_medications", {})]),
      fauxAssistantMessage([fauxToolCall("list_medications", {})]),
    ]);

    const result = await run(streamFn, executor, { maxTurns: 2 });
    expect(result.turnCount).toBe(2);
    expect(result.limitations).toContain("TURN_BUDGET_EXHAUSTED");
    expect(result.message).not.toContain("Metformin");
  });

  it("reports token usage from the provider", async () => {
    const { executor } = stubExecutor();
    const { streamFn } = fauxStream([
      fauxAssistantMessage([fauxToolCall("list_medications", {})]),
      fauxAssistantMessage("Done."),
    ]);

    const result = await run(streamFn, executor);
    // The faux provider estimates usage per request; exact summing is unit-tested below.
    expect(result.inputTokens).toBeGreaterThan(0);
    expect(result.outputTokens).toBeGreaterThan(0);
  });

  it("injects stored history into the transcript", async () => {
    const { executor } = stubExecutor();
    const captured: unknown[] = [];
    const faux = fauxStream([fauxAssistantMessage("Yes, 8am and 8pm.")]);
    const spy: StreamFn = (model, context, options) => {
      captured.push(...(context.messages as unknown[]));
      return faux.streamFn(model, context, options);
    };

    const result = await run(spy, executor, {
      history: [
        { role: "user", content: "When do I take Metformin?" },
        { role: "assistant", content: "Twice daily with meals." },
      ],
    });

    expect(result.message).toBe("Yes, 8am and 8pm.");
    const roles = captured.map((m) => (m as { role: string }).role);
    expect(roles).toContain("user");
    expect(roles).toContain("assistant");
    // The stored turns must precede the new question.
    expect(captured.length).toBeGreaterThanOrEqual(3);
  });
});

describe("Pi harness wiring", () => {
  it("sums usage across assistant messages only", () => {
    const usage = (input: number, output: number) => ({
      input,
      output,
      cacheRead: 0,
      cacheWrite: 0,
      totalTokens: input + output,
      cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, total: 0 },
    });
    const messages = [
      { role: "user", content: "hi", timestamp: 1 },
      { role: "assistant", content: [], usage: usage(100, 20), api: MODEL.api, provider: MODEL.provider, model: MODEL.id, stopReason: "stop", timestamp: 2 },
      { role: "assistant", content: [], usage: usage(150, 30), api: MODEL.api, provider: MODEL.provider, model: MODEL.id, stopReason: "stop", timestamp: 3 },
    ] as never;
    expect(sumUsage(messages)).toEqual({ inputTokens: 250, outputTokens: 50 });
  });

  it("exposes only permitted tools", () => {
    const { executor } = stubExecutor();
    const viewer = buildAgentTools({ authorization: VIEWER, executor, aiRunId: "r" });
    const names = viewer.tools.map((t) => t.name);
    expect(names).toContain("list_medications");
    expect(names).not.toContain("propose_create_medication");
    expect(viewer.authorize("propose_create_medication").allowed).toBe(false);
    expect(viewer.authorize("list_medications").allowed).toBe(true);
  });

  it("builds a gateway model descriptor", () => {
    expect(MODEL.api).toBe("openai-completions");
    expect(MODEL.baseUrl).toBe("http://127.0.0.1:8045/v1");
    expect(MODEL.id).toBe("gemini-3.8-flash-low");
  });

  it("rejects models outside the allowlist in the stream guard", () => {
    const guard = createGuardedStreamFn(1000);
    expect(() =>
      guard({ ...MODEL, id: "gpt-4" } as never, { messages: [] } as never, {}),
    ).toThrow(/not allowed/i);
  });

  it("converts stored history into agent messages", () => {
    const msgs = toAgentMessages([{ role: "user", content: "hi" }, { role: "assistant", content: "hello" }], MODEL);
    expect(msgs).toHaveLength(2);
    expect(msgs[0].role).toBe("user");
    expect(msgs[1].role).toBe("assistant");
  });
});
