# Medac — Technical Flow (for explaining to a teacher)

> A high-level walkthrough of **what** Medac does and **which technologies/services** it uses, in the order data flows through the system.
> This is *not* an implementation doc — no code, no internals. It's the story of how a medicine photo becomes a daily reminder, told with the real names of the pieces involved.

Last updated: 2026-08-19

---

## 1. The big picture

Medac is an **Android app** (Kotlin + Jetpack Compose) talking to a **backend server** over the internet. The phone and the server share the work:

| Where | What lives there |
|---|---|
| **Your phone** | The screens you see, the camera, reminders (alarms + notifications), a local copy of your data, and the offline "taken" log |
| **The backend server** | Your account, your saved medicines (the "database of truth"), the AI services, drug-name normalization, alerts, reports |
| **External services** | Google Lens OCR (reads text from photos), RxNorm drug database (real medicine names), the AI model (understands the text) |

```
┌────────────────────────── YOUR PHONE ──────────────────────────┐
│  Camera ──► Google Lens OCR (reads label text)                 │
│       │                                                       │
│       ▼                                                       │
│  Photo + text ──► (internet) ──► Medac server ──► AI engine   │
│       ▲                           │  ▲                        │
│       │                           │  └── RxNorm (drug names)  │
│       │                           ▼                           │
│  Review screen ←── JSON draft (name, dose, times)             │
│       │                                                       │
│       ▼                                                       │
│  User taps Save ──► medicine saved (phone + server)           │
│       │                                                       │
│       ▼                                                       │
│  AlarmManager schedules daily reminders ──► notifications      │
│       │                                                       │
│       ▼                                                       │
│  User taps "Taken" ──► dose logged (phone + server)            │
└────────────────────────────────────────────────────────────────┘
```

**The one-sentence version:** *the camera takes a photo, Google Lens turns it into text, our server's AI turns that text into a medicine record, and Android's alarm system turns that record into daily reminders.*

---

## 2. The flow, step by step

### Step 1 — Take the photo (on the phone)

- The user taps a big camera card. The app opens the **Android camera** (via the system camera app — `TakePicture`), or lets the user pick a photo from the gallery.
- The photo is stored in a **private app folder** on the phone (not in the public gallery).
- Before anything is sent, the app **prepares the image**: rotates it the right way up, shrinks it to a reasonable size, and compresses it to JPEG. This keeps uploads small and fast.

### Step 2 — Read the text: Google Lens OCR (external service)

- The app sends the prepared photo to **Google Lens's text-reading endpoint** (`lensfrontend-pa.googleapis.com/v1/crupload`) — the same kind of service that powers "translate text in a photo" on Google phones.
- The request is sent in a compact binary format called **Protobuf** (the format Google services use internally), not plain JSON.
- The response comes back as structured text: **blocks → lines → words**, plus each word's position and a confidence estimate.
- Result: the app now has the *text* that was printed on the medicine label, even though it doesn't yet know what any of it means.

> **Why Google Lens and not something else?** It's free, fast, very accurate on printed text, and it returns *structured* text (words with positions), which makes the next step — the AI — much easier.

### Step 3 — Understand the text: the server's AI (backend + external model)

Two paths exist in the code, but the one used at runtime is the **text path**:

- **Path A — image path (available, not primary):** the app can upload the (compressed) photo to the server, and the server's AI vision model *looks* at the label — name, dosage, form, directions — and returns a structured result.
- **Path B — text path (the one actually used):** the app sends the **OCR text** from Step 2 to the server (`POST /intelligence/label-interpretations`). The server's AI language model reads it like a pharmacist would: "Lisinopril 10 mg, take once daily" → `name: Lisinopril`, `dose: 10 mg`, `form: tablet`, `times: [08:00]`.

The server responds with a **structured JSON "interpretation"** — the AI's best guess at each field, with a **confidence score** (0–100%) and a list of fields it's **unsure about** (`uncertain_fields`).

**What the AI returns (the "format"):**

```json
{
  "interpretation": {
    "candidate_name": "Lisinopril",
    "candidate_generic_name": "Lisinopril",
    "strength_value": "10",
    "strength_unit": "mg",
    "form": "tablet",
    "route": "oral",
    "label_directions_text": "Take once daily",
    "uncertain_fields": ["strength_value"],
    "clarifying_questions": ["Is the dose 10 mg or 20 mg?"],
    "confidence": 0.92
  },
  "requires_user_confirmation": true
}
```

Note the last line: **`requires_user_confirmation: true`** — the AI is *never* allowed to save anything by itself. It only fills in a form; a human always checks and taps Save.

> **If the AI service is down or the photo is unreadable:** the app falls back to a **simple on-phone text scan** (the OCR text itself) with basic pattern-matching — it looks for words like "mg", "twice daily", "once daily" and guesses. The user is warned to double-check these guesses.

