package com.example.medac

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.example.medac.ui.DueMedicineAlarmFullScreen
import com.example.medac.ui.theme.MedacTheme

/**
 * Full-screen alarm interaction UI.
 *
 * Audio/vibration ownership lives solely in [AlarmService] (plus
 * [com.example.medac.ui.InAppAlarmPlayer] when the MainActivity due-alarm
 * overlay is visible). This activity never creates its own MediaPlayer so
 * rotation / screen-off recreation cannot cut the ringing mid-way, and
 * there is no competing audio-focus request.
 */
class AlarmActivity : ComponentActivity() {

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isFinishing) finish()
        }
    }

    // Hoisted so a second firing alarm can replace the displayed dose via
    // onNewIntent instead of being silently dropped (singleTask).
    private val currentDose = androidx.compose.runtime.mutableStateOf(ActiveAlarm("", "", 0L))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupLockscreenFlags()

        currentDose.value = doseFromIntent(intent)

        // Ensure the single audio owner is ringing (no-op if already started
        // by ReminderReceiver). Best-effort: may be denied in background on
        // Android 14 — the full-screen notification remains.
        try {
            AlarmService.start(this, currentDose.value.medicineName, currentDose.value.time)
        } catch (_: Exception) {}

        // Register dismiss broadcast from notification actions
        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            IntentFilter(ReminderReceiver.ACTION_DISMISS_ALARM_ACTIVITY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Back behaves exactly like "Go to Home (Skip)" — a bare finish()
        // would close the UI while the alarm kept ringing.
        onBackPressedDispatcher.addCallback(this) { performGoHome() }

        setContent {
            MedacTheme {
                val dose = currentDose.value
                DueMedicineAlarmFullScreen(
                    medicineName = dose.medicineName,
                    time = dose.time,
                    instructions = "",
                    onMarkTaken = {
                        AlarmService.stop(this)
                        logDoseDirectly(this, dose.medicineName, dose.time, "TAKEN")
                        ActiveAlarmStore.removeActiveAlarm(this, dose.medicineName, dose.time)
                        cancelNotifications(dose.medicineName, dose.time)
                        Toast.makeText(this, "${dose.medicineName} marked as taken", Toast.LENGTH_SHORT).show()
                        if (!advanceToNextAlarm()) finish()
                    },
                    onSnooze = {
                        AlarmService.stop(this)
                        ActiveAlarmStore.removeActiveAlarm(this, dose.medicineName, dose.time)
                        scheduleSnoozeReminder(this, dose.medicineName, dose.time, DEFAULT_SNOOZE_DELAY_MINUTES)
                        cancelNotifications(dose.medicineName, dose.time)
                        Toast.makeText(this, "Snoozed for $DEFAULT_SNOOZE_DELAY_MINUTES minutes", Toast.LENGTH_SHORT).show()
                        if (!advanceToNextAlarm()) finish()
                    },
                    onGoToHome = { performGoHome() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val dose = doseFromIntent(intent)
        if (dose.medicineName.isNotBlank()) {
            currentDose.value = dose
            try {
                AlarmService.start(this, dose.medicineName, dose.time)
            } catch (_: Exception) {}
        }
    }

    private fun doseFromIntent(intent: Intent?): ActiveAlarm {
        val medicineName = intent?.getStringExtra("medicine_name") ?: "Scheduled Medication"
        val time = intent?.getStringExtra("time") ?: ""
        return ActiveAlarm(medicineName, time, System.currentTimeMillis())
    }

    /**
     * After handling the displayed dose, show the next still-firing alarm (if
     * any) instead of closing. Returns true when another alarm took over.
     */
    private fun advanceToNextAlarm(): Boolean {
        val next = ActiveAlarmStore.getActiveAlarm(this) ?: return false
        if (next.medicineName == currentDose.value.medicineName && next.time == currentDose.value.time) return false
        currentDose.value = next
        try {
            AlarmService.start(this, next.medicineName, next.time)
        } catch (_: Exception) {}
        return true
    }

    private fun performGoHome() {
        val dose = currentDose.value
        AlarmService.stop(this)
        ActiveAlarmStore.removeActiveAlarm(this, dose.medicineName, dose.time)
        cancelNotifications(dose.medicineName, dose.time)
        val homeIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(homeIntent)
        finish()
    }

    private fun cancelNotifications(medicineName: String, time: String) {
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.cancel(alarmNotificationId(medicineName, time))
        notifManager.cancel(preNotificationId(medicineName, time))
        notifManager.cancel(AlarmService.NOTIFICATION_ID)
    }

    private fun setupLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_POWER) {
            AlarmService.stop(this)
            Toast.makeText(this, "Alarm sound silenced", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(dismissReceiver)
        } catch (_: Exception) {}
        // Deliberately NOT stopping AlarmService here: onDestroy fires on
        // rotation / screen-off recreation, and stopping would cut ringing.
        // Service stops on explicit Taken/Home/silence actions or its own
        // 5-minute auto-silence timer.
        super.onDestroy()
    }
}
