package com.example.medac

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

const val PRE_REMINDER_MINUTES = 2
const val DEFAULT_SNOOZE_DELAY_MINUTES = 3

/**
 * Helper for Android 12+ exact-alarm permission. [SCHEDULE_EXACT_ALARM] is
 * declared in the manifest; on API 31+ the user must grant it in Settings.
 * Callers fall back to inexact alarms when denied (see below).
 */
object ExactAlarmHelper {
    fun canScheduleExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) { /* Settings unavailable — caller keeps inexact fallback */
            }
        }
    }
}

/**
 * Schedules both the 2-minute pre-alarm notification and the exact full-screen incoming-call alarm.
 */
fun scheduleMedicineReminder(context: Context, medicineName: String, time: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // 1. Schedule full-screen exact dose alarm at scheduled time
    nextDoseTimeMillis(time)?.let { doseTriggerMillis ->
        val alarmIntent = getAlarmPendingIntent(context, medicineName, time, isAlarm = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, doseTriggerMillis, alarmIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, doseTriggerMillis, alarmIntent)
        }
    }

    // 2. Schedule 2-minute pre-alarm notification
    nextPreReminderTimeMillis(time, PRE_REMINDER_MINUTES)?.let { preTriggerMillis ->
        val preIntent = getAlarmPendingIntent(context, medicineName, time, isAlarm = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preTriggerMillis, preIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, preTriggerMillis, preIntent)
        }
    }
}

fun cancelMedicineReminder(context: Context, medicineName: String, time: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(getAlarmPendingIntent(context, medicineName, time, isAlarm = true, isSnooze = false))
    alarmManager.cancel(getAlarmPendingIntent(context, medicineName, time, isAlarm = true, isSnooze = true))
    alarmManager.cancel(getAlarmPendingIntent(context, medicineName, time, isAlarm = false, isSnooze = false))
}

fun scheduleSnoozeReminder(context: Context, medicineName: String, time: String, delayMinutes: Int = DEFAULT_SNOOZE_DELAY_MINUTES) {
    val effectiveDelay = if (delayMinutes <= 0) DEFAULT_SNOOZE_DELAY_MINUTES else delayMinutes
    val triggerAtMillis = System.currentTimeMillis() + effectiveDelay * 60 * 1000L
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pendingIntent = getAlarmPendingIntent(context, medicineName, time, isAlarm = true, isSnooze = true)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    } else {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}

