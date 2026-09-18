package com.example.medac

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.example.medac.ui.DueMedicineAlarmFullScreen
import com.example.medac.ui.theme.MedacTheme

class AlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoSilenceRunnable: Runnable? = null

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopAlarmSoundAndVibration()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupLockscreenFlags()

        val medicineName = intent.getStringExtra("medicine_name") ?: "Scheduled Medication"
        val time = intent.getStringExtra("time") ?: ""

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        startAlarmSoundAndVibration()
        setupAutoSilenceTimer(medicineName, time)

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
                        stopAlarmSoundAndVibration()
                        AlarmService.stop(this)
                        logDoseDirectly(this, medicineName, time, "TAKEN")
                        ActiveAlarmStore.clearActiveAlarm(this)
                        cancelNotifications(medicineName, time)
                        Toast.makeText(this, "$medicineName marked as taken", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onGoToHome = {
                        stopAlarmSoundAndVibration()
                        AlarmService.stop(this)
                        ActiveAlarmStore.clearActiveAlarm(this)
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

    private fun setupAutoSilenceTimer(medicineName: String, time: String) {
        // Auto-silence after 5 minutes of unattended ringing
        autoSilenceRunnable = Runnable {
            stopAlarmSoundAndVibration()
            ActiveAlarmStore.clearActiveAlarm(this)
            finish()
        }
        handler.postDelayed(autoSilenceRunnable!!, 5 * 60 * 1000L)
    }

    private fun startAlarmSoundAndVibration() {
        requestAudioFocus()

        try {
            var alertUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri!!)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setVolume(0.85f, 0.85f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.w("AlarmActivity", "Failed to start MediaPlayer for alarm: ${e.message}")
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            android.util.Log.w("AlarmActivity", "Failed to start vibrator: ${e.message}")
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_POWER) {
            stopAlarmSoundAndVibration()
            AlarmService.stop(this)
            Toast.makeText(this, "Alarm sound silenced", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun stopAlarmSoundAndVibration() {
        autoSilenceRunnable?.let { handler.removeCallbacks(it) }

        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}

        abandonAudioFocus()
        AlarmService.stop(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(dismissReceiver)
        } catch (_: Exception) {}
        stopAlarmSoundAndVibration()
    }
}
