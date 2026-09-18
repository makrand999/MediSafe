# Medac — Product & Safety Flags Spec

> Extracted from design conversation. Preserves **all** requirements from the initial feature list (table stakes + non-obvious differentiators) plus the deeper flag/router architecture.

Last updated: 2026-08-18

## 1. Product Vision

Medication management that is correct, safe, and humane — covering daily scheduling through safety-critical checks, without hallucinating medical truth. Rule-based router, deterministic sources first, AI as interpreter.

---

## 2. Table Stakes — What Any Decent App Needs

### 2.1 Scheduling

- **Flexible dosing**
  - Fixed clock times
  - Rolling intervals (e.g. "every 8 hours" from last dose)
  - PRN / as-needed meds
  - Multi-week taper schedules where the dose itself changes over time
- **Cyclic schedules** — e.g. 21 days on / 7 off
- **Unified daily view** — multiple meds stacked into one daily view, not siloed per-drug

### 2.2 Safety

- Drug interaction and duplicate-therapy warnings when adding a new medication
- Allergy flags
- Low-stock and expiration alerts

### 2.3 Tracking

- Simple taken / skipped / missed logging with one tap
- Adherence history and streaks
- Exportable report for doctor visits (PDF/CSV)

### 2.4 Logistics

- Refill tracking tied to **pill count**, not just calendar guesses
- Pharmacy contact / reorder info attached to each medication
- Caregiver or family sharing, with permission to see (not necessarily edit)

### 2.5 Accessibility & Reliability

- Large-text / high-contrast mode
- Voice or audio reminders
- Offline-first — reminders must not die because wifi did

---

## 3. Non-Obvious Differentiators — Quietly the Most Valuable Part

| # | Feature | Why it matters |
|---|---------|----------------|
| 1 | **"Pill looks different" check** | Flags when pharmacy swaps generic manufacturers so color/shape changes. Prevents accidental double-dosing or refusal-to-take in elderly users who go by appearance. |
| 2 | **Time-zone-aware vs. body-clock-aware toggle** | For travelers on birth control, immunosuppressants, etc., consistent interval matters more than local clock time. Most apps blindly shift to local time and break the regimen. |
| 3 | **Cross-product ingredient overlap detection** | Catches combo cold medicine containing acetaminophen + separate acetaminophen, preventing silent daily-ceiling breaches. |
| 4 | **Discreet / private notification mode** | Generic lock-screen text ("Reminder") instead of drug name — for mental health, HIV, fertility meds, etc. |
| 5 | **Taper / wean assistant** | Auto-adjusts schedule as steroid, benzo, or opioid dose steps down over weeks; no manual re-editing every few days. |
| 6 | **Injection site rotation tracker** | For insulin/injectables, tracks last site so user rotates and avoids tissue damage. |
| 7 | **Hospital-stay pause** | Freezes reminders (without deleting setup) when meds are administered by staff. |
| 8 | **Symptom-timestamp correlation log** | Jot a side effect next to a dose entry so patterns ("headache ~2 hrs after dose") become visible. |
| 9 | **Emergency medical ID widget** | Locked-screen-accessible summary of current meds + allergies for first responders, independent of phone unlock. |
| 10 | **Barcode / NDC scan-to-add** | Skip manual entry by scanning pharmacy label directly. |
| 11 | **Multi-dependent profiles under one login** | One caregiver managing meds for aging parent, kid, and pet without three accounts. |
| 12 | **"Missed dose" decision helper** | Generic, pharmacist-reviewed "if it's been X hours, do Y" guidance from drug inserts — not personalized advice; removes anxiety. |

---

## 4. Data Layer — Normalize, Don't Blob

Keep medical data in structured tables so you can query slices instead of dumping everything.

```
patients
  id, dob, sex, weight, conditions[], allergies[],
  pregnancy_status, renal_flag, hepatic_flag

medications
  id, patient_id, name, generic_name, rxnorm_id,
  strength, form, dose_per_administration, route,
  schedule (times/interval/cyclic), start_date, end_date,
  status: active | paused | discontinued,
  indication, prescriber, instructions

dose_events
  id, medication_id, scheduled_time, actual_time,
  status: taken | skipped | missed, note

interaction_flags  -- CACHE, not live-computed every time
  id, med_a_id, med_b_id, severity, description, source, checked_at

symptom_logs
  id, patient_id, timestamp, note, linked_dose_event_id

refill_tracking
  medication_id, pills_remaining, total_pills, pharmacy_info

caregivers
  patient_id, caregiver_id, permission_level
```

**Non-negotiable:** Resolve drug names to a canonical ID (RxNorm / NDC) **on entry**, not at query time. If "Tylenol", "acetaminophen", "Panadol" float as different strings, every downstream check (interactions, ingredient overlap) becomes unreliable. Do the messy name→ID resolution once, up front.

---

## 5. Core Architecture Principles

### 5.1 Context Builder per Flag, Not One Big Context

Don't have a single "give the AI everything" call. Define a fixed catalog of flags and for each write a small function that pulls only what that flag needs straight from the DB.

