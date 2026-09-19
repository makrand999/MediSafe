# Medac App State

Last updated: 2026-04-20

## What the app is

`medac` is an Android app built with Kotlin + Jetpack Compose. Its main purpose is:

- capture medicine photos with the camera
- capture medicine photos with the camera
- run OCR via Google Lens Protobuf API (`v1/crupload`) on label/prescription photos
- parse OCR results into saved medicines and reminder schedules (local heuristic)
- schedule and deliver daily dose reminders

## High-level architecture

- UI + most logic live in [`app/src/main/java/com/example/medac/MainActivity.kt`](/home/max/AndroidStudioProjects/medac/app/src/main/java/com/example/medac/MainActivity.kt)
- ViewModel is [`MedacViewModel.kt`](/home/max/AndroidStudioProjects/medac/app/src/main/java/com/example/medac/MedacViewModel.kt)
- Google Lens OCR client is in [`app/src/main/java/com/example/medac/ocr/GoogleLensOcrClient.kt`](/home/max/AndroidStudioProjects/medac/app/src/main/java/com/example/medac/ocr/GoogleLensOcrClient.kt)
- Lens Protobuf Codec is in [`app/src/main/java/com/example/medac/ocr/LensProtobufCodec.kt`](/home/max/AndroidStudioProjects/medac/app/src/main/java/com/example/medac/ocr/LensProtobufCodec.kt)
- UI screens are in [`MedacScreens.kt`](/home/max/AndroidStudioProjects/medac/app/src/main/java/com/example/medac/MedacScreens.kt)
- UI models/helpers are in [`MedacUiModels.kt`](/home/max/AndroidStudioProjects/medac/app/src/main/java/com/example/medac/MedacUiModels.kt)
- reminder scheduling is in [`ReminderScheduler.kt`](/home/max/AndroidStudioProjects/medac/app/src/main/java/com/example/medac/ReminderScheduler.kt)
- reminder notification delivery is in [`ReminderReceiver.kt`](/home/max/AndroidStudioProjects/medac/app/src/main/java/com/example/medac/ReminderReceiver.kt)
- app/data models are in [`Models.kt`](/home/max/AndroidStudioProjects/medac/app/src/main/java/com/example/medac/Models.kt)

## Current user flow

1. User opens the app.
2. User captures a medicine label photo for OCR analysis, or a prescription photo to bulk-import medicines.
3. Google Lens OCR extracts text blocks, lines, and words. Text is parsed locally into a draft medicine (name, dose, instructions, suggested times).
4. User confirms/edits details and saves the medicine; reminder times are scheduled as exact alarms.

## Current state of the main screens

- Today: dose schedule grouped by time of day with next-dose header.
- Medicines: saved medicine cards, prescription-scan shortcut, add-medicine entry.
- Reminders: active/paused reminder toggles with per-time editing.
- Profile: summary/privacy info only (no external service auth).
- Add Medicine: single OCR scan path (Scan label) → review draft → optional card image from scan photo → save.

## OCR and medicine ingestion

- camera capture uses `ActivityResultContracts.TakePicture()` and `GetContent` for gallery
- photos are stored under app internal storage `filesDir/medicine_photos`
- `GoogleLensOcrClient` sends image to Google Lens endpoint (`https://lensfrontend-pa.googleapis.com/v1/crupload`) using Protobuf wire format
- `MedacViewModel.buildDraftSuggestion()` and `buildPrescriptionMedicines()` parse the OCR text into medicine drafts

## Local persistence

### Saved medicines

- shared preferences file: `medicines`
- key `items` stores JSON array of `ManagedMedicine`

### Reminder pause state

- shared preferences file: `reminder_state`
- key `paused_keys` stores paused `medicineName|time` keys

## Reminder system

Scheduling:

- `scheduleMedicineReminder()` uses `AlarmManager`
- exact alarms when available, otherwise falls back to `setAndAllowWhileIdle()`
- each reminder is keyed by hash of `medicineName|time`

Delivery:

- `ReminderReceiver` posts a high-priority notification
- after firing, it reschedules the same reminder for the next day

Current limitations:

- reminders are daily only
- no snooze / taken confirmation

## Permissions and platform assumptions

Declared in [`AndroidManifest.xml`](/home/max/AndroidStudioProjects/medac/app/src/main/AndroidManifest.xml):

- `INTERNET`
- `CAMERA`
- `SCHEDULE_EXACT_ALARM`
- `POST_NOTIFICATIONS`
- `WAKE_LOCK`

## Build/config state

From [`app/build.gradle.kts`](/home/max/AndroidStudioProjects/medac/app/build.gradle.kts):

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 24`
- Java 11
- Compose enabled

Main libraries in use:

- Jetpack Compose Material 3
- Lifecycle ViewModel
- CameraX
- Google Lens Protobuf OCR (OkHttp)
- Coil
- Retrofit + Gson + OkHttp (Retrofit currently unused; see below)

## Known leftover / next-step notes

- Legacy `ApiService.kt` was removed (2026-09); Retrofit/OkHttp remain in use via `data/MedacApi.kt` + `NetworkModule.kt`.
- Email verification is disabled product-wide: register auto-logs-in locally and there is no verify flow.
- The prescription bulk-import ViewModel flow (`analyzePrescriptionPhoto` / `savePrescriptionImport`) has no UI entry point yet.
- `tools/` no longer contains ChatGPT-specific harnesses.
