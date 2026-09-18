# Medac User Manual

> A plain-language guide to the Medac app — written for people who just want to use it.
> No technical jargon, no code, no assumptions about how phones "work". If something sounds too techy, skip it and follow the taps.

Last updated: 2026-08-19

---

## 1. What is Medac?

Medac is a **medicine reminder app** for your Android phone. It helps you:

- **Keep a list** of all the medicines you (or someone you care for) take.
- **Remember when to take them** — it sends you a notification at the right time every day.
- **Track whether you took them** — one tap, and it's recorded.
- **Spot problems early** — like running out of a medicine, or a medicine about to expire.
- **Make sense of a prescription or medicine label** — take a photo and the app reads it for you (more on that below).

**The one-line summary:** you take a photo of your medicine label (or type it in), the app saves it and reminds you every day, and you tap "Taken" when you swallow the pill.

---

## 2. Before you start

### 2.1 What you need

- An Android phone (Android 7 or newer).
- An internet connection for the first setup and for the "AI reads the label" feature.
- An email address you can check (used for creating your account).

### 2.2 First-time setup — the 2-minute version

The very first time you open the app you'll be asked to sign in. Here's the whole journey:

1. **Create an account** — enter your **email address** and choose a **password** (at least 12 characters; the app shows you a little checklist while you type, so just make it long and a bit weird, like `blue-elephant-42`).
2. **Check your email** — the app sends you a verification link (a long code). Open the email, copy the code/link into the app's verification box (or tap the link if it opens the app directly).
3. **Sign in** — now use your email and password to log in.
4. **Tell the app who the plan is for** — the app asks something like *"Who is this medication plan for?"*. This is where you type your name (or the name of the person you're caring for). This matters only if you manage medicines for more than one person (see [Caring for someone else](#91-caring-for-someone-else-multi-patient-mode)).
5. **Add your first medicine** — tap the big button that says **Add your first medicine**. This opens the Add Medicine flow, which is the next section.

> **The quick welcome screen:** right after the app opens you'll see a green screen asking for a "Security PIN". It's optional — you can tap **Continue** without typing anything (or type any 4–6 digit number; it's not checked against anything yet). This is a leftover gate from an earlier version of the app and takes one tap to get past.

> **If you already have an account:** just open the app and tap **Log in**. If you forgot your password, tap **Forgot password?** — the app will email you a reset link.

> **If you get a message like "Too many attempts":** you typed the password wrong several times. Wait about 15 minutes and try again.

---

## 3. The four tabs (how to move around)

The app has a menu bar at the bottom with four buttons. Think of them as four rooms in a house:

| Tab | What it's for | When you'll use it |
|---|---|---|
| **Today** | What to take *right now* | Every day, several times a day |
| **Medicines** | Your full list of medicines | When adding, editing, or checking a medicine |
| **Reminders** | The notification schedule | When you want to change or pause notifications |
| **Profile** | Your account, reports, and settings | Occasionally |

That's it — four tabs, and almost everything else in the app opens *from* one of these four places.

---

## 4. Today — your daily dose plan (the main screen)

This is the screen the app opens on, and the one you'll see most.

### 4.1 What you'll see

- **The date** at the top.
- **Your doses grouped by time of day** — **Morning**, **Afternoon**, **Night**.
- Each medicine appears as a small white card showing just its **name** and a **status label** (Upcoming / Now / Taken / Missed).
- If anything needs attention — like a medicine running low — a colored warning banner appears near the top.

### 4.2 Example — a normal morning

Let's say you take two medicines:

- **Metformin** (for diabetes) — morning and evening.
- **Lisinopril** (for blood pressure) — morning only.

At 7:45 am you open the app. Under **Morning** you see:

```
Metformin      [Now]
Lisinopril     [Now]
```

The blue **Now** label means these are due right now.

### 4.3 Marking a dose as taken (the most important tap)

1. **Tap the medicine card** to expand it. It now shows the time, the dose ("500 mg"), and two buttons: **Taken** and **Skip**.
2. **Tap Taken** the moment you actually take the medicine.
3. The label flips to a green **Taken ✓** and the card tucks itself back in.

That's the whole daily routine: *take the pill → open app → tap Taken.* Two taps total.

> **What if I forgot to tap it?** No problem. If you tap **Taken** after the time has passed, the app records it honestly as "late but taken". If you never took it, tap **Skip** instead — or leave it alone and it will show as **Missed** with a red edge. You can still change a Missed dose to Taken later in the day (see [Correcting a mistake](#52-correcting-a-mistake)).

### 4.4 Example — a missed dose

It's 9 pm and you realize you never took your evening Metformin. You open the app and the card looks different: it has a **thin red strip on the left edge** and says **Missed**.

- Tap the card → tap **Taken** (if you take it now, late is better than never), or
- Tap **Skip** to close it out honestly.

### 4.5 The "As needed" section

Medicines you take only sometimes — painkillers, allergy pills, "take when you have a headache" types — appear at the bottom under **As needed** instead of at set times.

**Example:** You have a headache and your doctor said to take Ibuprofen when needed. Open the app, scroll to **As needed**, and tap the **Take** button on the Ibuprofen row. The app logs that you took it — and since it's "as needed", there's no set time for it to nag you about.

### 4.6 Adding a note to a dose

Expand a dose card and tap **Add note**. A small window opens where you can type something like *"took with food"* or *"felt dizzy after"*. This note is saved with that specific dose and can be looked at later — great for telling your doctor exactly what happened, and when.

---

## 5. Medicines — your full list

The **Medicines** tab shows every medicine you've saved, newest first. Tap any card to open its detail page.

### 5.1 What you can do from a medicine's page

| Section | What it does | Example |
|---|---|---|
| **Status** | Pause, resume, discontinue, or archive a medicine | You're going on a 2-week trip where the med isn't needed → tap **Pause** so the reminders stop, but the medicine isn't deleted |
| **Schedule** | See/edit the times you take it | Doctor changes your dose from twice a day to once → tap to edit the times |
| **Stock** | Track how many pills you have left | You open a new pack of 30 → tap **Update stock** |
| **Expiry** | Note when the medicine expires | Box says "expires 2027-03" → add that date |
| **Adherence** | Shows your "took it" percentage | Over the last 30 days you took 27 of 30 doses → 90% |
| **History** | The list of every Taken / Skipped / Missed entry | Scroll back to see last Tuesday's entry |
| **Symptoms** | Notes you've added about how you feel | "Headache 2 hours after dose" |

### 5.2 Correcting a mistake

If you tap **Taken** by accident (or marked the wrong time), don't panic — **you never need to delete anything.**

1. Open the medicine → scroll to **History**.
2. Find the wrong entry → tap **Correct**.
3. Change Taken to Skipped (or fix the time), and add a short reason like *"tapped by accident"*.
4. The original entry stays in the list but is now marked **Corrected** — so the record stays honest, and your doctor sees the truth.

### 5.3 Archiving (not deleting)

When a medicine is finished — the course ended, or the doctor stopped it — tap **Archive**. This removes it from your active list and cancels its reminders. **You can undo this** within a few seconds (an "Undo" button pops up), and the medicine's history is never destroyed — that history is valuable at your next doctor visit.

---

## 6. Adding a medicine (the "take a photo" flow)

This is the flagship feature, and it's designed to take you **from photo to saved medicine in under a minute**. There are two ways in:

- **Medicines tab** → tap the **+ Add** button, or
- **Today tab** → the empty screen's **Add your first medicine** button.

The flow has three steps: **Capture → Review → Schedule**.

### 6.1 Step 1 — Capture (take the photo)

- You'll see a big **camera card**. Tap it and take a clear, well-lit photo of the **medicine label** (the box or strip) — or of a **prescription** if you have one.
- You can also pick a photo already on your phone, or tap **Add manually instead** and type everything yourself (skip to [Adding manually](#64-adding-manually-no-photo)).
- While the app reads the label you'll see *"Identifying medicine…"* — this is the AI part doing its thing. It usually takes a few seconds.

> **Tip for a good photo:** flat, well-lit, no fingers, and the medicine name fully visible. Like taking a photo of a receipt you want to read later.

### 6.2 Step 2 — Review (check what the app read)

The app fills in a form with what it found on the label. **This is where you check its work — the app never saves anything without you looking first.**

- Fields the AI is confident about look normal.
- Fields it's *not* sure about get a **yellow/amber border** with a "Please verify" note. Check these carefully — usually the dosage or a similar-sounding medicine name.
- Under the name field, if the app suggests a match from a drug database (like "Acetaminophen" for "Tylenol"), that's shown as a dropdown — tap the right match if it appears.
- Edit anything that's wrong. The only field most people need to touch is the **dosage** — for tablets it's usually a simple **Half / Full** choice.

**Example — what you might see:**

| Field | What the app guessed | Your job |
|---|---|---|
| Name | Lisinopril | ✅ correct, leave it |
| Dosage | Full | Tap **Half** if you take half a tablet |
| Purpose | Treats high blood pressure | ✅ fine |
| Instruction | Take once daily | ✅ fine |
| ⚠ "Is this the correct name?" | (amber prompt) | Verify — the label may have been blurry |

When you're happy, tap **Next → Schedule**.

### 6.3 Step 3 — Schedule (when to take it)

- Choose **Morning / Afternoon / Night** quick chips, and/or tap **Add exact time** for a precise clock time (like 20:00).
- Below the times, you'll see a preview line: *"You'll take this at 08:00 and 20:00 daily · next dose tomorrow 08:00."* This is your confirmation of what will happen.
- **More options** hides advanced schedule types:
  - **Fixed times** — same time every day (the default; most medicines).
  - **As needed (PRN)** — no schedule at all; log doses from Today when you take them (like a painkiller).
  - *Every 8 hours, On/off cycle, and Taper* are coming soon (shown as greyed-out).
- Choose **Before eating** or **After eating** if it matters for this medicine.
- Tap **Save medicine**. A confirmation box appears — tap **Save** and you land back on the Medicines list with your new card. Done! The reminders are now scheduled on your phone.

> **Note on "Body clock vs Local clock":** only visible if you pick an interval-type schedule, and only relevant if you travel across time zones. If you're not a frequent flier, ignore it.

### 6.4 Adding manually (no photo)

Tap **Add manually instead** on the Capture step, then just fill in the form yourself — name, dosage, and times. Same Review and Schedule steps, no photo needed. Use this for medicines the camera can't read (unlabeled pills, liquid doses you measure yourself, etc.).

### 6.5 What if the AI can't read the label?

If the photo is too blurry or the AI service is unavailable, you'll see an amber message like *"Couldn't identify — filled from on-device scan, please double-check"* with a **Retry** button. Two options:

1. Tap **Retry** with a better photo, or
2. Check the filled-in fields yourself (they're the result of a basic on-phone text reader, so be extra careful with the name and dose) and continue.

There's never a dead end — you can always continue with manual entry.

---

## 7. Reminders (notifications)

The **Reminders** tab controls when and how the app pings you.

### 7.1 What you'll see

- **Quiet hours** card — a time window (default 22:00–07:00) during which the app stays silent, plus a **Discreet** toggle.
- **Active** — every medicine/time combo that currently has a notification. Each row has the time, medicine name, an edit button, and a pause switch.
- **Paused** — the ones you switched off, kept so you can bring them back instantly.

### 7.2 Example — pausing a reminder

Your doctor says, "Take the Vitamin D only on weekdays." On the Reminders tab, flip the switch next to *Vitamin D · 09:00* to pause it. It moves to the **Paused** list. Next week, flip it back. Nothing is deleted, nothing needs re-creating.

### 7.3 Discreet mode (privacy)

Flip **Discreet** on and your lock screen will show only the word **"Reminder"** instead of the medicine name. This exists for people taking medicines they'd rather not have strangers see on their phone — mental health, HIV, fertility, and similar medicines. It's one toggle, and it only affects what shows on the locked screen.

### 7.4 What a reminder looks like

When it's time, you get a notification like:

> **Medac** — *"Time to take Lisinopril (10 mg)"*
> [Taken] [Snooze 10m]

- Tap **Taken** from the notification itself — no need to open the app (you'll still be able to verify it in Today).
- Tap **Snooze 10m** if you're mid-meeting and want it back in 10 minutes.

---

## 8. Profile — account, reports, and helpers

The **Profile** tab is a menu of deeper features:

### 8.1 Account & security

- Change your password, see the devices you're signed in on, and log out (including "log out on all devices" if you suspect someone else has access).
- Two-factor authentication (MFA): you can add it in Security — it asks for a code from an authenticator app (like Google Authenticator) at login. When you set it up, the app shows you **10 recovery codes — save them somewhere safe** (a note on paper is fine). They're the only way back in if you lose your phone.

### 8.2 Doctor report (bring this to your visit)

1. Open **Profile → Doctor report**.
2. Pick a date range (say, the last 30 days).
3. The app shows an **adherence summary**: "You took 90% of your scheduled doses (27 of 30)." As-needed medicines are not counted, and the app says so, because there's nothing to be "on time" about with those.
4. Tap **Export CSV** to create a file you can share — email it to your doctor, or open it in a spreadsheet. The download link stays valid for 1 hour.

**Why this matters:** doctors love this. Instead of "I think I took them most days", you walk in with the actual record.

### 8.3 Assistant (the AI chat helper)

In **Profile → Assistant** you can ask questions in plain language, like:

- *"Remind me when my Metformin runs out."* (it looks at your stock and schedule)
- *"Plan my dose times around my 8am–6pm work day."* (use the **Draft** tab, tell it your wake and sleep times)
- *"How has my adherence been this month?"* (use the **Summary** tab)

**Important rules of the Assistant:**

- Its replies can include **proposals** — cards that say *"I suggest changing X. Confirm or Reject?"* Nothing is ever applied until **you** tap **Confirm**. It can't change your medicines by itself.
- Conversations are **temporary** — they disappear after 72 hours.
- **It is not a doctor.** It can summarize your data and explain things, but it will never give you personal medical advice. When something looks serious, it will tell you to talk to your pharmacist or doctor — believe it.

### 8.4 People & sharing (caregivers)

See [Caring for someone else](#91-caring-for-someone-else-multi-patient-mode) below.

### 8.5 About / privacy

Shows what data lives on your phone vs. what syncs to the server, and a request ID you can quote in bug reports (it helps the support team find your issue).

---

## 9. Special situations

### 9.1 Caring for someone else (multi-patient mode)

You can manage medicines for more than one person from one account — for example, your dad and yourself.

1. **Profile → People & sharing** → **Invite** someone by email (or set up a second patient profile).
2. Once you have more than one person, a small **person switcher chip** appears at the top of Today and Medicines — tap it to switch between people. Each person has their own medicines, schedule, and reminders.
3. Everyone gets a **role**:
   - **Owner / Manager** — can do everything, including inviting others.
   - **Contributor** — can log doses and add notes, but can't edit schedules or medicines.
   - **Viewer** — can see everything but change nothing.

**Example:** Your dad takes 4 medicines. You're the owner of his profile. His nurse is a contributor (she logs the doses she gives him). His sister is a viewer (she checks from afar that doses are being taken). Everyone sees the same live data; only you can change the plan.

### 9.2 Hospital stay (pause everything)

If your dad goes into hospital and the staff will administer his meds, open each medicine (or the reminders) and **Pause** them. Nothing is deleted — when he comes home, tap **Resume** and everything comes back exactly as it was.

### 9.3 Traveling across time zones

For most medicines, the app uses your **local clock** — 08:00 stays 08:00 wherever you are. For interval medicines (like "every 12 hours"), you may see a **Body clock** option: it keeps the *gap between doses* the same even as your wall clock jumps around. Choose **Body clock** for time-critical meds like birth control or immunosuppressants. If you're not sure, leave it on Local clock and ask your pharmacist about the travel plan.

---

## 10. Alerts and warnings (when the app raises its voice)

The app never deletes, changes, or "fixes" anything on its own. It does tell you when something needs your attention, and it uses **colors** so you can read the urgency at a glance:

| Color | Meaning | What you do |
|---|---|---|
| Blue/gray chip | Just information | Nothing — it's FYI |
| **Amber** banner | Needs attention | Read it, tap **Done** |
| **Red** banner | Needs attention *now* | Read it, act on it (e.g., refill the medicine), tap **Done** |
| Red pop-up dialog | Possible emergency | This blocks everything until you acknowledge it |

**Example — low stock:** You saved Metformin with 30 tablets and take 2 per day. The app does the math (15 days left) and, a week before you run out, shows an amber banner: *"Low stock: Metformin — 7 days left."* You tap it, go to the medicine's Stock section, tap **Update stock** after refilling, and the banner clears.

**Example — expiry:** You added an expiry date of next March. A month before, you get a notice: *"Metformin expires in 30 days."* Time to check the pharmacy.

> **Where do these come from?** The app combines real drug database lookups with plain calculations — not guesswork. It tells you where each fact came from (like "Resolved via RxNorm"), so you always know which parts are verified facts and which parts are the AI's reading of your label.

---

## 11. Frequently asked questions

**Q: Do I need internet for the reminders to work?**
No. Reminders are scheduled on your phone itself and fire even in airplane mode. When you're offline, your "Taken" taps are saved on the phone and sent to the server later — you'll see a small note like "3 updates waiting" at the top of Today. Nothing is lost.

**Q: I accidentally archived a medicine. Can I get it back?**
Yes — tap **Undo** in the snackbar that appears right after archiving. If you missed that window, the medicine still exists (it's just hidden); ask whoever manages the account to un-archive it.

**Q: I marked the wrong dose as Taken. Is that history now "fake"?**
No — and that's by design. You use **Correct** (see [5.2](#52-correcting-a-mistake)) and the entry stays visible but marked **Corrected**. The history stays honest for your doctor.

**Q: The AI read the wrong medicine name. What now?**
On the Review step, the name field lets you pick the right name from the suggestions — type and choose the correct one. If it's not in the list, type it yourself. Remember: you are always the last check; the AI only fills the form.

**Q: Can this tell me if two medicines interact?**
The app flags likely interaction and duplicate-ingredient concerns when medicines are added, based on real drug databases, and shows you the source. But it is a tool, not a pharmacist — for anything serious, it will tell you to check with your pharmacist or doctor, and so do we.

**Q: Who can see my data?**
Your data is tied to your account. Others can only see it if *you* invite them (People & sharing), and then only at the permission level you chose. Photos of labels stay on your phone in a private folder — only a compressed copy is sent to the server when you tap to identify.

---

## 12. Quick reference — every screen and how to reach it

| I want to… | Go to | Tap |
|---|---|---|
| See today's doses | **Today** tab | (it's the home screen) |
| Mark a dose taken | **Today** tab | medicine card → **Taken** |
| Add a medicine by photo | **Medicines** tab | **+ Add** → camera card |
| Add a medicine by typing | **Medicines** tab | **+ Add** → **Add manually instead** |
| Edit dose times | **Medicines** tab | medicine card → **Schedule** |
| Pause/resume notifications | **Reminders** tab | the switch on the row |
| Make notifications private | **Reminders** tab | **Discreet** toggle |
| Track pills left | **Medicines** tab | medicine card → **Stock** |
| Add expiry date | **Medicines** tab | medicine card → **Expiry** |
| Fix a mistaken log | **Medicines** tab | medicine card → **History** → **Correct** |
| Log how I feel | **Medicines** tab | medicine card → **Symptoms** → **+ Add symptom** |
| Get a doctor report | **Profile** | **Doctor report** |
| Export data as CSV | **Profile** | **Doctor report** → **Export CSV** |
| Ask the AI a question | **Profile** | **Assistant** |
| Share with a caregiver | **Profile** | **People & sharing** |
| Change password / log out | **Profile** | **Account** |
| Set up two-factor auth | **Profile** | **Security** |
| See what data is stored | **Profile** | **About / privacy** |

---

## 13. The golden rules (read once, remember forever)

1. **The app reminds; you decide.** Every AI action, every schedule change — you confirm it before it's saved.
2. **One tap a day keeps the data honest.** Tap **Taken** when you take it, **Skip** when you don't.
3. **Never delete, always correct.** Medicine history is for your doctor — use **Correct**, not delete.
4. **Colors mean urgency.** Blue/amber = look. Red = act now.
5. **Offline is fine.** Your reminders and your logged doses survive without internet.
6. **When in doubt, ask a human.** The app will literally tell you to — listen to it.