| Flag | Context it gets | Context it does NOT get |
|------|-----------------|-------------------------|
| new_med_interaction_check | active meds only + allergies + conditions | dose history, symptom logs, discontinued meds |
| dose_timing_conflict | active meds' schedules only | everything else |
| duplicate_therapy_check | active meds' ingredient/class only | dosing history |
| adherence_summary | dose_events for date range | med details beyond name |
| refill_forecast | pill counts + dose frequency | interactions, symptoms |
| symptom_correlation | symptom_logs + dose_events | other patients' data, unrelated meds |

Each builder is a scoped SQL query wrapped in a function, e.g. `build_interaction_context(patient_id, new_med)`. The model never sees the whole record — small context, small hallucination surface, independently testable and auditable.

### 5.2 Deterministic Source First, AI Second (Safety-Critical Flags)

Don't let the LLM decide whether two drugs interact from its own knowledge.

1. **Deterministic layer:** Hit a real interaction database/API (RxNorm interaction API, DrugBank, openFDA) with resolved drug IDs → structured, sourced results.
2. **AI layer:** Orchestration and explanation — decide which pairs need checking, merge multiple flags into one summary, translate structured result into plain language, flag "talk to your pharmacist" when severity is high.

AI is the interpreter on top of ground truth, not the ground truth. Show the user "source: RxNorm interaction database" rather than "the AI thinks."

### 5.3 Cache, Don't Recompute

Interactions between two specific meds don't change day to day. Compute once when a medication is added, store in `interaction_flags`, invalidate only when one of the two meds changes. The "Today" screen should never re-run interaction checks on every load — it reads cached flags.

### 5.4 Output Contracts, Not Prose

Every flag returns structured JSON (severity, affected meds, reason, source, suggested action). Natural-language explanation is a final rendering step only. This makes flags composable, testable (assert on fields, not parse prose), and confines free-text generation to where it's actually needed.

### 5.5 Session Context vs. Long-Term Store

The DB is the only source of truth. Never let a growing chat/session history become a second, drifting copy of medical state. Every flag call re-derives its context fresh from the DB at call time — minimal, current, and correct.

---

## 6. Router — Event → Flags Trigger Matrix

Rule-based router. Flags run off specific events plus a couple of daily sweeps. Keeps AI/API calls rare and predictable.

| Event | Flags triggered |
|-------|-----------------|
| `medication_added` | interaction_check, duplicate_therapy_check, allergy_check, contraindication_check, dose_range_check |
| `medication_dose_or_schedule_edited` | dose_range_check, timing_conflict_check |
| `medication_discontinued` | interaction_recompute (drop stale flags tied to it) |
| `condition_added` | contraindication_check (re-run against all active meds) |
| `allergy_added` | allergy_check (re-run against all active meds) |
| `dose_event_logged: taken` | overdose_risk_check (only if 2nd "taken" too close to 1st) |
| `dose_event_logged: missed` (3+ in a row) | adherence_drift_check, missed_critical_dose_check (if med is high-risk) |
| `daily_cron` (once/day) | refill_forecast_check, expiration_check, taper_progression_check |
| `weekly_cron` | symptom_correlation_check, adherence_summary |

Nothing runs "just in case." Each flag has an owner event.

**Decision:** Rule-based router (chosen over AI orchestrator). More predictable and easier to certify/test for a health app. AI is called only for the explanation layer, not for routing.

---

## 7. Flag Spec Template

Every flag follows the same shape:

```
name, trigger, context_pulled, check_source (deterministic | statistical | ai-explain),
severity_scale, output_schema, escalation_action
```

---

## 8. Flag Catalog — Detailed

### 8.1 interaction_check — deterministic

- **Trigger:** new medication added, or existing dose changes materially
- **Context:** active meds' resolved drug IDs only
- **Check source:** interaction API (RxNorm / DrugBank), not LLM
- **AI job:** none for check; only phrasing result in plain language afterward
- **Output:**
```json
{
  "flag": "interaction_check",
  "severity": "moderate",
  "pairs": [
    {"med_a": "warfarin", "med_b": "ibuprofen", "risk": "increased bleeding risk", "source": "rxnorm_interactions"}
  ],
  "action": "notify_user"
}
```

### 8.2 duplicate_therapy_check — deterministic

- **Trigger:** new medication added
- **Context:** active meds' ingredient + drug-class only (not full record)
- **Logic:** flag if two active meds share active ingredient or therapeutic class (e.g. two NSAIDs, or combo product + standalone overlap)
- **AI job:** none for detection; explanation layer only

### 8.3 allergy_check / contraindication_check — deterministic

- **Trigger:** new med added, or new allergy/condition added (retroactive sweep)
- **Context:** allergies[]/conditions[] + the one med's ingredient class — never whole list
- **Logic:** lookup against contraindication table (e.g. penicillin-class + penicillin allergy; NSAID + late-stage renal impairment)
- **Escalation:** critical severity blocks silent auto-approval — always surfaces before med is marked active