### Step 4 — User reviews and saves

- The phone shows the AI's draft as a **form**: medicine name, dosage, purpose, instructions, suggested times.
- Fields the AI was unsure about are highlighted (amber border, "please verify").
- The medicine name field is checked against **RxNorm** — a real database of medicine names run by the US National Library of Medicine — so "Tylenol" becomes the canonical "acetaminophen" and can be matched against other medicines later.
- When the user taps **Save**, the record is written **both** on the phone (local storage) and to the server (the database of truth). This is the only moment anything is permanently saved.

### Step 5 — Schedule reminders (on the phone)

- For each chosen time (e.g., 08:00 and 20:00), the app uses Android's **AlarmManager** to schedule an exact alarm.
- When the alarm fires, a **notification** appears: *"Don't forget to take Lisinopril (10 mg)"* with **Taken / Snooze 10 min** actions.
- These alarms live **on the phone itself** — reminders keep working with no internet, in airplane mode, even if the server is unreachable.
- Each reminder is keyed by a unique ID built from `medicine name + time`, so pausing or editing one never touches the others.

### Step 6 — Log doses (phone + server)

- Tapping **Taken** (in the app or on the notification) records a **dose event** — which medicine, which scheduled time, when it was actually taken.
- **Online:** it's sent straight to the server.
- **Offline:** it's saved in a local queue with a unique event ID, and pushed to the server when the connection returns. The server treats duplicates as "already done", so nothing is ever double-logged.
- This log is the raw material for the **adherence report** (the "you took 90% of doses" chart) and the **doctor report / CSV export**.

---

## 3. The services and technologies, at a glance

| Piece | What it is | What it does in Medac |
|---|---|---|
| **Kotlin + Jetpack Compose** | Modern Android UI framework | Every screen you see |
| **Android Camera (`TakePicture`)** | System camera integration | Takes the label photo |
| **Google Lens OCR** | Google's text-recognition service (Protobuf API) | Turns the photo into readable text |
| **Medac backend server** | Our own API server (Node/Express + PostgreSQL) | Accounts, the master medicine database, AI orchestration, alerts, reports |
| **Muse Spark (AI model)** | The AI engine on the server | Reads labels, parses instructions, answers questions, drafts schedules, summarizes adherence |
| **RxNorm** | US National Library of Medicine drug database | Converts brand names → official drug IDs, checks interactions |
| **JSON** | Universal text data format | How the phone and server exchange data |
| **Protobuf** | Google's compact binary format | How the phone talks to Google Lens OCR |
| **AlarmManager + Notifications** | Android's alarm/notification system | Fires the daily reminders, even offline |
| **Local storage + server DB** | Phone: SharedPreferences/Room; Server: PostgreSQL | Two copies of your data (phone-first, server as truth) |

---

## 4. The AI features (what the "AI" actually does)

Medac uses AI in **five places**, and every one follows the same safety rule: **AI proposes, the user confirms.**

| Feature | What the AI does | Input → Output | Needs human confirmation? |
|---|---|---|---|
| **Label interpretation** | Reads a medicine photo or its OCR text and fills in a form (name, dose, form, directions) | photo/OCR text → structured JSON fields + confidence | ✅ always |
| **Instruction parsing** | Takes a typed instruction like "take 5 ml after meals twice a day" and turns it into structured dose info | free text → `{dose_value, dose_unit, frequency_text, route}` | ✅ always |
| **Assistant chat** | Answers questions about *your own data* ("when will I run out of Metformin?") with facts pulled from your records | chat message → reply + "facts used" list | ✅ (changes come as Confirm/Reject cards) |
| **Schedule drafts** | Suggests dose times around your wake/sleep schedule | wake/sleep times → proposed times | ✅ always |
| **Summaries** | Summarizes adherence/inventory/timeline for a date range | date range → plain-language summary | read-only, no action needed |

**Key safety design — "proposals":** when the AI chat wants to *change* something (e.g., "I'll update your Metformin schedule"), it doesn't do it. It creates a **proposal** — a card saying *"I suggest changing X. Confirm or Reject?"* — with a short expiry (30 minutes). Only a tap on **Confirm** by a real person (with the right permission) executes it. Every AI action is logged server-side with a run ID, so any of them can be audited.

**Data honesty:** the AI is only given the **minimum data** each task needs. For example, an interaction check sees only your active medicines' drug IDs — not your symptom history. This reduces the chance of the AI making things up, and every medical fact it reports is labelled with its **source** (e.g., "Resolved via RxNorm").

---

## 5. Behind the scenes: what keeps it safe and correct

### 5.1 Deterministic checks before AI guessing

Not everything is AI. The server runs **rule-based safety checks** on real data:

