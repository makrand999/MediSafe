import { describe, it, expect } from "vitest";
import { Orchestrator } from "../../../src/modules/intelligence/orchestrator.js";
import type { IntelligenceProvider, ToolTurnRequest, ToolTurnResponse } from "../../../src/modules/intelligence/muse-spark-provider.js";
import type { PatientAuthorization, ToolExecutor } from "../../../src/modules/intelligence/orchestrator.js";

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

interface ScriptedTurn {
  content?: string | null;
  toolCalls?: Array<{ id: string; name: string; args: string }>;
  usage?: { inputTokens: number; outputTokens: number };
}

/** Provider that replays a script and records every request it receives. */
function scriptedProvider(script: ScriptedTurn[]) {
  const requests: ToolTurnRequest[] = [];
  let i = 0;
  const provider: IntelligenceProvider = {
    generateStructured: async () => {
      throw new Error("not used");
    },
    analyzeImage: async () => {
      throw new Error("not used");
    },
    async runToolTurn(req: ToolTurnRequest): Promise<ToolTurnResponse> {
      requests.push(req);
      const turn = script[Math.min(i, script.length - 1)];
      i++;
      return {
        content: turn.content ?? null,
        toolCalls: (turn.toolCalls ?? []).map((tc) => ({ id: tc.id, name: tc.name, argumentsJson: tc.args })),
        usage: turn.usage,
      };
    },
  };
  return { provider, requests };
}

function countingExecutor(results: Record<string, unknown> = {}) {
  const calls: Array<{ name: string; args: unknown }> = [];
  const executor: ToolExecutor = {
    async executeRead(name, args) {
      calls.push({ name, args });
      return results[name] ?? { id: `${name}-result`, type: "medication", items: [] };
    },
    async createProposal(name) {
      calls.push({ name, args: undefined });
      return { proposalId: `proposal-${name}`, expiresAt: new Date().toISOString() };
    },
  };
  return { executor, calls };
}

const AUTHZ = (role: string, perms: string[]): PatientAuthorization => ({
  patientId: "44444444-4444-4444-8444-444444444444",
  role: role as PatientAuthorization["role"],
  can: (perm: string) => perms.includes(perm),
});

const OWNER_PERMS = ["medication:read", "medication:create", "schedule:read", "adherence:read", "inventory:read", "doseEvent:read", "doseEvent:create", "medication:update"];
const VIEWER_PERMS = ["medication:read", "schedule:read", "adherence:read", "inventory:read", "doseEvent:read"];

function run(orchestrator: Orchestrator, authz: PatientAuthorization, message = "What do I take today?") {
  return orchestrator.run({
    auth: { userId: "55555555-5555-4555-8555-555555555555", sessionId: "s", hasRecentAuth: true },
    authorization: authz,
    aiRunId: "run-1",
    purpose: "assistant_turn",
    systemPrompt: "You are Medac assistant.",
    initialUserMessage: message,
  });
}

// ---------------------------------------------------------------------------

describe("orchestrator tool exposure", () => {
  it("sends only the tools the role can execute", async () => {
    const { provider, requests } = scriptedProvider([{ content: "done" }]);
    const { executor } = countingExecutor();
    const orchestrator = new Orchestrator(provider, executor, undefined, { maxTurns: 4, maxToolCalls: 8, maxOutputTokens: 1000, maxTimeMs: 30000, maxContextBytes: 16000 });

    await run(orchestrator, AUTHZ("viewer", VIEWER_PERMS));
    const viewerTools = requests[0].tools.map((t) => t.function.name);
    expect(viewerTools).toContain("get_today_occurrences");
    expect(viewerTools).not.toContain("propose_create_medication");

    requests.length = 0;
    await run(orchestrator, AUTHZ("owner", OWNER_PERMS));
    const ownerTools = requests[0].tools.map((t) => t.function.name);
    expect(ownerTools).toContain("propose_create_medication");
    expect(ownerTools.length).toBeGreaterThan(viewerTools.length);
  });

  it("describes tool arguments with real JSON Schema types", async () => {
    const { provider, requests } = scriptedProvider([{ content: "done" }]);
    const { executor } = countingExecutor();
    const orchestrator = new Orchestrator(provider, executor, undefined, { maxTurns: 2, maxToolCalls: 4, maxOutputTokens: 1000, maxTimeMs: 30000, maxContextBytes: 16000 });
    await run(orchestrator, AUTHZ("viewer", VIEWER_PERMS));

    const listMeds = requests[0].tools.find((t) => t.function.name === "list_medications")!;
    const status = (listMeds.function.parameters as { properties: Record<string, { enum?: string[]; type?: string }> }).properties.status;
    expect(status.enum).toContain("active");
    expect(status.type).toBe("string");

    const expirations = requests[0].tools.find((t) => t.function.name === "get_expiration_summary")!;
    const withinDays = (expirations.function.parameters as { properties: Record<string, { type?: string; maximum?: number }> }).properties.withinDays;
    expect(withinDays.type).toBe("integer");
    expect(withinDays.maximum).toBe(365);
  });
});

