You are auditing this application for UX consistency and implementation correctness. 
The core problem: the UX is defined inconsistently across the app, it has grown 
unnecessarily complex, and parts of the implementation don't actually match what 
was designed or intended. Your job is NOT to add features — it's to find and fix 
drift between intent, design, and code.

Do this in phases, and keep a running mental model of the WHOLE app (all screens/
flows/states) so fixes in one place don't contradict another.

PHASE 1 — Map the current state
- Enumerate every screen, view, component, and user flow in the app.
- For each, note: its purpose, entry points, exit points, and states (loading, 
  empty, error, success).
- Build a single source-of-truth map (list or diagram) of how flows connect to 
  each other. Flag anything undocumented or that you had to infer.

PHASE 2 — Define the intended UX (if not already defined)
- If design specs, style guides, or PRDs exist, extract the intended behavior/
  patterns from them.
- If they don't exist or are incomplete, infer the *most sensible, consistent* 
  pattern from how the app is used most commonly, and treat that as the standard 
  going forward. State these standards explicitly (naming, navigation patterns, 
  interaction patterns, error handling, terminology).

PHASE 3 — Find inconsistencies
- Compare every flow/screen against the standards from Phase 2.
- Flag: duplicated concepts with different names, similar actions handled 
  differently in different places, inconsistent navigation/back behavior, 
  inconsistent error/loading/empty states, unnecessary steps or screens that 
  add complexity without adding value.
- For each inconsistency, note where it lives (file/component) and what the 
  correct/consistent version should be.

PHASE 4 — Validate implementation against intent
- For each flow, trace the actual code path and confirm it does what the 
  UX/spec says it should do.
- Flag every mismatch: broken states, dead-end flows, silent failures, 
  conditions that don't match the documented behavior, stale logic left over 
  from earlier versions.
- Do not assume the code is right just because it runs — check it against 
  intended behavior, not just "does it execute."

PHASE 5 — Propose simplification
- Identify redundant screens/steps/components that could be merged or removed 
  without losing functionality.
- Propose a simpler flow only where it doesn't remove necessary functionality — 
  flag trade-offs if any.

PHASE 6 — Report before fixing
- Produce a prioritized list of issues (inconsistency vs. broken implementation 
  vs. unnecessary complexity), each with location, what's wrong, and proposed fix.
- Wait for confirmation on priorities before making sweeping changes — but you 
  may fix small, unambiguous bugs immediately if they're clearly wrong.

Constraints:
- Keep referring back to the full app map from Phase 1 — don't fix a flow in 
  isolation if it breaks consistency elsewhere.
- Don't introduce new UX patterns; converge everything toward the single 
  standard defined in Phase 2.
- Call out any assumption you make explicitly, don't silently guess.
