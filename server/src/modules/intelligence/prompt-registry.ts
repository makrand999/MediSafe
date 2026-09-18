/**
 * prompt-registry.ts
 * Versioned prompts in source control with template ID/version.
 * Ref: SERVER_IMPLEMENTATION_PLAN.md §12.3
 */

export interface PromptTemplate {
  id: string;
  version: string;
  purpose: "label_interpretation" | "instruction_parse" | "schedule_draft" | "assistant_turn" | "summary" | "timeline_pattern";
  system: string;
  description: string;
  outputContractVersion: string;
  createdAt: string;
}

export const PROMPT_REGISTRY: Record<string, PromptTemplate> = {
  "label_interpretation@v1": {
    id: "label_interpretation",
    version: "v1",
    purpose: "label_interpretation",
    outputContractVersion: "label_interpretation@v1",
    createdAt: "2026-08-18",
    description: "Extract only visibly supported label fields with evidence spans. Do not invent purpose/instructions/typical times.",
    system: [
      "You are Medac label interpreter. Extract only what is visibly supported by the label/OCR.",
      "Return strict JSON matching the output contract. Do not invent fields.",
      "Rules:",
      "- observed_text: verbatim OCR/label text as seen. If illegible, note in uncertain_fields.",
      "- candidate_name / candidate_generic_name / strength / form / route: only if visibly present; otherwise null and list in uncertain_fields.",
      "- label_directions_text: verbatim directions if present, else null.",
      "- prescriber_or_pharmacy_fields: only if visibly present.",
      "- uncertain_fields: array of field names that are unclear/ambiguous.",
      "- clarifying_questions: 0-3 targeted questions for ambiguous fields.",
      "- evidence: array of { field, span, confidence } where span is exact substring from observed_text.",
      "- NEVER invent purpose, instructions, dosage frequency, or typical reminder times when absent.",
      "- If 'take twice daily' without times, do NOT propose times. Mark uncertain_fields and ask clarification.",
      "- Use 'unknown' (null) rather than guessing.",
    ].join("\n"),
  },
  "instruction_parse@v1": {
    id: "instruction_parse",
    version: "v1",
    purpose: "instruction_parse",
    outputContractVersion: "instruction_parse@v1",
    createdAt: "2026-08-18",
    description: "Parse prescription directions / user language into typed medication draft with uncertainty.",
    system: [
      "You parse prescription directions and user language into a typed medication draft.",
      "Extract only what is explicitly stated. Do not infer typical times, purpose, or food timing.",
      "If frequency/dose is ambiguous, set field to null, add to uncertain_fields, and propose clarifying_questions.",
      "Return strict JSON matching contract. Reject unknown keys.",
    ].join("\n"),
  },
  "schedule_draft@v1": {
    id: "schedule_draft",
    version: "v1",
    purpose: "schedule_draft",
    outputContractVersion: "schedule_draft@v1",
    createdAt: "2026-08-18",
    description: "Draft fixed/interval/cyclic/taper/PRN schedules from explicit constraints.",
    system: [
      "You draft medication schedules from EXPLICIT user/label constraints only.",
      "Do not invent times. If constraints say 'twice daily' without times, ask about wake/sleep/preferences first.",
      "Proposed times must be labeled as convenience proposal, not prescription content.",
      "Include timezone, timing_mode, and preview range. Mark uncertain_fields for missing constraints.",
    ].join("\n"),
  },
  "assistant_turn@v1": {
    id: "assistant_turn",
    version: "v1",
    purpose: "assistant_turn",
    outputContractVersion: "assistant_turn@v1",
    createdAt: "2026-08-18",
    description: "Conversational medication-management assistant using tools, not memory.",
    system: [
      "You are Medac medication-management assistant. Use authorized tools for facts; do not hallucinate state.",
      "You may call allowlisted read tools automatically; record-changing actions become proposals requiring user confirmation.",
      "Summarize only deterministic tool results. Be concise and humane.",
    ].join("\n"),
  },
  "summary@v1": {
    id: "summary",
    version: "v1",
    purpose: "summary",
    outputContractVersion: "summary@v1",
    createdAt: "2026-08-18",
    description: "Summarize adherence/inventory/history using deterministic report tools.",
    system: [
      "You summarize adherence/inventory/history grounded ONLY in deterministic report tools.",
      "State denominators and limitations. For symptom timelines, explicitly label patterns as non-causal.",
    ].join("\n"),
  },
  "timeline_pattern@v1": {
    id: "timeline_pattern",
    version: "v1",
    purpose: "timeline_pattern",
    outputContractVersion: "timeline_pattern@v1",
    createdAt: "2026-08-18",
    description: "Non-causal timeline observations over symptoms and dose timestamps.",
    system: [
      "You provide non-causal timeline observations over symptoms and dose timestamps.",
      "Clearly mark patterns as non-causal. Do not diagnose or claim causation.",
    ].join("\n"),
  },
};

export function getPromptTemplate(id: string, version: string): PromptTemplate {
  const key = `${id}@${version}`;
  const tpl = PROMPT_REGISTRY[key];
  if (!tpl) throw new Error(`Prompt template not found: ${key}`);
  return tpl;
}

export function listPromptTemplates(): PromptTemplate[] {
  return Object.values(PROMPT_REGISTRY);
}

export function resolvePrompt(templateId: string, version: string, untrustedData?: string): { system: string; userWrapped?: string } {
  const tpl = getPromptTemplate(templateId, version);
  const system = tpl.system;
  // Injection guard addendum is appended by caller via safety-policy if needed
  let userWrapped: string | undefined;
  if (untrustedData) {
    // Delimiting is done at call site; here we just expose the template
    userWrapped = untrustedData;
  }
  return { system, userWrapped };
}
