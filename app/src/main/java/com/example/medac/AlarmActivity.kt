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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupLockscreenFlags()

        val medicineName = intent.getStringExtra("medicine_name") ?: "Scheduled Medication"
        val time = intent.getStringExtra("time") ?: ""

        // Ensure the single audio owner is ringing (no-op if already started
        // by ReminderReceiver). Best-effort: may be denied in background on
        // Android 14 — the full-screen notification remains.
        try {
            AlarmService.start(this, medicineName, time)
        } catch (_: Exception) {}

        // Register dismiss broadcast from notification actions
        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            IntentFilter(ReminderReceiver.ACTION_DISMISS_ALARM_ACTIVITY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            MedacTheme {
                DueMedicineAlarmFullScreen(
                    medicineName = medicineName,
                    time = time,
                    instructions = "",
                    onMarkTaken = {
                        AlarmService.stop(this)
                        logDoseDirectly(this, medicineName, time, "TAKEN")
                        ActiveAlarmStore.removeActiveAlarm(this, medicineName, time)
                        cancelNotifications(medicineName, time)
                        Toast.makeText(this, "$medicineName marked as taken", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onGoToHome = {
                        AlarmService.stop(this)
                        ActiveAlarmStore.removeActiveAlarm(this, medicineName, time)
                        cancelNotifications(medicineName, time)
                        val homeIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(homeIntent)
                        finish()
                    }
                )
            }
        }
    }

    private fun cancelNotifications(medicineName: String, time: String) {
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.cancel(("$medicineName|$time|alarm_notif").hashCode())
        notifManager.cancel(("$medicineName|$time|pre_notif").hashCode())
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
