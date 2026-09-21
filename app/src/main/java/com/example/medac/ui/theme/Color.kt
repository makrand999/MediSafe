package com.example.medac.ui.theme

import androidx.compose.ui.graphics.Color

// MedRemind Brand & Primary Colors (from PDF)
val MedRemindGreen = Color(0xFF1B7A43)
val MedRemindDarkGreen = Color(0xFF145A32)
val MedRemindSplashGreen = Color(0xFF1E824C)
val MedRemindLightGreen = Color(0xFFE8F5E9)
val MedRemindAccentGreen = Color(0xFF22C55E)

// Quick Action Card Colors (Screen 2 & 7)
val QuickActionGreen = Color(0xFF10B981)
val QuickActionBlue = Color(0xFF2563EB)
val QuickActionPurple = Color(0xFF8B5CF6)
val QuickActionOrange = Color(0xFFF97316)

// Button & Badge Colors
val TakeButtonOrange = Color(0xFFEA580C)
val TakeButtonBackground = Color(0xFFF97316)
val TakenBadgeGreen = Color(0xFF16A34A)
val AsteriskGold = Color(0xFFF59E0B)

// Neutral & Background Colors
val AppBackground = Color(0xFFF8FAFC)
val CardSurface = Color(0xFFFFFFFF)
val BorderSubtle = Color(0xFFE2E8F0)
val DividerMuted = Color(0xFFE2E8F0)

// Text Colors
val TextPrimary = Color(0xFF111827)
val TextSecondary = Color(0xFF64748B)
val TextMuted = Color(0xFF94A3B8)

// Status Colors
val StatusGreen = Color(0xFF16A34A)
val StatusGreenContainer = Color(0xFFDCFCE7)
val StatusBlue = Color(0xFF2563EB)
val StatusBlueContainer = Color(0xFFDBEAFE)
val StatusGray = Color(0xFF64748B)
val StatusGrayContainer = Color(0xFFF1F5F9)
val StatusAmber = Color(0xFFD97706)
val StatusAmberContainer = Color(0xFFFEF3C7)
val StatusRed = Color(0xFFDC2626)
val StatusRedContainer = Color(0xFFFEE2E2)
val WarningRed = Color(0xFFDC2626)
val PausedMuted = Color(0xFFF1F5F9)

// ── Time-of-Day (Circadian) accents ──────────────────────────────────────────
// A deliberately restrained family: all four sit in the same lightness band
// (4.9:1–7.8:1 on white) and are desaturated enough to read as ONE system
// rather than a rainbow. Colour is a signal here, not a surface — schedule
// cards stay neutral and only the rail, dot and chapter chip carry the hue.
val PeriodMorningAccent = Color(0xFFA16207)   // ochre
val PeriodAfternoonAccent = Color(0xFF0369A1) // steel blue
val PeriodEveningAccent = Color(0xFF9F3F52)   // muted plum
val PeriodNightAccent = Color(0xFF3F4E8C)     // slate indigo

// ── Neutral schedule surfaces ────────────────────────────────────────────────
val ScheduleCardSurface = Color(0xFFFFFFFF)
val ScheduleCardSurfaceTaken = Color(0xFFF8FAFC)
val ScheduleCardBorder = Color(0xFFEAEEF3)
val ScheduleIconWell = Color(0xFFF6F8FA)
val ScheduleChipText = Color(0xFF0F172A)
val ScheduleChipCount = Color(0xFF475569)

// ── Schedule actions ─────────────────────────────────────────────────────────
// Brand green for "Take" so the period accents stay the only chromatic
// variation on the screen (the old hot orange fought every chapter tint).
val TakenPillContainer = Color(0xFFF0FDF4)
val TakenPillText = Color(0xFF166534)

// Neutral that stays >=4.5:1 on any subtle tint (unlike TextSecondary, which
// only clears AA on pure white / AppBackground).
val TextSecondaryOnTint = Color(0xFF475569)

// Backwards compatibility aliases
val NavyPrimary = MedRemindGreen
val NavyDark = MedRemindDarkGreen
val BlueInfo = Color(0xFFEAF3F9)
val BlueInfoText = MedRemindGreen
