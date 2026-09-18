package com.example.medac

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver responsible for re-scheduling all active medicine alarms
 * whenever the device restarts, the app is updated, or system time/timezone changes.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        android.util.Log.d("BootReceiver", "Received broadcast: $action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                rescheduleAllActiveRemindersFromStorage(context)
            }
        }
    }
}