### 8.4 dose_range_check — deterministic

- **Trigger:** medication added/edited
- **Context:** this one med's dose + patient's age/weight/renal-hepatic flags
- **Logic:** compare against standard dosing tables. Flags outliers ("unusually high/low for this age group") as sanity check, always says "confirm with prescriber," never "this is wrong."

### 8.5 dose_timing_conflict_check — deterministic

- **Trigger:** schedule added/edited
- **Context:** active meds' times[] and food/spacing requirements only
- **Logic:** flags same-time conflicts where one needs empty stomach, or drugs requiring minimum gap (e.g. antacid vs. antibiotic absorption windows)
- **AI job:** suggest reshuffled schedule as proposal; user still approves

### 8.6 adherence_drift_check — statistical (no AI for detection)

- **Trigger:** 3+ missed doses in rolling 7-day window for any one med
- **Context:** dose_events for that med only, last 14 days
- **Logic:** pure math (miss rate crosses threshold) — rolling average, not AI
- **AI job:** only surfaced message — turning "62% adherence over 7 days" into non-judgmental, useful language

### 8.7 refill_forecast_check — deterministic

- **Trigger:** daily cron
- **Context:** pills_remaining, dose_per_administration, times[] per med — nothing else
- **Logic:** `days_left = pills_remaining / doses_per_day`; flag at thresholds (7 days, 3 days, 0 days)

### 8.8 taper_progression_check — deterministic

- **Trigger:** daily cron, only for meds with taper schedule defined
- **Context:** that one medication's taper table + today's date
- **Logic:** confirms today's dose matches taper plan; flags if manual edit broke sequence

### 8.9 symptom_correlation_check — AI-heavy (the one genuine AI pattern-match)

- **Trigger:** weekly cron, only if symptom_logs exist for period
- **Context:** symptom_logs + dose_events timestamps for same patient, same week — not other patients, not unrelated meds
- **Logic:** surfaces "headache logged ~2hrs after dose, 4 of last 5 doses" as correlation, explicitly **not** a diagnosis
- **Guardrail:** output must always be "pattern observed, discuss with prescriber," never causal medical claim. Strictest output-contract enforcement — the one place free-text reasoning touches the user.

### 8.10 overdose_risk_check — deterministic, real-time

- **Trigger:** a "taken" dose_event logged where previous taken-event for same med was less than safe-minimum interval ago
- **Context:** just that one med's last 2 dose_events
- **Severity:** always critical — bypasses daily-summary batching, surfaces immediately, notifies caregiver if linked

### 8.11 missed_critical_dose_check — deterministic

- **Trigger:** missed dose_event for medication flagged high_risk (insulin, anticoagulants, anti-rejection, seizure meds — tag set at med-creation time)
- **Context:** that one dose_event + med's risk tag
- **Escalation:** immediate notification, not batched into daily summary

---

## 9. Shared Severity Taxonomy

Every flag reports into the same four buckets so router and UI need no per-flag logic:

| Severity | Behavior |
|----------|----------|
| `info` | Shows in daily summary only |
| `moderate` | Push notification, user must acknowledge |
| `critical` | Immediate alert, blocks silent auto-approval of triggering action |
| `emergency` | Immediate alert + caregiver notification if linked |

---

## 10. What Is Deliberately Not on This List

No flag does open-ended "does this look okay?" reasoning over the full medication list. If it's not one of the above, the router doesn't call the model at all. Anything not covered by a defined flag simply doesn't trigger AI involvement — that's the point of rule-based over agentic.

---

## 11. Implementation Notes

- Resolve to RxNorm/NDC on write; all checks use IDs, never raw strings.
- Interaction and contraindication sources must be attributed in output (`source` field).
- Taper schedules should be stored as an explicit table (date → dose), not inferred from free text.
- Hospital-stay pause is a status (`paused`) on medications/dose_events, not a deletion — preserves history and allows resume.
- Discreet notification mode and emergency widget are OS-integration concerns (notification channel config + lock-screen widget), not flag logic.
- Barcode/NDC scan maps directly to RxNorm resolution step.
- Multi-dependent profiles require patient-scoped queries throughout — context builders must filter by `patient_id`.
- Missed-dose helper is generic text from drug inserts, not personalized advice — store as static guidance per drug, gated behind "discuss with pharmacist" disclaimer.
- Injection site rotation needs its own small table: `injection_sites(medication_id, timestamp, site_code)` + UI rotation suggestion.
- Time-zone toggle affects how `scheduleMedicineReminder` computes next alarm — `body-clock` mode keeps UTC interval, `local-clock` mode keeps wall-clock time.

---

## 12. Architecture Choice Recap

| Approach | Predictability | Auditability | Flexibility |
|----------|---------------|--------------|-------------|
| Rule-based router (chosen) | High | High — each flag independently testable | Lower, but flags are extensible |
| AI orchestrator | Lower | Harder to certify | Higher |

Rule-based is preferred for a health-adjacent app where determinism and certification matter more than open-ended flexibility.
