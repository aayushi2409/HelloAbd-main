package com.example.hello_abd.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderText = intent.getStringExtra("REMINDER_TEXT") ?: "Time for your reminder!"
        val serviceIntent = Intent(context, ReminderAlarmService::class.java).apply {
            putExtra("REMINDER_TEXT", reminderText)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
