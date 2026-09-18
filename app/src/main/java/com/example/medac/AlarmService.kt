package com.example.medac

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmService : Service() {

    companion object {
        const val ACTION_START_ALARM = "com.example.medac.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.medac.ACTION_STOP_ALARM"
        const val NOTIFICATION_ID = 999123

        fun start(context: Context, medicineName: String, time: String) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START_ALARM
                putExtra("medicine_name", medicineName)
                putExtra("time", time)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startService(intent)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volumeRampRunnable: Runnable? = null
    private var autoSilenceRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_ALARM) {
            stopAlarm()
            stopSelf()
            return START_NOT_STICKY
        }

        val medicineName = intent?.getStringExtra("medicine_name") ?: "Medicine"
        val time = intent?.getStringExtra("time") ?: ""

        startForegroundAlarm(medicineName, time)
        return START_NOT_STICKY
    }

    private fun startForegroundAlarm(medicineName: String, time: String) {
        val channelId = "med_alarm_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Medicine Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alarm ringing for scheduled medication"
                enableVibration(true)
                setBypassDnd(true)
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }

        val alarmActivityIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("medicine_name", medicineName)
            putExtra("time", time)
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            ("$medicineName|$time|fullscreen").hashCode(),
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val takeIntent = Intent(this, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TAKE_DOSE
            putExtra("medicine_name", medicineName)
            putExtra("time", time)
        }
        val takePendingIntent = PendingIntent.getBroadcast(
            this,
            ("$medicineName|$time|take").hashCode(),
            takeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val delayIntent = Intent(this, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DELAY_DOSE
            putExtra("medicine_name", medicineName)
            putExtra("time", time)
        }
        val delayPendingIntent = PendingIntent.getBroadcast(
            this,
            ("$medicineName|$time|delay").hashCode(),
            delayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Medicine Alarm: $medicineName")
            .setContentText("Time to take your medication ($time)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.checkbox_on_background, "✓ Taken", takePendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "⏱ Delay 3m", delayPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } catch (e: SecurityException) {
                // Android 14+: FGS start denied in background (no exemption).
                // The high-priority full-screen notification posted by
                // ReminderReceiver remains the delivery path — stop quietly.
                android.util.Log.w("AlarmService", "startForeground denied: ${e.message}")
                stopSelf()
                return
            }
        } else {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                android.util.Log.w("AlarmService", "startForeground denied: ${e.message}")
                stopSelf()
                return
            }
        }

        startRingingAndVibration()
        setupAutoSilenceTimer(medicineName, time)
    }

    private fun startRingingAndVibration() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        requestAudioFocus()

        try {
            var alertUri: Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri!!)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setVolume(0.3f, 0.3f)
                prepare()
                start()
            }

            var currentVol = 0.3f
            volumeRampRunnable = object : Runnable {
                override fun run() {
                    if (mediaPlayer != null && currentVol < 1.0f) {
                        currentVol = (currentVol + 0.1f).coerceAtMost(1.0f)
                        try {
                            mediaPlayer?.setVolume(currentVol, currentVol)
                        } catch (_: Exception) {}
                        if (currentVol < 1.0f) {
                            handler.postDelayed(this, 1000L)
                        }
                    }
                }
            }
            handler.postDelayed(volumeRampRunnable!!, 1000L)

        } catch (e: Exception) {
            android.util.Log.w("AlarmService", "Failed to start MediaPlayer for alarm: ${e.message}")
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 1000, 600, 1000, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            android.util.Log.w("AlarmService", "Failed to start vibrator: ${e.message}")
        }
    }

    private fun setupAutoSilenceTimer(medicineName: String, time: String) {
        autoSilenceRunnable = Runnable {
            stopAlarm()
            scheduleSnoozeReminder(this, medicineName, time, DEFAULT_SNOOZE_DELAY_MINUTES)
            stopSelf()
        }
        handler.postDelayed(autoSilenceRunnable!!, 5 * 60 * 1000L)
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

    private fun stopAlarm() {
        volumeRampRunnable?.let { handler.removeCallbacks(it) }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}
