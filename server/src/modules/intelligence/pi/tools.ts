/**
 * Pi harness — medac tools exposed as Pi AgentTools.
 *
 * The tool surface is ours: the same allowlist, the same permission gate, the
 * same Zod validation, the same executor (DB reads + proposals). Pi only
 * decides when to call them.
 */

import { Type } from "@earendil-works/pi-ai";
import type { AgentTool } from "@earendil-works/pi-agent-core";
import { zodToJsonSchema } from "zod-to-json-schema";
import { getToolDefinition, listToolsFor, validateToolCall } from "../tool-registry.js";
import type { PatientAuthorization, ToolExecutor } from "../orchestrator.js";

export interface AgentToolBundle {
  tools: AgentTool<any>[];
  /** Authorization gate used by the harness `beforeToolCall` hook. */
  authorize: (toolName: string) => { allowed: boolean; reason?: string };
}

export function buildAgentTools(input: {
  authorization: PatientAuthorization;
  executor: ToolExecutor;
  aiRunId: string;
}): AgentToolBundle {
  const { authorization, executor, aiRunId } = input;

  const tools = listToolsFor(authorization).map((def) => ({
    name: def.name,
    label: def.name,
    description: def.description,
    parameters: Type.Unsafe(zodToJsonSchema(def.schema, { target: "openApi3" }) as Record<string, unknown>),
    async execute(_toolCallId: string, params: unknown) {
      // Defense in depth: Pi validates against the schema it was given, but the
      // server-side Zod contract stays authoritative.
      const validation = validateToolCall(def.name, params);
      if (!validation.valid) {
        throw Object.assign(new Error("Tool argument validation failed"), {
          code: "ARG_VALIDATION_FAILED",
          issues: validation.issues,
        });
      }

      if (def.mode === "read") {
        const result = await executor.executeRead(def.name, params, authorization);
        return {
          content: [{ type: "text" as const, text: JSON.stringify(result).slice(0, 4000) }],
          details: { kind: "read" as const, tool: def.name, result },
        };
      }

      const proposal = await executor.createProposal(def.name, params, authorization, aiRunId);
      return {
        content: [
          {
            type: "text" as const,
            text: JSON.stringify({ proposal_created: proposal.proposalId, requires_confirmation: true, expires_at: proposal.expiresAt }),
          },
        ],
        details: {
          kind: "proposal" as const,
          tool: def.name,
          proposal: {
            proposal_id: proposal.proposalId,
            action_type: def.name,
            requires_confirmation: true as const,
            expires_at: proposal.expiresAt,
          },
        },
      };
    },
  }));

  return {
    tools,
    authorize(toolName: string) {
      const def = getToolDefinition(toolName);
      if (!def) return { allowed: false, reason: "UNKNOWN_TOOL" };
      if (!authorization.can(def.requiredPermission)) {
        return { allowed: false, reason: `NOT_AUTHORIZED:${def.requiredPermission}` };
      }
      return { allowed: true };
    },
  };
}