private fun getAlarmPendingIntent(
    context: Context,
    medicineName: String,
    time: String,
    isAlarm: Boolean,
    isSnooze: Boolean = false
): PendingIntent {
    return PendingIntent.getBroadcast(
        context,
        alarmRequestCode(medicineName, time, isAlarm, isSnooze),
        Intent(context, ReminderReceiver::class.java).apply {
            putExtra("medicine_name", medicineName)
            putExtra("time", time)
            putExtra("is_alarm", isAlarm)
            putExtra("is_snooze", isSnooze)
            putExtra("pre_minutes", PRE_REMINDER_MINUTES)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/**
 * Calculates the next exact dose time in millis.
 */
fun nextDoseTimeMillis(time: String): Long? {
    val parts = time.split(":")
    if (parts.size != 2) return null

    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null

    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    if (target.timeInMillis <= now.timeInMillis) {
        target.add(Calendar.DATE, 1)
    }

    return target.timeInMillis
}

/**
 * Calculates the next trigger time in millis, precisely `minutesBefore` (default 2) minutes prior to the dose time.
 */
fun nextPreReminderTimeMillis(time: String, minutesBefore: Int = PRE_REMINDER_MINUTES): Long? {
    val parts = time.split(":")
    if (parts.size != 2) return null

    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null

    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.MINUTE, -minutesBefore)
    }

    if (target.timeInMillis <= now.timeInMillis) {
        target.add(Calendar.DATE, 1)
    }

    return target.timeInMillis
}

private fun alarmRequestCode(medicineName: String, time: String, isAlarm: Boolean, isSnooze: Boolean): Int {
    val type = if (isSnooze) "snooze" else if (isAlarm) "alarm" else "pre"
    return "$medicineName|$time|$type".hashCode()
}

/**
 * Canonical notification IDs / PendingIntent request codes for the alarm path.
 * Every ID is derived from the dose key (`medicineName|time`) plus a purpose
 * suffix so simultaneously-firing doses never overwrite each other's
 * notifications or tap targets.
 */
fun alarmNotificationId(medicineName: String, time: String): Int =
    "$medicineName|$time|alarm_notif".hashCode()

fun preNotificationId(medicineName: String, time: String): Int =
    "$medicineName|$time|pre_notif".hashCode()

fun preAlarmOpenRequestCode(medicineName: String, time: String): Int =
    "$medicineName|$time|pre_open".hashCode()

/**
 * Returns today's date formatted as YYYY-MM-DD safely on all Android API levels (minSdk 24+).
 */
fun currentDateFormatted(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return sdf.format(java.util.Date())
}

/**
 * Recovers and re-schedules all active medicine reminders directly from storage (e.g. after reboot).
 */
fun rescheduleAllActiveRemindersFromStorage(context: Context) {
    try {
        val json = context.getSharedPreferences("medicines", Context.MODE_PRIVATE).getString("items", "[]").orEmpty()
        val pausedKeys = context.getSharedPreferences("reminder_state", Context.MODE_PRIVATE).getStringSet("paused_keys", emptySet()).orEmpty()
        val arr = org.json.JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.optString("name", "")
            val status = obj.optString("status", "active")
            if (status == "active" && name.isNotBlank()) {
                val timesArr = obj.optJSONArray("times")
                if (timesArr != null) {
                    for (j in 0 until timesArr.length()) {
                        val time = timesArr.getString(j)
                        val key = "$name|$time"
                        if (!pausedKeys.contains(key)) {
                            scheduleMedicineReminder(context, name, time)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("ReminderScheduler", "rescheduleAllActiveRemindersFromStorage failed: ${e.message}")
    }
}

/**
 * Records a dose entry directly to local storage from the alarm screen or notification action,
 * using the real ManagedMedicine.id and standard doseLogKey format.
 */
fun logDoseDirectly(context: Context, medicineName: String, time: String, status: String = "TAKEN") {
    try {
        val date = currentDateFormatted()

        // Lookup actual ManagedMedicine.id from stored medicines
        var actualMedicineId: Long? = null
        try {
            val medsJson = context.getSharedPreferences("medicines", Context.MODE_PRIVATE).getString("items", "[]").orEmpty()
            val medsArr = org.json.JSONArray(medsJson)
            for (i in 0 until medsArr.length()) {
                val medObj = medsArr.getJSONObject(i)
                if (medObj.optString("name", "").equals(medicineName, ignoreCase = true)) {
                    val idVal = medObj.optLong("id", 0L)
                    if (idVal != 0L) {
                        actualMedicineId = idVal
                        break
                    }
                }
            }
        } catch (_: Exception) {}

        val medicineId = actualMedicineId ?: medicineName.hashCode().toLong()
        val key = doseLogKey(medicineId, date, time)

        val prefs = context.getSharedPreferences("dose_logs", Context.MODE_PRIVATE)
        val json = prefs.getString("entries", "[]").orEmpty()
        val arr = org.json.JSONArray(json)
        val updated = org.json.JSONArray()

        val newObj = org.json.JSONObject().apply {
            put("id", key)
            put("medicineId", medicineId)
            put("medicineName", medicineName)
            put("time", time)
            put("date", date)
            put("status", status)
            put("doseAmount", "")
            put("reason", "")
            put("note", "")
            put("updatedAt", System.currentTimeMillis())
        }
        updated.put(newObj)
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            if (item.optString("id") != key) {
                updated.put(item)
            }
        }
        prefs.edit().putString("entries", updated.toString()).apply()
        android.util.Log.d("ReminderScheduler", "Dose logged directly: $key -> $status (medId: $medicineId)")
    } catch (e: Exception) {
        android.util.Log.w("ReminderScheduler", "logDoseDirectly failed: ${e.message}")
    }
}
