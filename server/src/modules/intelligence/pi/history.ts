/**
 * Pi harness — conversation history bridge.
 *
 * Our messages are stored encrypted (ai_messages.content_ciphertext). This
 * loads the most recent turns, decrypts them in memory only, and hands them to
 * the Pi agent as its initial transcript. Nothing is written to disk in the
 * clear: Pi's own session stores are not used for PHI.
 */

import { desc, eq } from "drizzle-orm";
import type { NodePgDatabase } from "drizzle-orm/node-postgres";
import * as schema from "../../../db/schema.js";
import { decryptPayload } from "../proposal-service.js";
import type { PiHistoryMessage } from "./harness.js";

export const DEFAULT_HISTORY_TURNS = 6;

export async function loadConversationHistory(
  db: NodePgDatabase<typeof schema>,
  conversationId: string,
  limit = DEFAULT_HISTORY_TURNS,
): Promise<PiHistoryMessage[]> {
  const rows = await db.query.aiMessages.findMany({
    where: eq(schema.aiMessages.conversationId, conversationId),
    orderBy: [desc(schema.aiMessages.createdAt)],
    limit,
  });

  const history: PiHistoryMessage[] = [];
  for (const row of rows.reverse()) {
    try {
      const content = decryptPayload(row.contentCiphertext).trim();
      if (!content) continue;
      history.push({ role: row.role === "user" ? "user" : "assistant", content });
    } catch {
      // Undecryptable rows are skipped rather than failing the turn.
    }
  }
  return history;
}