| Check | Trigger | Logic |
|---|---|---|
| **Drug interaction** | New medicine added | Looks up the real pair in a drug interaction database (RxNorm) |
| **Duplicate therapy** | New medicine added | Flags two meds with the same active ingredient (e.g., two acetaminophen products) |
| **Dose range sanity** | Medicine added/edited | Compares the dose against standard dosing tables for age/weight |
| **Refill forecast** | Daily | Simple math: `pills_left ÷ doses_per_day = days_left`, alerts at 7/3/0 days |
| **Expiry** | Daily | Alerts when a saved expiry date approaches |
| **Missed critical dose** | A dose is missed | Immediate alert for high-risk meds (insulin, anticoagulants) |

The rule-based checks come **first**; AI is only used to *explain* results in plain language. The app tells the user where every fact came from.

### 5.2 Offline-first

- **Reminders:** scheduled on the phone → always fire, no internet needed.
- **Dose logging:** queued on the phone with unique IDs → synced later, duplicates safely ignored.
- **The UI shows sync state** ("3 updates waiting") instead of blocking or failing.

### 5.3 Security

- Passwords are never stored in plain text (server hashes them).
- Login issues **two tokens**: a short-lived access token (kept in memory only) and a long-lived refresh token (stored encrypted on the phone). Access tokens are silently renewed.
- Optional **two-factor authentication (MFA)** with recovery codes.
- Every API request is authorized; caregivers only get the permissions their **role** allows (owner / manager / contributor / viewer).
- AI conversations expire after **72 hours** server-side.

---

## 6. Data flow summary (one diagram)

```
 USER TAKES PHOTO
      │
      ▼
 [Phone] prepare image (rotate, shrink, compress JPEG)
      │
      ├───────────────────────────────► [Google Lens OCR]  (Protobuf request)
      │                                        │
      │                                        ▼
      │                              blocks → lines → words (+confidence)
      │                                        │
      ▼                                        ▼
 [Medac server] ◄── photo OR OCR text ──┘
      │
      ▼
 [Muse Spark AI] ──► interpretation JSON (name, dose, form, times,
      │               uncertain_fields, confidence, requires_user_confirmation)
      ▼
 [Phone] Review form → user edits → user confirms
      │
      ▼
 Save medicine (phone local + server DB) → RxNorm name check
      │
      ▼
 [Phone] AlarmManager schedules alarms for each dose time
      │
      ▼
 Daily: notification fires → user taps Taken → dose event logged
      │                    (offline: queued + synced later)
      ▼
 Reports: adherence %, doctor report, CSV export
      │
      ▼
 Alerts: low stock, expiry, interactions (rule-based, server cron)
```

---

## 7. If the teacher asks "why did you build it this way?"

Short answers worth remembering:

1. **"Why Google Lens for OCR?"** — It's free, very accurate on printed text, and returns structured text (words + positions), which makes the next AI step reliable.
2. **"Why a server, why not everything on the phone?"** — The heavy AI model can't run well on a phone; the server also gives one shared "database of truth" so a caregiver's phone and your phone see the same data.
3. **"Why does the AI need confirmation?"** — It's a health app. The AI is allowed to *guess*, but only a human can *decide*. Every AI output is a pre-filled form or a Confirm/Reject card, never a silent change. `requires_user_confirmation: true` on every AI response is a deliberate contract.
4. **"Why two databases?"** — Phone-first storage means reminders and logging work offline (a medication app that dies without wifi is useless). The server copy keeps everyone in sync and powers the safety checks.
5. **"Why rules AND AI?"** — Drug interactions and stock counts are *facts* — a rule-based lookup with a real database is more trustworthy than AI guessing. AI is used only where language understanding is needed (reading labels, explaining, summarizing). "Deterministic source first, AI as interpreter."

---

## 8. Glossary (plain definitions)

| Term | Meaning |
|---|---|
| **OCR** | Optical Character Recognition — turning a photo of text into actual text |
| **Protobuf** | A compact binary format for sending data, used by Google services |
| **JSON** | A readable text format for sending data between phone and server |
| **Endpoint** | A specific URL on a server that accepts a specific type of request |
| **API** | The set of "endpoints" the phone uses to talk to the server |
| **AI model** | A program trained on data to understand text/images (here: the Muse Spark model) |
| **Prompt** | The instructions+context given to the AI model for one task |
| **Confidence** | How sure the AI is, 0–100% |
| **Structured data** | Data in defined fields (name, dose, time) instead of free-form sentences |
| **AlarmManager** | Android's built-in system for scheduling alarms/notifications |
| **Dose event** | A record that a specific dose was taken / skipped / missed |
| **Adherence** | The percentage of scheduled doses actually taken |
| **RxNorm** | The US government's database of standard medicine names and codes |
| **Idempotent** | Safe to retry — sending the same event twice records it only once |
