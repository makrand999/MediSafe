import { describe, it, expect } from "vitest";
import { validateToolCall, getToolDefinition, isReadTool, isProposalTool, listTools } from "../../../src/modules/intelligence/tool-registry.js";

describe("tool-registry allowlist/validation", () => {
  it("lists 12 read + 12 proposal tools", () => {
    const all = listTools();
    const reads = all.filter((t) => t.mode === "read");
    const proposals = all.filter((t) => t.mode === "proposal");
    expect(reads.length).toBe(12);
    expect(proposals.length).toBe(12);
  });

  it("rejects unknown tool", () => {
    const res = validateToolCall("evil_tool", {});
    expect(res.valid).toBe(false);
    expect(res.error).toMatch(/Unknown tool/);
  });

  it("rejects extra args (strict)", () => {
    const res = validateToolCall("list_medications", { patientId: "00000000-0000-4000-a000-000000000001", extra: "nope" });
    expect(res.valid).toBe(false);
  });

  it("accepts valid read tool args", () => {
    const res = validateToolCall("list_medications", { patientId: "00000000-0000-4000-a000-000000000001" });
    expect(res.valid).toBe(true);
  });

  it("rejects proposal tool with missing expectedVersion", () => {
    const res = validateToolCall("propose_update_medication", {
      patientId: "00000000-0000-4000-a000-000000000001",
      medicationId: "00000000-0000-4000-a000-000000000002",
      patch: { enteredName: "x" },
      // missing expectedVersion
    });
    expect(res.valid).toBe(false);
  });

  it("classifies read/proposal correctly", () => {
    expect(isReadTool("get_medication")).toBe(true);
    expect(isProposalTool("get_medication")).toBe(false);
    expect(isProposalTool("propose_create_medication")).toBe(true);
    expect(isReadTool("propose_create_medication")).toBe(false);
  });

  it("rejects invalid uuid for patientId", () => {
    const res = validateToolCall("get_medication", { patientId: "not-a-uuid", medicationId: "00000000-0000-4000-a000-000000000002" });
    expect(res.valid).toBe(false);
  });

  it("getToolDefinition returns undefined for unknown", () => {
    expect(getToolDefinition("does_not_exist")).toBeUndefined();
  });
});
