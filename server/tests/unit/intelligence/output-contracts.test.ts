import { describe, it, expect } from "vitest";
import {
  LabelInterpretationSchema,
  validateWithOneRepair,
  parseJsonStrict,
} from "../../../src/modules/intelligence/output-contracts.js";

const validLabelJson = JSON.stringify({
  observed_text: "Lisinopril 10 mg Take one tablet by mouth once daily",
  candidate_name: "Lisinopril",
  candidate_generic_name: "lisinopril",
  strength_value: "10",
  strength_unit: "mg",
  form: "tablet",
  route: "oral",
  label_directions_text: "Take one tablet by mouth once daily",
  prescriber_fields: { prescriber_name: "Dr. Smith", pharmacy_name: "CVS" },
  uncertain_fields: [],
  clarifying_questions: [],
  evidence: [{ field: "candidate_name", span: "Lisinopril", confidence: 0.95 }],
  confidence: 0.9,
});

describe("output-contracts label interpretation", () => {
  it("accepts valid label interpretation", () => {
    const res = parseJsonStrict(LabelInterpretationSchema, validLabelJson);
    expect(res.ok).toBe(true);
  });

  it("accepts the same JSON wrapped in a markdown fence", () => {
    // Gemini via the Antigravity gateway wraps json_object answers in ```json fences.
    for (const fenced of [`\`\`\`json\n${validLabelJson}\n\`\`\``, `\`\`\`\n${validLabelJson}\n\`\`\``, `  \`\`\`json\n${validLabelJson}\n\`\`\`  `]) {
      const res = parseJsonStrict(LabelInterpretationSchema, fenced);
      expect(res.ok).toBe(true);
    }
  });

  it("does not treat a plain string with backticks as fenced JSON", () => {
    const res = parseJsonStrict(LabelInterpretationSchema, "not ```json at all");
    expect(res.ok).toBe(false);
  });

  it("strips a fence before the forbidden-key repair pass", () => {
    const fencedWithPurpose = `\`\`\`json\n${JSON.stringify({
      ...JSON.parse(validLabelJson),
      purpose: "for blood pressure",
    })}\n\`\`\``;
    const repaired = validateWithOneRepair(LabelInterpretationSchema, fencedWithPurpose);
    expect(repaired.ok).toBe(true);
    expect(repaired.repaired).toBe(true);
  });

  it("rejects unknown keys (strict)", () => {
    const withPurpose = JSON.stringify({
      ...JSON.parse(validLabelJson),
      purpose: "for blood pressure", // forbidden — must not invent purpose
    });
    const res = parseJsonStrict(LabelInterpretationSchema, withPurpose);
    expect(res.ok).toBe(false);
  });

  it("rejects invalid enum/unit", () => {
    const bad = JSON.stringify({ ...JSON.parse(validLabelJson), strength_unit: "bananas" });
    const res = parseJsonStrict(LabelInterpretationSchema, bad);
    expect(res.ok).toBe(false);
  });

  it("rejects missing evidence", () => {
    const noEvidence = JSON.stringify({ ...JSON.parse(validLabelJson), evidence: [] });
    const res = parseJsonStrict(LabelInterpretationSchema, noEvidence);
    expect(res.ok).toBe(false);
  });

  it("requires uncertain_fields present", () => {
    const obj = JSON.parse(validLabelJson) as Record<string, unknown>;
    delete obj.uncertain_fields;
    const res = parseJsonStrict(LabelInterpretationSchema, JSON.stringify(obj));
    expect(res.ok).toBe(false);
  });

  it("repair at most once: strips forbidden purpose and succeeds on retry", () => {
    const withForbidden = JSON.stringify({
      ...JSON.parse(validLabelJson),
      purpose: "invented",
      typical_times: ["08:00"],
    });
    const first = parseJsonStrict(LabelInterpretationSchema, withForbidden);
    expect(first.ok).toBe(false);
    const repaired = validateWithOneRepair(LabelInterpretationSchema, withForbidden);
    expect(repaired.ok).toBe(true);
    expect(repaired.repaired).toBe(true);
    // second repair should not be attempted again — second failure stays repaired=true but ok false
    const doubleBad = JSON.stringify({
      ...JSON.parse(validLabelJson),
      purpose: "x",
      strength_unit: "bad_unit", // not repairable by default sanitizer
    });
    const r2 = validateWithOneRepair(LabelInterpretationSchema, doubleBad);
    expect(r2.ok).toBe(false);
  });

  it("rejects invalid strength_value format", () => {
    const bad = JSON.stringify({ ...JSON.parse(validLabelJson), strength_value: "10 mg" });
    const res = parseJsonStrict(LabelInterpretationSchema, bad);
    expect(res.ok).toBe(false);
  });

  it("allows null for unknown fields (unknown state rather than guess)", () => {
    const unknown = JSON.stringify({
      observed_text: "Illegible label",
      candidate_name: null,
      candidate_generic_name: null,
      strength_value: null,
      strength_unit: null,
      form: null,
      route: null,
      label_directions_text: null,
      uncertain_fields: ["candidate_name", "strength_value", "form", "route", "label_directions_text"],
      clarifying_questions: ["Can you provide a clearer photo?"],
      evidence: [{ field: "observed_text", span: "Illegible", confidence: 0.4 }],
      confidence: 0.4,
    });
    const res = parseJsonStrict(LabelInterpretationSchema, unknown);
    expect(res.ok).toBe(true);
  });

  it("must NOT allow invented typical_times even after repair", () => {
    const withTypical = JSON.stringify({
      ...JSON.parse(validLabelJson),
      typical_times: ["08:00", "20:00"],
    });
    // strict should reject
    expect(parseJsonStrict(LabelInterpretationSchema, withTypical).ok).toBe(false);
    // repair strips it
    const repaired = validateWithOneRepair(LabelInterpretationSchema, withTypical);
    expect(repaired.ok).toBe(true);
    expect((repaired.data as Record<string, unknown>).typical_times).toBeUndefined();
  });
});
