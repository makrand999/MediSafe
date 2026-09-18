package com.example.medac

import android.content.Context
import android.content.SharedPreferences

data class ActiveAlarm(
    val medicineName: String,
    val time: String,
    val triggeredMillis: Long
)

object ActiveAlarmStore {
    private const val PREFS_NAME = "active_alarm_state"
    private const val KEY_MED_NAME = "med_name"
    private const val KEY_TIME = "time"
    private const val KEY_TRIGGERED_MILLIS = "triggered_millis"
    private const val KEY_IS_ACTIVE = "is_active"
    private const val KEY_ALARM_SET = "alarm_set_v2"

    // Alarm is considered active for up to 60 minutes if not acknowledged
    private const val MAX_ACTIVE_WINDOW_MILLIS = 60 * 60 * 1000L

    const val ACTION_MEDICINE_DUE = "com.example.medac.ACTION_MEDICINE_DUE"
    const val EXTRA_SHOW_DUE_ALARM = "com.example.medac.EXTRA_SHOW_DUE_ALARM"
    const val EXTRA_DUE_MEDICINE_NAME = "com.example.medac.EXTRA_DUE_MEDICINE_NAME"
    const val EXTRA_DUE_TIME = "com.example.medac.EXTRA_DUE_TIME"

    fun setActiveAlarm(context: Context, medicineName: String, time: String, triggeredMillis: Long = System.currentTimeMillis()) {
        addActiveAlarm(context, medicineName, time, triggeredMillis)
    }

    /**
     * Multi-alarm aware: tracks every simultaneously-firing dose instead of a
     * single slot. Legacy single-key fields are kept updated to the most
     * recent alarm for backward-compatible readers.
     */
    fun addActiveAlarm(context: Context, medicineName: String, time: String, triggeredMillis: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = getActiveAlarms(context)
            .filterNot { it.medicineName == medicineName && it.time == time }
            .plus(ActiveAlarm(medicineName, time, triggeredMillis))
        persistSet(prefs, updated)
        prefs.edit()
            .putString(KEY_MED_NAME, medicineName)
            .putString(KEY_TIME, time)
            .putLong(KEY_TRIGGERED_MILLIS, triggeredMillis)
            .putBoolean(KEY_IS_ACTIVE, true)
            .apply()
    }

    fun getActiveAlarms(context: Context): List<ActiveAlarm> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY_ALARM_SET, emptySet()).orEmpty()
        val now = System.currentTimeMillis()
        return raw.mapNotNull { entry ->
            // Format: name <US> time <US> triggeredMillis
            val parts = entry.split("\u001F")
            if (parts.size != 3) return@mapNotNull null
            val triggered = parts[2].toLongOrNull() ?: return@mapNotNull null
            if (now - triggered > MAX_ACTIVE_WINDOW_MILLIS) return@mapNotNull null
            ActiveAlarm(parts[0], parts[1], triggered)
        }.sortedBy { it.triggeredMillis }
    }

    fun getActiveAlarm(context: Context): ActiveAlarm? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean(KEY_IS_ACTIVE, false)
        if (!isActive) return null

        val name = prefs.getString(KEY_MED_NAME, null) ?: return null
        val time = prefs.getString(KEY_TIME, null) ?: return null
        val triggered = prefs.getLong(KEY_TRIGGERED_MILLIS, 0L)

        // Expire alarm if older than max window
        if (System.currentTimeMillis() - triggered > MAX_ACTIVE_WINDOW_MILLIS) {
            clearActiveAlarm(context)
            return null
        }

        return ActiveAlarm(name, time, triggered)
    }

    fun clearActiveAlarm(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_ACTIVE, false)
            .remove(KEY_MED_NAME)
            .remove(KEY_TIME)
            .remove(KEY_TRIGGERED_MILLIS)
            .remove(KEY_ALARM_SET)
            .apply()
    }

    /** Dismiss a single dose without clearing simultaneously-firing alarms. */
    fun removeActiveAlarm(context: Context, medicineName: String, time: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val remaining = getActiveAlarms(context)
            .filterNot { it.medicineName == medicineName && it.time == time }
        if (remaining.isEmpty()) {
            clearActiveAlarm(context)
        } else {
            persistSet(prefs, remaining)
            val latest = remaining.maxByOrNull { it.triggeredMillis }!!
            prefs.edit()
                .putString(KEY_MED_NAME, latest.medicineName)
                .putString(KEY_TIME, latest.time)
                .putLong(KEY_TRIGGERED_MILLIS, latest.triggeredMillis)
                .putBoolean(KEY_IS_ACTIVE, true)
                .apply()
        }
    }

    private fun persistSet(prefs: SharedPreferences, alarms: List<ActiveAlarm>) {
        val encoded = alarms.map { "${it.medicineName}\u001F${it.time}\u001F${it.triggeredMillis}" }.toSet()
        prefs.edit().putStringSet(KEY_ALARM_SET, encoded).apply()
    }
}
