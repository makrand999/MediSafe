# Clinical Feature Gate — §26

No deferred check may be enabled merely because code exists `server/src/db/schema.ts:673` `safety_checks` `safety_findings` remain empty in MVP.

Required before enabling `interaction|duplicate|allergy|dose-range|timing|missed-dose|overdose` `server/SERVER_IMPLEMENTATION_PLAN.md §26`:
1. Intended use + jurisdiction doc
2. Licensed/current source `clinical_source_versions` `server/src/db/schema.ts:673`
3. Ingestion + version verification `checksum`
4. Clinical reviewer approval
5. Validated rule + tests
6. Limitations doc
7. Structured result `clear_in_checked_source|finding|unknown|failed|not_supported`
8. Source citation shown `source_citation`
9. Staleness monitoring
10. Kill switch by `sourceVersion`
11. Incident procedure `server/docs/INCIDENT_RESPONSE.md`
12. Regulatory/legal
13. No-result wording `unknown` not global safety
14. Human-readable review independent of LLM

Muse Spark explanations of findings must consume only structured `safety_findings` and be schema-constrained.
