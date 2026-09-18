# Medac UI/UX Specification

> Companion docs: [`API.md`](API.md) (backend contract, source of truth for endpoints) and [`MEDAC_SPEC.md`](MEDAC_SPEC.md) (product vision, flag architecture, severity taxonomy).
> This doc defines the target UI for the app on the new `muse-spark` API: what each screen shows, how the user moves through the app, and how every flow maps to API calls.

Last updated: 2026-08-19

> **Reading guide:** §1–2 are the philosophy and the map. §3 is the complete visual system — colors, type, motion, components — and is the section to hand to whoever builds the theme. §4 is screen-by-screen. §5–9 are flows, safety, states, and the code migration plan.

---

## 1. Design principles

The UI must be **first class, easy to use, and flow simply**. These principles are the tie-breaker for every design decision below.

1. **One glance answers "what do I take now?"** The Today screen is the product. Everything else supports it.
2. **One primary action per screen.** Taken/Skip on Today. Save on Add Medicine. Confirm on AI proposals. Everything else is secondary.
3. **Progressive disclosure.** Cards start collapsed (name + status badge). Time, dosage, instructions, and history reveal on expand or on detail tap. Density is the enemy of first-class UX in a health app used by stressed, hurried, often older people.
4. **AI fills, the user confirms.** Mirrors the API contract (`requires_user_confirmation: true` everywhere in §16 of API.md). The AI never saves anything by itself — every AI output lands as a pre-filled form or a Confirm/Reject proposal card. No silent writes.
5. **Offline-first.** Reminders and dose logging are local alarms + a local queue; the network is an optimization, never a gate. The UI shows sync state instead of blocking spinners.
6. **Safety escalates visually.** Four severity levels (API §alerts: `info | attention | urgent_review | potential_emergency`) map to four fixed UI treatments. Users learn one pattern, it never changes per feature.
7. **Corrections, not deletions.** The API models corrections (`dose-events/:id/corrections`, symptom corrections) — the UI never offers destructive edits of medical history. Undo snackbar for the 5-second mistakes.
8. **Permissions shape the interface.** Role (owner/manager/contributor/viewer) hides or disables actions before the user can attempt them — the server would reject them anyway (403).
9. **Calm by default, loud only when it matters.** A medication app is opened by stressed, hurried, often older people. Motion is short and purposeful, color is quiet except for status, and nothing pulses, bounces, or sparkles unless a human needs to act *right now*. Delight comes from the app feeling instant and trustworthy, not from decoration.

---

## 2. Navigation & information architecture

### 2.1 App map

```
Launch
 └─ Auth gate (not logged in)
     ├─ Login
     ├─ Register → Verify email
     ├─ MFA code (only if server says mfa_required)
     └─ Forgot password
 └─ Main app (logged in)
     ├─ Today          (default tab)
     │    ├─ Dose cards (expand → Taken / Skip / Add note)
     │    ├─ Alerts strip (open alerts only)
     │    └─ Patient switcher (only when >1 patient)
     ├─ Medicines
     │    ├─ Search + Add
     │    ├─ Medicine detail
     │    │    ├─ Schedule (edit/supersede)
     │    │    ├─ Stock & expiry (inventory)
     │    │    └─ History & adherence
     │    └─ Add Medicine (full-screen): Scan → Review → Schedule → Save
     ├─ Reminders      (active/paused, quiet hours, discreet mode)
     └─ Profile
          ├─ Account & security (password, MFA, sessions)
          ├─ Patients & sharing (memberships, invites)
          ├─ Alert preferences
          ├─ Doctor report (adherence + CSV export)
          └─ Assistant (AI conversations)     [intelligence:read]
```

