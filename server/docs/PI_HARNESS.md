# Pi harness (flagged) — assistant conversation loop

`ASSISTANT_HARNESS=pi` runs the assistant conversation on [Pi](https://pi.dev)'s
agent runtime (`@earendil-works/pi-agent-core`, MIT, pinned `0.87.0`) instead of
our own `orchestrator.ts` tool loop. Everything medac-specific stays ours.

## What Pi does and does not do here

| Concern | Owner |
|---|---|
| Turn loop, retries, abort, streaming events | Pi `Agent` |
| Tool definitions (24, Zod) | ours → exposed as TypeBox `AgentTool`s (`pi/tools.ts`) |
| Tool execution (DB reads, proposals) | ours (`DbToolExecutor`) |
| Authorization per tool call | ours, enforced in `beforeToolCall` |
| Turn budget | ours, enforced in `finishTurn` |
| Model allowlist + request timeout | ours, enforced in `createGuardedStreamFn` |
| Proposals / user confirmation | ours (unchanged) |
| Audit (`ai_runs`), rate limits, encrypted messages | ours (unchanged) |
| Conversation history | ours (`ai_messages`, decrypted in memory only) |
| Label OCR / instruction parse / drafts | ours (not the agent path) |

Pi's own session stores (JSONL) are **not** used: they write plaintext to disk.

## Wiring

```
routes.ts (assistant messages)
  └─ config.assistantHarness === "pi"
       ├─ pi/model.ts     buildGatewayModel()  → gateway as an openai-completions model
       ├─ pi/history.ts   loadConversationHistory() → decrypt last 6 turns
       └─ pi/harness.ts   runPiAssistantTurn()
            ├─ pi/tools.ts      buildAgentTools()  (registry + executor)
            ├─ beforeToolCall   bundle.authorize()
            ├─ finishTurn       turn budget
            ├─ afterToolCall    facts_used / proposals
            └─ subscribe        tool_call count, audit events
```

Result shape is `OrchestratorResponse`, so the route, audit row and response
payload are identical for both harnesses.

## Eval (6 questions, same tools/prompt/stub data, real gateway)

| Question | legacy | pi |
|---|---|---|
| today-doses | — | 3.98 s, 2 turns |
| low-stock + missed | 5.39 s, 3 turns | 6.47 s, 3 turns |
| morning check | 4.41 s, 3 turns | 3.91 s, 3 turns |
| dose lookup | 5.05 s, 3 turns | 3.38 s, 3 turns |
| expiry | 3.90 s, 2 turns | 2.35 s, 2 turns |
| schedule | 3.96 s, 3 turns | 2.96 s, 3 turns |

Average 4.30 s → 3.84 s (~11% faster), zero failures on either side, answers
equivalent. Pi's real advantages are the features we did not have: streaming
events, session history, compaction and usage accounting.

## Rollback

```
ASSISTANT_HARNESS=legacy   # in /opt/medac/env/api.env
systemctl restart medac-api-new medac-worker
```

## Version policy

Pi ships multiple releases per week. Dependencies are pinned exactly
(`"@earendil-works/pi-agent-core": "0.87.0"`), and the flagged path uses the
stable `Agent`/agent-loop API — not the v2 `AgentHarness` surface, parts of
which still throw `HarnessNotImplemented`. Upgrade deliberately, re-run the
eval (`/tmp/opencode/pi-eval.mjs` pattern) and keep `legacy` as the fallback.
