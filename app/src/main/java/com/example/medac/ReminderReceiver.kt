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
        const val ACTION_DISMISS_ALARM_ACTIVITY = "com.example.medac.ACTION_DISMISS_ALARM_ACTIVITY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicineName = intent.getStringExtra("medicine_name") ?: "Medicine"
        val time = intent.getStringExtra("time") ?: ""
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = ("$medicineName|$time|alarm_notif").hashCode()
        val preNotifId = ("$medicineName|$time|pre_notif").hashCode()

        when (intent.action) {
            ACTION_TAKE_DOSE -> {
                logDoseDirectly(context, medicineName, time, "TAKEN")
                ActiveAlarmStore.clearActiveAlarm(context)
                AlarmService.stop(context)
                notificationManager.cancel(notifId)
                notificationManager.cancel(preNotifId)
                context.sendBroadcast(Intent(ACTION_DISMISS_ALARM_ACTIVITY).setPackage(context.packageName))
                return
            }
            ACTION_DELAY_DOSE -> {
                ActiveAlarmStore.clearActiveAlarm(context)
                AlarmService.stop(context)
                scheduleSnoozeReminder(context, medicineName, time, DEFAULT_SNOOZE_DELAY_MINUTES)
                notificationManager.cancel(notifId)
                notificationManager.cancel(preNotifId)
                context.sendBroadcast(Intent(ACTION_DISMISS_ALARM_ACTIVITY).setPackage(context.packageName))
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

            // 2. Persist active alarm so app shows full-screen UI when opened
            ActiveAlarmStore.setActiveAlarm(context, medicineName, time)

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

            // Direct action to go to home / skip
            val homeIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val homePendingIntent = PendingIntent.getActivity(
                context,
                ("$medicineName|$time|home").hashCode(),
                homeIntent,
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
                .addAction(android.R.drawable.ic_menu_today, "Home / Skip", homePendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()

            notificationManager.notify(notifId, alarmNotification)

            // 6. Best-effort Foreground Service / Activity start (catches modern Android restrictions safely)
            try {
                AlarmService.start(context, medicineName, time)
            } catch (e: Exception) {
                android.util.Log.d("ReminderReceiver", "AlarmService.start not permitted in background: ${e.message}")
            }

            try {
                context.startActivity(alarmActivityIntent)
            } catch (e: Exception) {
                android.util.Log.d("ReminderReceiver", "Background startActivity not permitted: ${e.message}")
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
                0,
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