Bottom navigation keeps the four existing tabs — **Today, Medicines, Reminders, Profile** — no fifth tab. New capabilities slot into existing tabs as detail screens rather than new top-level destinations. An Assistant entry lives in Profile (and a shortcut from Today's overflow) instead of adding a tab.

### 2.2 Multi-patient (caregiver) model

- All data is patient-scoped in the API (`/patients/:patientId/...`). The app always has an **active patient** in the ViewModel state.
- With one patient (the default single user), no switcher is shown anywhere — zero cognitive cost.
- With 2+ memberships, a compact **patient switcher** appears at the top of Today and Medicines (avatar chip + name). Switching patient re-queries everything patient-scoped and re-checks the role for UI gating.
- The active patient's role drives action visibility (see §7.4).

---

## 3. Visual language

### 3.1 Color system

The current Compose theme already has the right bones — the spec formalizes it, adds the missing severity pair, and assigns every token a semantic role so new screens never invent ad-hoc colors.

**Core palette (existing — keep, codify):**

| Token | Hex | Role |
|---|---|---|
| `NavyPrimary` | `#1B4F72` | Primary actions, headers, selected states, links |
| `NavyDark` | `#143B55` | Pressed/active variant of primary, dark header accents |
| `AppBackground` | `#F4F7FA` | Screen background |
| `CardSurface` | `#FFFFFF` | Card and sheet background |
| `BorderSubtle` | `#D7DFE6` | 0.5dp card borders, input outlines |
| `DividerMuted` | `#E6ECF1` | In-card dividers, collapsed-section separators |
| `TextPrimary` | `#16212B` | Titles, medicine names, primary content |
| `TextSecondary` | `#4E6070` | Captions, metadata, secondary text |
| `BlueInfo` / `BlueInfoText` | `#EAF3F9` / `#1B4F72` | Informational fills: AI results, time chips, "Resolved via RxNorm" captions |
| `PausedMuted` | `#EDF2F5` | Paused/discontinued surfaces, disabled fills |

**Status palette (existing — keep):**

| Token | Hex | Meaning |
|---|---|---|
| `StatusGreen` / `StatusGreenContainer` | `#2E7D32` / `#E8F5E9` | Taken, success, in-stock |
| `StatusBlue` / `StatusBlueContainer` | `#1565C0` / `#E8F0FE` | Now / due — the one "act now" status |
| `StatusGray` / `StatusGrayContainer` | `#66737F` / `#F1F4F6` | Upcoming, skipped, neutral |
| `WarningRed` | `#B3261E` | Errors, destructive confirmations |

**New tokens — severity pair (required by §6.1):**

| Token | Hex | Meaning |
|---|---|---|
| `StatusAmber` / `StatusAmberContainer` | `#B26A00` / `#FFF4E0` | `attention` alerts, uncertain AI fields, offline banner |
| `StatusRed` / `StatusRedContainer` | `#B3261E` / `#FDECEA` | `urgent_review` / `potential_emergency` alerts, missed-dose badge accent |

`StatusRed` reuses the existing `WarningRed` hue so red means one thing app-wide: *stop and look*. `StatusAmber` is deliberately a brown-amber (not yellow) to stay legible on white at 4.5:1 with its container.

**Color rules:**
- Status is never color-only — every colored element also carries a text label or icon (§8).
- Only one saturated color per card. A dose card is white with one status badge; a severity banner is the only saturated block on Today.
- No gradients, no glassmorphism, no shadows-as-decoration. Depth comes from border + background contrast only.
- Dark theme: out of scope for v1 of this spec (the app ships light-only today); when added, map tokens 1:1 — never hardcode hex in screens.

### 3.2 Typography

The existing `Type.kt` scale is kept and given semantic assignments so screens stop picking sizes ad hoc:

| Style | Spec | Use |
|---|---|---|
| `headlineMedium` | 28/34 SemiBold | Today date header, empty-state headlines |
| `headlineSmall` | 24/34 SemiBold | Screen titles (Medicines, Profile detail screens) |
| `titleLarge` | 22/28 SemiBold | Card titles, medicine name on detail |
| `titleMedium` | 18/24 Medium | Section headers, dose-card medicine name |
| `bodyLarge` | 16/24 Normal | Primary reading text, form fields |
| `bodyMedium` | 16/22 Normal | Secondary text, captions with emphasis |
| `labelLarge` | 16/20 Medium | Button labels |
| `labelMedium` | 14/18 Medium | Badges, chips, timestamps, "AI confidence" line |

Rules: `sp` everywhere (dynamic type is a hard requirement — many users set system text to Large). Medicine names never go below `titleMedium`. Numbers that matter (dose values, stock counts) use `titleMedium`+ so they're glanceable. Never more than 3 type styles visible on one card.

### 3.3 Shape & metrics

- Cards: `RoundedCornerShape(18–24.dp)`, 0.5dp `BorderSubtle` stroke, no elevation.
- Buttons & inputs: `RoundedCornerShape(14.dp)`; chips and badges: `RoundedCornerShape(50)` (full pill).
- Touch targets: minimum 48dp, primary buttons 56dp (current standard, keep).
- Screen content padding 20dp; card inner padding 16–18dp; card-to-card gap 12dp.
- Buttons: filled `Button` for the one primary action, `OutlinedButton` for secondary, `TextButton` for tertiary/links.
- Iconography: Material Symbols, outlined style, 24dp default / 20dp in chips. Status icons always paired with text.

### 3.4 Motion & feedback

Motion exists to explain *what changed* and *where it went* — nothing more. All durations respect the system "Remove animations" accessibility setting (Compose does this via `Animatable` defaults; never hand-roll infinite loops).

| Moment | Spec |
|---|---|
| Screen transitions | Shared-axis slide+fade, 250ms, `FastOutSlowInEasing`. Bottom-tab switches crossfade only (200ms) — no sliding between tabs |
| Dose card expand/collapse | Height + fade, 250ms. The card grows in place; nothing else moves |
| **Taken / Skip confirmation** | The badge morphs in place: `Now` (blue) crossfades to `Taken ✓` (green) with a 200ms scale-up to 1.06× and settle — the single most-repeated animation in the app, so it must feel instant and satisfying, not bouncy. No confetti, ever |
| Card dismissed (archive, alert acknowledged) | Swipe/fade out 200ms, list collapses the gap 150ms after, Undo snackbar slides up |
| Severity banner arrival | Slides down from under the header once (300ms). `potential_emergency` dialog fades in, no slide — it must not feel playful |
| AI fill-in (Review step) | Fields fade in staggered 40ms apart top-to-bottom (total ≤400ms), suggesting the AI "read top to bottom". Uncertain fields get a single 300ms amber border pulse on first display, then stay static |
| Skeleton loading | Shimmer sweep 1200ms linear, `PausedMuted` base — only on true cold start (§7.1) |
| Sync | `SyncChip` spinner only while actively syncing; queued state is static text ("3 updates waiting") — no perpetual animation |
| Haptics | `HapticFeedbackType.LongPress` on Taken/Skip confirm; `TextHandleMove` on toggle switches; none on navigation or typing. Errors do not vibrate — the red text is enough |

Reduced-motion fallback: all of the above degrade to 100ms crossfades.

### 3.5 Component inventory

**Existing components to reuse as-is:** `StatusBadge`, `EmptySectionCard`, `InfoCard`, `SectionHeader`, `TimeChipRow`, `CameraCaptureCard`, `ConfirmActionDialog`, expandable dose row pattern.

**New components to build:**

| Component | Purpose |
|---|---|
| `SeverityBanner` | Fixed treatments for open alerts on Today (§6.1) — icon + one line + action, colored per severity token |
| `UncertainFieldBox` | Wraps an AI-filled field listed in `uncertain_fields` — 2dp `StatusAmber` left border, `StatusAmberContainer` fill, "Please verify" caption with ⚠ |
| `ClarifyingQuestionRow` | Renders one `clarifying_questions` item inline under the field it concerns — `labelMedium`, `TextSecondary`, prefixed with "?" icon |
| `ProposalCard` | AI proposal with human summary + Confirm / Reject (§5.9) — `BlueInfo` header strip reading "Suggested by assistant" so AI origin is unmistakable |
| `OfflineBanner` / `SyncChip` | Queue depth + last-sync time, non-blocking — thin amber bar / gray chip |
| `PatientSwitcherChip` | Active patient avatar + name + role glyph, dropdown on tap |
| `StockBar` | Horizontal bar: green above threshold, amber below, red at ≤3 days forecast; label "12 left · ~6 days" |
| `SchedulePreviewRow` | Renders `preview: Occurrence[]` after schedule create/draft — `BlueInfo` strip, clock icon, one line |
| `TypeaheadField` | Medicine name field with RxNorm suggestions (§5.2) — dropdown rows show generic name in `TextSecondary` under the brand name |
| `AdherenceRing` | Small circular progress (taken %) for Medicine detail + Doctor report — green ≥80%, amber 50–79%, red <50%, always with the number printed in the center (never color-only) |

---

## 4. Screens

### 4.1 Auth screens

The current code ships username/password screens wired to legacy endpoints (`/medac/api/auth/*`) that **do not exist** in the new API. The new API (`/api/v1/auth`) is **email + password** with mandatory email verification. The auth screens must be rebuilt to match — see §9 for the rewire list.

**Login** (`POST /auth/login`)
- Two fields: Email, Password. One button: **Log in**. Link: "Forgot password?" Link: "Create account".
- On `mfa_required: true` → navigate to **MFA screen**, carry `mfa_session_token`.
- Error mapping (§7.3): `INVALID_CREDENTIALS` → "Incorrect email or password."; `ACCOUNT_LOCKED` (423) → "Too many attempts. Try again in 15 minutes."; `ACCOUNT_NOT_ACTIVE` → "Check your email to verify your account first." with a Resend link.
- Send `device: {platform: "android", display_name: Build.MODEL, timezone: IANA}` so sessions are recognizable in Profile → Security.

**Register** (`POST /auth/register`)
- Email, Password (min 12 chars — enforce client-side with a live checklist, don't let the server be the first to say it), Create account.
- On 201 → **Verify email screen**: short explanation, 6-digit/code entry field, **Verify** (`POST /auth/verify-email`), and **Resend email** (`POST /auth/resend-verification`, rate-limited — disable button with countdown).
- After verify → auto-navigate to Login with email pre-filled.

**MFA** (`POST /auth/mfa/verify`)
- Code field + "Use a recovery code" toggle. On success store tokens and proceed like login.
- Reached only via the login branch — never a standalone destination.

**Forgot password** (`POST /auth/password/forgot` → `POST /auth/password/reset`)
- Email field → always show "If that address exists, a reset link is on its way." (no enumeration, matches API behavior). Reset happens via link; if the link routes back to the app (`medac://reset?token=…`), the Reset screen takes the new password (12+ chars) + token.

**Token handling (invisible to the user)**
- `access_token` in memory only; `refresh_token` in EncryptedSharedPreferences; silent refresh with rotation on 401; reuse-detection (`REFRESH_REUSE`) → wipe session, straight to Login with a neutral "Session expired" message.

### 4.2 Today

The default tab and the heart of the app. Answers, in order: *what do I take now, did I take it, is anything wrong?*

```
┌──────────────────────────────────────────┐
│  Tuesday, 19 August              [👤 Dad▾]│   ← date card; patient chip only if >1 patient
├──────────────────────────────────────────┤
│ ⚠ Low stock: Metformin — 4 days left  →  │   ← SeverityBanner, only when open alerts exist
├──────────────────────────────────────────┤
│ Morning                          [Now]   │
│ ┌──────────────────────────────────────┐ │
│ │ Metformin                  [Taken ✓] │ │   ← collapsed: name + status badge only
│ └──────────────────────────────────────┘ │
│ ┌──────────────────────────────────────┐ │
│ │ Lisinopril                  [Now]    │ │
│ │   (expanded → time, dose, actions)   │ │
│ └──────────────────────────────────────┘ │
│ Afternoon / Night …                      │
└──────────────────────────────────────────┘
```

- **Data:** `GET /patients/:pid/occurrences?from=startOfDay&to=endOfDay` + `GET /patients/:pid/medications` + `GET /patients/:pid/alerts?status=open`, merged with the local Room cache (offline-first; server refresh best-effort on foreground).
- **Header:** date only (existing pattern). No countdown pill.
- **Dose cards (collapsed):** medicine name + `StatusBadge`. That's all. Expand on tap reveals: time chip, dose (`nominal_dose_value` + unit from the occurrence), **Taken** / **Skip** buttons, and "Add note" (symptom log linked to the dose event).
- **States on a dose occurrence** map from the API `state`: `scheduled` → Upcoming (gray), `due` → Now (blue), `taken` → Taken (green), `skipped` → Skipped (gray), `missed` → Missed (gray badge with red left edge — visible concern without alarm, still tappable: late Taken is allowed and logs `actual_at` honestly), `cancelled` → hidden.
- **The Taken moment** is the emotional core of the app: tapping **Taken** flips the badge with the morph animation (§3.4) + a light haptic, and the card auto-collapses after 600ms so the list visibly shrinks toward "done". When the last dose of a time group is logged, the group header gains a quiet green check — no celebration screen, just closure.
- **Grouping:** Morning / Afternoon / Night with auto-scroll to the current period (existing behavior, keep).
- **Log actions:** `POST /dose-events` with `client_event_id = UUID` (idempotent retries), `occurrence_id` from the card, `actual_at = now`, `timezone` = device IANA. Offline → write to local queue, flip badge optimistically, sync via `/dose-events/batch` on reconnect. `outcome: "duplicate"` is a success.
- **Snooze** (existing "Postpone 10m") → `POST /dose-events` with `event_type: "snoozed"` + local alarm reschedule.
- **PRN meds** (no occurrence) show in their own slim "As needed" row under the last time group — one tap logs a taken event with `occurrence_id: null`.

### 4.3 Medicines

- **Header:** Search field + **+ Add** button side by side (existing layout, keep). Nothing else.
- **List cards:** medicine name only + chevron (existing `MedicineNameCard`, keep). Filter by name/generic client-side. Active meds first; paused/discontinued collapse under a muted divider ("Paused (2)", "Ended (1)") — hidden by default.
- Swipe-to-delete is replaced by **swipe-to-archive** with an Undo snackbar (see §1.7; the API has no delete, only `archive`).

**Medicine detail** (tapped card)

Sections, top to bottom — all read-first, edit on tap:

| Section | Content | API |
|---|---|---|
| Identity | Name, generic (`drug_concept` name when resolved), strength, form, route; normalization status chip | `GET /medications/:id` |
| Status | Active / Paused / Discontinued + actions: Pause ⇄ Resume, Discontinue, Archive | `/pause`, `/resume`, `/discontinue`, `/archive` |
| Schedule | Times or interval/PRN/cyclic/taper summary; "Edit schedule" → supersede flow with preview | `GET /schedules`, `POST /schedules/:vid/supersede` (returns `preview`) |
| Stock | `StockBar`: remaining vs low threshold, forecast days, "+ Update stock" (fill / used / lost) | `GET /inventory`, `POST /inventory/transactions`, `PATCH /inventory/settings` |
| Expiry | Next expiration date + lot, "Add expiry" | `/expirations` CRUD |
| High-attention | Toggle "High-attention medicine" (`high_attention_user_flag`) — feeds missed-dose alerts | `PATCH /medications/:id` |
| Adherence | Taken % for range (uses report math: taken / eligible, PRN excluded) + History list with per-entry "Correct" action | `GET /reports/adherence`, `GET /dose-events`, `POST /dose-events/:id/corrections` |
| Notes | Instruction text from label | `label_instructions_text` |

Status transitions always confirm with a one-line reason field when the API wants context ("Discontinue — finished course / stopped by doctor / other"). Edits send `expected_updated_at` for optimistic locking; a 409-style conflict shows "This was changed on another device — refresh?" instead of overwriting.

### 4.4 Add Medicine — the flagship flow

Three steps, one screen each, back navigation allowed, everything pre-filled by AI when a photo is taken. Target: **photo to saved medicine in under 60 seconds.**

**Step 1 — Capture** (existing `CameraCaptureCard`)
- Big camera card → `TakePicture()` → local ML Kit OCR immediately, then `POST /intelligence/label-interpretations` with the image (base64, ≤4MB after the existing 1024px/JPEG-85 compression) or `ocrText` when the image upload is undesirable.
- "Add manually instead" link below the card for the no-photo path (name typeahead via public `GET /drug-normalization/search?q=`).

**Step 2 — Review** (AI-filled form, user confirms)
- Fields pre-filled from `interpretation`: name (→ resolved `drug_concept` via `resolve-ndc`/`search` when confident), generic name, strength value+unit, form, route, label instructions.
- Every field listed in `uncertain_fields` renders inside `UncertainFieldBox` (amber border, "Please verify"). `clarifying_questions` render inline as small prompts. `confidence` shows as a single quiet line ("AI confidence: high/medium/low") — never a number to the user.
- The **only field the user must usually touch is dosage** — pre-filled with `dose_quantity_value`/`unit` from the interpretation, presented as the existing Half/Full segmented control for tablets, numeric field otherwise.
- Save of this step does **not** create anything yet — it just advances to Step 3 with a draft held in memory (`status: "draft"` on create if the user abandons mid-flow on a retry, so nothing is lost).

**Step 3 — Schedule**
- Default: fixed times picker (Morning/Afternoon/Night quick chips + exact-time chips, existing pattern).
- "More options" (collapsed): every-8-hours (elapsed interval), as-needed (PRN), on/off cycle days, taper steps. Each advanced type gets a tiny one-line explainer — not a manual.
- Before Save: `SchedulePreviewRow` shows what the server preview returns ("You'll take this at 08:00 and 20:00 daily · next dose tomorrow 08:00").
- **Save** = `POST /medications` (status `active`) then `POST /schedules` with the chosen `CreateScheduleSchema`; then schedule local alarms from the returned occurrence preview. One confirmation dialog (existing `ConfirmActionDialog`), then land on Medicines with the new card.
- Travelers' toggle lives here for interval schedules: **Body clock** (`timing_mode: "elapsed_interval"` — keeps the interval across time zones) vs **Local clock** (`local_clock` — shifts to wall time). Only visible for interval-type schedules.

### 4.5 Reminders

- Active and paused lists (existing layout, keep). Each row: time, medicine name, edit-time, pause/resume switch. Pause = medicine-level `POST /medications/:id/pause` for whole-med pause, or alert-preference/quiet-hours for time-level control — one row per active schedule time as today.
- **Quiet hours & channels** (new card at top): per patient `PUT /alert-preferences` (delay minutes, quiet start/end, timezone) and notification privacy — **Discreet notifications** toggle mapping to the patient's `notification_privacy_mode: private|generic|detailed`. Discreet = lock-screen text becomes just "Reminder". This is a one-tap setting with a plain-language explanation; it exists for mental-health, HIV, and fertility meds.
- Local `AlarmManager` remains the delivery source of truth offline (existing `ReminderScheduler`); on fire, the notification's actions are **Taken** / **Snooze 10m** (posting dose events through the same queue as the app).

### 4.6 Profile

Grouped cards, each one a detail screen — the Profile list itself stays a simple menu:

- **Account** — email, change password (`POST /auth/password/change` — warn "this logs out your other devices"), log out (`/auth/logout`), log out everywhere (`/auth/logout-all`).
- **Security** — MFA setup (`/auth/mfa/totp/setup` → QR → `/confirm` → show the 10 recovery codes once with a "I saved them" checkbox), active sessions list with revoke (`GET/DELETE /auth/sessions`).
- **People & sharing** — patient list; per patient: members + roles, invite by email with role picker (`POST /patients/:pid/invites`), pending invites with revoke, accept-flow for invitees (`POST /invites/:token/accept`). Role changes that the API guards with recent-auth (transfer ownership, revoke) re-prompt for password first — the UI explains why.
- **Alert preferences** — per alert type (`low_stock`, `expiration`, `missed_user_attention_med`, `stale_device`, `generic`): enabled, delay, quiet hours (`GET/PUT /alert-preferences`).
- **Doctor report** — date range picker → adherence summary card (`GET /reports/adherence`) + "Export CSV" (`POST /exports` → poll status → `GET /exports/:id/download?token=` when ready; show "link valid 1h"). One share intent straight to the mail/print dialog.
- **Assistant** — entry point to AI conversations (§5.9).
- **About / privacy** — data-at-a-glance: what's stored locally, what syncs, request-id in bug reports.

### 4.7 Assistant (AI conversations)

One screen, chat-shaped, deliberately plain:

- `POST /intelligence/conversations` on open (silent), `POST .../messages` per turn. Conversations expire after 72h server-side — the UI says so up front ("Notes are temporary and not medical advice").
- Each assistant reply may carry **proposals** — rendered as `ProposalCard`: human summary, what it will change, **Confirm** / **Reject**. Confirm sends `payload_hash` (+ `expected_resource_versions`) to `/proposals/:id/confirm`; high-impact actions re-ask for password (server's 5-min recent-auth rule). Expired proposals (30-min TTL) collapse to "Expired — ask again".
- Replies show a small "Based on: N facts" footer from `facts_used` — grounding made visible, no hallucinated authority.
- Also here: **Schedule draft** shortcut ("Plan my times") → `POST /intelligence/schedule-drafts` with wake/sleep times → draft times as a ProposalCard, never auto-applied.

---

## 5. Core flows (step → API)

### 5.1 First run

1. Register (`/auth/register`) → Verify email (`/auth/verify-email`) → Login (`/auth/login`).
2. Empty patients → inline prompt on Today: "Who is this medication plan for?" → name + timezone (default device) → `POST /patients`. (Skippable only by creating one — the API requires a patient for everything else.)
3. Empty Today → single EmptyState with one button: **Add your first medicine** → Add Medicine flow.
4. First medicine saved → first occurrence preview → schedule local alarms → done.

Total: register → 4 taps → photo → confirm → saved. No tour, no tooltips.

### 5.2 Add medicine (scan)

`TakePicture` → ML Kit OCR (local) → `POST /intelligence/label-interpretations` (image or `ocrText`) → review form (uncertain fields highlighted, user confirms — the only manual field is usually dosage) → schedule picker + server `preview` → `POST /medications` + `POST /schedules` → local alarms → Medicines list.

Failure handling: AI unavailable (503/502) → fall back to the local OCR draft (existing `buildDraftSuggestion` heuristic) with a banner "Filled from on-device scan — please double-check", plus Retry. No dead ends, no placeholder data.

### 5.3 Daily dose loop

Today → expand card → **Taken** (`POST /dose-events`, `client_event_id`, optimistic badge flip) → done. Offline: badge flips anyway, event queued, `POST /dose-events/batch` on reconnect, `duplicate` treated as success. Notification actions run the same path from the lock screen.

### 5.4 Fix a wrong log

History row → **Correct** → Taken↔Skipped, time, or note + required reason → `POST /dose-events/:id/corrections`. The original entry stays visible but marked corrected — history is never rewritten (§1.7).

### 5.5 Refill & expiry

Medicine detail → Stock → "+ Update stock" → quantity + type (`fill` / `manual_adjustment` / `lost_or_damaged` / `disposed` / `transferred`) → `POST /inventory/transactions`. `low_stock` and `forecast_days` come back from `GET /inventory`; threshold editable via `PATCH /inventory/settings`. Daily-cron alerts surface as SeverityBanners (§6.1).

### 5.6 Alerts

Generated server-side (cron + events) and listed via `GET /alerts?status=open`; acknowledge per alert (`POST /alerts/:id/acknowledge`) or from the banner's "Done" action. Batching and delays are preference-driven (§4.6), so the UI never needs per-alert logic — one severity pattern, everywhere.

### 5.7 Caregiver sharing

Profile → People & sharing → Invite (email + role) → `POST /patients/:pid/invites` → accepter taps link → `POST /invites/:token/accept` → patient appears in their switcher with the granted role. Invitee never sees members management unless owner/manager.

### 5.8 Doctor visit

Profile → Doctor report → pick range → adherence % + counts (`GET /reports/adherence`, PRN excluded by the server's denominator — the UI states "as-needed medicines not counted" in the caption) → Export CSV (`POST /exports` → poll → download, 1h link) → share sheet.

### 5.9 AI proposal lifecycle

Assistant reply contains proposal → `ProposalCard` (summary + Confirm/Reject) → Confirm (`payload_hash` match, optional recent-auth) → executed → snackbar "Done" + affected screen refresh. Reject → card collapses, never reappears. Expired → clearly dead, invite re-asking.

---

## 6. Safety & AI trust patterns

### 6.1 Severity → UI treatment (fixed, app-wide)

From API alert severity (`info | attention | urgent_review | potential_emergency`) / MEDAC_SPEC §9:

| Severity | Treatment |
|---|---|
| `info` | Gray/blue chip in the Alerts strip; no push, no interruption |
| `attention` | Amber `SeverityBanner` (`StatusAmberContainer` fill, `StatusAmber` icon+text) pinned under Today header + push with acknowledge |
| `urgent_review` | Red `SeverityBanner` (`StatusRedContainer`, full-width), acknowledge required; push is high-priority |
| `potential_emergency` | System alert dialog on arrival, red, blocks until acknowledged; caregiver notification happens server-side |

One color per level, one interaction per level, never customized per feature. Banners stack max 2, then collapse into "+2 more alerts" — the Today screen must never become a wall of warnings.

### 6.2 Uncertainty is visible

Anything the model flagged (`uncertain_fields`, low `confidence`, `clarifying_questions`) must be visibly marked in the UI before save. Confident fields look normal; uncertain fields ask to be checked. The user should never wonder which parts the AI guessed.

### 6.3 Attribution over authority

Where the server provides sources (normalization via RxNorm, interaction flags per MEDAC_SPEC), the UI shows "Resolved via RxNorm" style captions. AI explanations are labeled as explanations; deterministic results are labeled with their source. No screen ever says "the AI thinks" about a safety fact.

---

## 7. States & error handling

### 7.1 The four states of every list/screen

- **Loading** — skeleton cards, not spinners, on Today/Medicines (data is usually cached; skeletons only on true cold start).
- **Empty** — one sentence + one action ("No medicines yet" + **Add medicine**). Never a blank screen.
- **Error** — inline card with Retry and the server's human message; never a toast-and-vanish.
- **Content** — the happy path above.

### 7.2 Offline & sync

- `OfflineBanner` (thin, amber, non-modal) when the device is offline or the last sync failed; `SyncChip` shows queued events count ("3 updates waiting").
- Incremental sync via `GET /patients/:pid/sync?cursor=` with the cursor persisted; conflicts resolved server-wins for reads, queue-retry for writes (idempotent by `client_event_id`).

### 7.3 Error code → user message (API §1.3)

| Code | UI |
|---|---|
| `VALIDATION_ERROR` | Field-level errors from `details.validation` |
| `UNAUTHORIZED` | Silent refresh → else session-expired → Login |
| `FORBIDDEN` / `PATIENT_MISMATCH` | "You don't have access to this" + hide the action |
| `NOT_FOUND` | "This was removed on another device" + refresh |
| `RATE_LIMITED` | Gentle retry with backoff, no alarm |
| `ACCOUNT_LOCKED` | Countdown message |
| `5xx` / `INTERNAL_ERROR` | Generic message + request-id in the bug-report path |

Always log `request_id` locally for support correlation.

### 7.4 Role gating

Before rendering, filter actions by permission: viewer sees no write buttons at all; contributor sees dose logging + notes but no schedule/med edits; manager like owner minus destructive patient actions; owner sees everything. Server still enforces — the UI just never sends what would bounce.

---

## 8. Accessibility & privacy

- 56dp primary targets, 48dp minimum everywhere (current standard, keep); dynamic type respected (`sp` everywhere — current code complies).
- Status is never color-only: every badge carries a text label (Taken / Now / Missed…), banners carry icons, the `AdherenceRing` prints its number.
- All status/text color pairs in §3.1 meet WCAG AA (4.5:1) on their container colors — verified when the tokens were chosen; any new token must be checked before merge.
- Motion respects the system reduced-motion setting (§3.4 fallback); nothing flashes faster than 3/s.
- TalkBack order on Today: alerts → current time group → dose cards in time order. A dose card reads as one element: "Lisinopril, due now, button: expand".
- Discreet notification mode (§4.5) is patient-level, one toggle, plain language.
- Photos stay in app-private storage; OCR runs on device first; the AI receives a compressed image only when the user taps identify.
- Emergency ID / widget, "pill looks different" comparison, missed-dose helper: **not in the current API** — listed in MEDAC_SPEC as future; UI reserves no placeholders for them (no dead UI).

---

## 9. Implementation bridge (current code → target)

What exists today vs. what this spec requires:

| Area | Current | Target |
|---|---|---|
| Auth | `AuthRepository` posts **username** to legacy `/medac/api/auth/*` (endpoints don't exist in new API); `AuthScreens` are username/password | Email + password per §4.1; verify-email + MFA + forgot screens; token pair with rotation (API §3) |
| AI identify | `MedicineAiRepository` → legacy `/medac/api/medicine/identify` | `/patients/:pid/intelligence/label-interpretations` + `instruction-parses`; render `uncertain_fields`/`clarifying_questions` |
| Data | SharedPreferences JSON (`medicines`, `dose_logs`, `reminder_state`) | Room cache keyed by patient + `/sync` cursor; SharedPreferences only for session/prefs |
| Dose logging | Local-only `markDoseTaken/Skipped`, `snoozeDose` | Same UX + `POST /dose-events` with `client_event_id`, batch queue, corrections |
| Schedules | Flat `times: List<String>` per medicine | `CreateScheduleSchema` types (fixed/interval/PRN/cyclic/taper) + occurrence preview + supersede versioning |
| Medicines | Local add/remove with undo | Patient-scoped CRUD + status machine (draft/active/paused/discontinued/archived), archive-not-delete |
| New surfaces | — | Alerts + preferences, inventory/expiry, adherence report + export, assistant + proposals, people & sharing |
| Networking | `HttpURLConnection` hand-rolled JSON; Retrofit `ApiService` unused | Retrofit + OkHttp per API §17.1 (auth interceptor, refresh on 401, `X-Request-ID`), decimals as strings |

Build order suggestion (each step shippable): ① rewire auth → ② patient scoping + Room cache → ③ medications/schedules server-backed with local queue → ④ dose-event sync → ⑤ alerts/inventory/report → ⑥ assistant & proposals.
