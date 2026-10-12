package com.example.hello_abd.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.example.hello_abd.R
import com.example.hello_abd.ui.theme.screens.QuizActivity

class ReminderAlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var vibrator: Vibrator
    private lateinit var audioManager: AudioManager
    private lateinit var volumeHandler: Handler
    private lateinit var volumeRunnable: Runnable

    private val notificationId = 123
    private val channelId = "REMINDER_ALARM_CHANNEL"

    companion object {
        const val ACTION_STOP_SERVICE = "com.example.hello_abd.services.STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeHandler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }

        val reminderText = intent?.getStringExtra("REMINDER_TEXT") ?: "Time for your reminder!"
        startForeground(notificationId, createNotification(reminderText))

        forceVolumeToMax()

        // Play alarm sound using ALARM stream attributes for robust playback
        try {
            val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                // Use a wake lock to keep the CPU running during playback
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(attributes)
                setDataSource(applicationContext, alarmSoundUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // If sound fails, at least the vibration and notification will work
        }

        vibrator.vibrate(longArrayOf(0, 1000, 500, 1000, 500), 0)
        return START_STICKY
    }
    
    private fun forceVolumeToMax() {
        volumeRunnable = Runnable {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            volumeHandler.postDelayed(volumeRunnable, 100) // Check every half a second
        }
        volumeHandler.post(volumeRunnable)
    }

    private fun createNotification(reminderText: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Reminder Alarm",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent
        val isOngoing: Boolean
        val isAutoCancel: Boolean

        if (reminderText.contains("study", ignoreCase = true)) {
            val quizIntent = Intent(this, QuizActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            pendingIntent = PendingIntent.getActivity(
                this, 0, quizIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            isOngoing = true
            isAutoCancel = false
        } else {
            val stopSelfIntent = Intent(this, ReminderAlarmService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            pendingIntent = PendingIntent.getService(
                this, 1, stopSelfIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            isOngoing = false
            isAutoCancel = true
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Reminder")
            .setContentText(reminderText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setOngoing(isOngoing)
            .setAutoCancel(isAutoCancel)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        volumeHandler.removeCallbacks(volumeRunnable)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