describe("orchestrator tool execution", () => {
  it("executes identical calls once and reuses the result", async () => {
    const { provider } = scriptedProvider([
      { toolCalls: [
        { id: "c1", name: "list_medications", args: '{"status":"active"}' },
        { id: "c2", name: "list_medications", args: '{"status":"active"}' },
      ] },
      { content: "You take Metformin." },
    ]);
    const { executor, calls } = countingExecutor({ list_medications: { id: "m1", type: "medication", items: [{ id: "m1" }] } });
    const orchestrator = new Orchestrator(provider, executor, undefined, { maxTurns: 4, maxToolCalls: 8, maxOutputTokens: 1000, maxTimeMs: 30000, maxContextBytes: 16000 });

    const res = await run(orchestrator, AUTHZ("viewer", VIEWER_PERMS));
    expect(calls.filter((c) => c.name === "list_medications")).toHaveLength(1);
    expect(res.toolCallCount).toBe(2);
    expect(res.message).toBe("You take Metformin.");
  });

  it("runs distinct calls in parallel and keeps result order", async () => {
    const { provider } = scriptedProvider([
      { toolCalls: [
        { id: "c1", name: "list_medications", args: "{}" },
        { id: "c2", name: "get_today_occurrences", args: "{}" },
      ] },
      { content: "Here is today." },
    ]);
    let active = 0;
    let maxActive = 0;
    const executor: ToolExecutor = {
      async executeRead(name) {
        active++;
        maxActive = Math.max(maxActive, active);
        await new Promise((r) => setTimeout(r, 20));
        active--;
        return { id: `${name}-1`, type: "medication" };
      },
      async createProposal() {
        throw new Error("no proposals in this test");
      },
    };
    const orchestrator = new Orchestrator(provider, executor, undefined, { maxTurns: 4, maxToolCalls: 8, maxOutputTokens: 1000, maxTimeMs: 30000, maxContextBytes: 16000 });

    const res = await run(orchestrator, AUTHZ("viewer", VIEWER_PERMS));
    expect(maxActive).toBe(2); // both were in flight at once
    expect(res.factsUsed.map((f) => f.id)).toEqual(["list_medications-1", "get_today_occurrences-1"]);
  });

  it("forces a final answer when the turn budget is exhausted", async () => {
    const { provider, requests } = scriptedProvider([
      { toolCalls: [{ id: "c1", name: "list_medications", args: "{}" }] },
      { toolCalls: [{ id: "c2", name: "get_today_occurrences", args: "{}" }] },
      { content: "You take Metformin twice daily." },
    ]);
    const { executor } = countingExecutor();
    const orchestrator = new Orchestrator(provider, executor, undefined, { maxTurns: 2, maxToolCalls: 8, maxOutputTokens: 1000, maxTimeMs: 30000, maxContextBytes: 16000 });

    const res = await run(orchestrator, AUTHZ("viewer", VIEWER_PERMS));
    expect(res.turnCount).toBe(2);
    expect(res.message).toBe("You take Metformin twice daily.");
    // The closing call must carry no tools so the gateway cannot call them.
    expect(requests[2].tools).toEqual([]);
  });

  it("sums token usage across turns", async () => {
    const { provider } = scriptedProvider([
      { toolCalls: [{ id: "c1", name: "list_medications", args: "{}" }], usage: { inputTokens: 100, outputTokens: 20 } },
      { content: "done", usage: { inputTokens: 150, outputTokens: 30 } },
    ]);
    const { executor } = countingExecutor();
    const orchestrator = new Orchestrator(provider, executor, undefined, { maxTurns: 4, maxToolCalls: 8, maxOutputTokens: 1000, maxTimeMs: 30000, maxContextBytes: 16000 });

    const res = await run(orchestrator, AUTHZ("viewer", VIEWER_PERMS));
    expect(res.inputTokens).toBe(250);
    expect(res.outputTokens).toBe(50);
  });
});
