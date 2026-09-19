package com.example.medac

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TAKE_DOSE = "com.example.medac.ACTION_TAKE_DOSE"
        const val ACTION_DELAY_DOSE = "com.example.medac.ACTION_DELAY_DOSE"
        const val ACTION_SKIP_DOSE = "com.example.medac.ACTION_SKIP_DOSE"
        const val ACTION_DISMISS_ALARM_ACTIVITY = "com.example.medac.ACTION_DISMISS_ALARM_ACTIVITY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicineName = intent.getStringExtra("medicine_name") ?: "Medicine"
        val time = intent.getStringExtra("time") ?: ""
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = alarmNotificationId(medicineName, time)
        val preNotifId = preNotificationId(medicineName, time)

        when (intent.action) {
            ACTION_TAKE_DOSE -> {
                logDoseDirectly(context, medicineName, time, "TAKEN")
                ActiveAlarmStore.removeActiveAlarm(context, medicineName, time)
                AlarmService.stop(context)
                notificationManager.cancel(notifId)
                notificationManager.cancel(preNotifId)
                context.sendBroadcast(Intent(ACTION_DISMISS_ALARM_ACTIVITY).setPackage(context.packageName))
                return
            }
            ACTION_DELAY_DOSE -> {
                ActiveAlarmStore.removeActiveAlarm(context, medicineName, time)
                AlarmService.stop(context)
                scheduleSnoozeReminder(context, medicineName, time, DEFAULT_SNOOZE_DELAY_MINUTES)
                notificationManager.cancel(notifId)
                notificationManager.cancel(preNotifId)
                context.sendBroadcast(Intent(ACTION_DISMISS_ALARM_ACTIVITY).setPackage(context.packageName))
                return
            }
            ACTION_SKIP_DOSE -> {
                // Same semantics as the full-screen "Go to Home (Skip)": silence
                // and dismiss WITHOUT registering a dose outcome.
                ActiveAlarmStore.removeActiveAlarm(context, medicineName, time)
                AlarmService.stop(context)
                notificationManager.cancel(notifId)
                notificationManager.cancel(preNotifId)
                context.sendBroadcast(Intent(ACTION_DISMISS_ALARM_ACTIVITY).setPackage(context.packageName))
                try {
                    val homeIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(homeIntent)
                } catch (e: Exception) {
                    // Background activity launch may be blocked on API 29+; the
                    // alarm is already silenced and cleared above, so there is
                    // nothing ringing left behind.
                    android.util.Log.d("ReminderReceiver", "Skip: home launch blocked: ${e.message}")
                }
                return
            }
        }

        val isAlarm = intent.getBooleanExtra("is_alarm", true)
        val isSnooze = intent.getBooleanExtra("is_snooze", false)
        val preMinutes = intent.getIntExtra("pre_minutes", PRE_REMINDER_MINUTES)
        val channelId = if (isAlarm) "med_alarm_channel" else "med_reminders"

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                channelId,
                if (isAlarm) "Medicine Alarms" else "Medicine Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = if (isAlarm) "Alarms for scheduled medicine doses" else "Pre-dose reminder notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
                setBypassDnd(true)
                if (isAlarm && alarmSound != null) {
                    setSound(alarmSound, audioAttributes)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        if (isAlarm) {
            // 1. Cancel lingering 2-min pre-alarm notification
            notificationManager.cancel(preNotifId)

            // 2. Persist active alarm (multi-alarm aware) so app shows full-screen UI when opened
            ActiveAlarmStore.addActiveAlarm(context, medicineName, time)

            // 3. Broadcast to running MainActivity for immediate in-app UI and audio alarm
            val dueBroadcast = Intent(ActiveAlarmStore.ACTION_MEDICINE_DUE).apply {
                setPackage(context.packageName)
                putExtra(ActiveAlarmStore.EXTRA_DUE_MEDICINE_NAME, medicineName)
                putExtra(ActiveAlarmStore.EXTRA_DUE_TIME, time)
            }
            context.sendBroadcast(dueBroadcast)

            // 4. Build PendingIntent to open MainActivity with due alarm full-screen simple UI
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(ActiveAlarmStore.EXTRA_SHOW_DUE_ALARM, true)
                putExtra(ActiveAlarmStore.EXTRA_DUE_MEDICINE_NAME, medicineName)
                putExtra(ActiveAlarmStore.EXTRA_DUE_TIME, time)
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // FullScreenIntent for lockscreen popup
            val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra("medicine_name", medicineName)
                putExtra("time", time)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                ("$medicineName|$time|fullscreen").hashCode(),
                alarmActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Direct action to mark taken from notification
            val takeIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_TAKE_DOSE
                putExtra("medicine_name", medicineName)
                putExtra("time", time)
            }
            val takePendingIntent = PendingIntent.getBroadcast(
                context,
                ("$medicineName|$time|take").hashCode(),
                takeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Direct action to go to home / skip: must silence and clear via the
            // receiver first — a bare getActivity would leave the alarm ringing.
            val skipIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_SKIP_DOSE
                putExtra("medicine_name", medicineName)
                putExtra("time", time)
            }
            val homePendingIntent = PendingIntent.getBroadcast(
                context,
                ("$medicineName|$time|home").hashCode(),
                skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Direct action to snooze — same triple as the foreground-service
            // notification and the full-screen UI.
            val delayIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_DELAY_DOSE
                putExtra("medicine_name", medicineName)
                putExtra("time", time)
            }
            val delayPendingIntent = PendingIntent.getBroadcast(
                context,
                ("$medicineName|$time|delay").hashCode(),
                delayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 5. Explicitly post heads-up high-priority alarm notification
            val alarmNotification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Medicine Alarm: $medicineName")
                .setContentText(if (time.isNotBlank()) "Time to take your medication ($time)" else "Time to take your medication")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(alarmSound)
                .setVibrate(longArrayOf(0, 800, 400, 800, 400, 800))
                .setContentIntent(openAppPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(android.R.drawable.checkbox_on_background, "✓ Taken", takePendingIntent)
                .addAction(android.R.drawable.ic_menu_recent_history, "⏱ Delay ${DEFAULT_SNOOZE_DELAY_MINUTES}m", delayPendingIntent)
                .addAction(android.R.drawable.ic_menu_today, "Home / Skip", homePendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()

            notificationManager.notify(notifId, alarmNotification)

            // 6. Best-effort foreground service for lockscreen ringing.
            // The full-screen UI is delivered via setFullScreenIntent() above,
            // which is the Android 10+ compliant path. A direct
            // startActivity() from background is blocked on API 29+ and is
            // deliberately not attempted here.
            try {
                AlarmService.start(context, medicineName, time)
            } catch (e: Exception) {
                android.util.Log.d("ReminderReceiver", "AlarmService.start not permitted in background: ${e.message}")
            }

            // 7. Reschedule next day's reminder if this wasn't a temporary snooze
            if (!isSnooze && time.isNotBlank()) {
                scheduleMedicineReminder(context, medicineName, time)
            }
        } else {
            // 2-minute pre-alarm notification
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                preAlarmOpenRequestCode(medicineName, time),
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = "Upcoming Medicine in $preMinutes minutes"
            val contentText = "Time to take $medicineName at $time (in $preMinutes minutes)"

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(preNotifId, notification)
        }
    }
}
