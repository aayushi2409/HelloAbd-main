package com.example.hello_abd.viewmodel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hello_abd.network.JarvisService
import com.example.hello_abd.repository.AppSettings
import com.example.hello_abd.repository.SettingsRepository
import com.example.hello_abd.repository.SyllabusData
import com.example.hello_abd.repository.SyllabusTopic
import com.example.hello_abd.repository.SyllabusUnit
import com.example.hello_abd.repository.RawSyllabusData
import com.example.hello_abd.repository.UserProfile
import com.example.hello_abd.repository.UserRepository
import com.example.hello_abd.services.ReminderBroadcastReceiver
import com.example.hello_abd.utils.AppLauncher
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

// Data class for a single chat message
data class ChatMessage(val text: String, val isFromUser: Boolean)

data class JarvisUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val uploadProgress: Float = 0f,
    val uploadStatus: String = "",
    val estimatedTime: String = "",
    val error: String? = null,
    val userProfile: UserProfile? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val appSettings: AppSettings = AppSettings(isWakeWordEnabled = true, isVoiceReplyEnabled = true, speechRate = 1.0f, speechPitch = 1.0f)
)

// Define a one-time event for signaling actions
sealed class UiEvent {
    object StartSpeechRecognition : UiEvent()
}

class JarvisViewModel : ViewModel() {

    private val _uiState = mutableStateOf(JarvisUiState())
    val uiState: State<JarvisUiState> = _uiState

    private val _syllabusList = mutableStateOf<List<SyllabusData>>(emptyList())
    val syllabusList: State<List<SyllabusData>> = _syllabusList

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    private val _speakFlow = MutableSharedFlow<String>()
    val speakFlow = _speakFlow.asSharedFlow()

    private val userRepository = UserRepository()
    private lateinit var settingsRepository: SettingsRepository
    private val auth = FirebaseAuth.getInstance()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = "AIzaSyBOHUmpw_Pmk8MmIFYUWsvpwY2cCSQQURc"
    )

    fun init(context: Context) {
        settingsRepository = SettingsRepository(context)
        viewModelScope.launch {
            val profile = userRepository.getUserProfile()
            val currentUser = auth.currentUser
            val localPhotoPath = getLocalPhotoPath(context)
            val settings = settingsRepository.getSettings()

            // Load saved syllabus list
            val savedSyllabus = userRepository.getSyllabusList()

            _syllabusList.value = savedSyllabus
            _uiState.value = _uiState.value.copy(
                userProfile = profile,
                email = currentUser?.email,
                photoUrl = localPhotoPath ?: currentUser?.photoUrl?.toString(),
                appSettings = settings
            )
        }
    }

    private fun saveSyllabusToCloud() {
        viewModelScope.launch {
            userRepository.saveSyllabusList(_syllabusList.value)
        }
    }

    fun addSubject(subjectName: String) {
        val newSyllabus = SyllabusData(subject = subjectName)
        _syllabusList.value = _syllabusList.value + newSyllabus
        saveSyllabusToCloud()
    }

    fun deleteSubject(subjectId: String) {
        _syllabusList.value = _syllabusList.value.filter { it.id != subjectId }
        saveSyllabusToCloud()
    }

    private fun generateSyllabusPrompt(subjectName: String) = """
        You are an expert academic assistant. I have provided a document (Image or PDF) that contains syllabus information. 
        Your task is to extract the syllabus specifically for the subject: "$subjectName".
        
        Guidelines:
        1. Search the entire document for sections related to "$subjectName".
        2. Ignore any other subjects, general university rules, or irrelevant text.
        3. Organize the found information into "Units" and "Topics". 
        4. If the document is mixed up or the information is scattered, synthesize it into a logical structure.
        5. Format the output as a clean JSON object with this structure:
        {
          "subject": "$subjectName",
          "units": [
            {
              "unit_title": "Unit Name/Number",
              "topics": ["Topic 1", "Topic 2"]
            }
          ]
        }
        Return ONLY the JSON object. Do not include any introductory or concluding text.
    """.trimIndent()

    private var progressJob: Job? = null

    private fun startSmoothProgress(start: Float, end: Float, durationMs: Long) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val steps = 50
            val delayPerStep = durationMs / steps
            val increment = (end - start) / steps
            var current = start
            repeat(steps) {
                current += increment
                _uiState.value = _uiState.value.copy(uploadProgress = current)
                delay(delayPerStep)
            }
        }
    }

    fun extractSyllabusForSubject(subjectId: String, bitmap: Bitmap) {
        val subject = _syllabusList.value.find { it.id == subjectId }?.subject ?: "the subject"
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            uploadProgress = 0.1f,
            uploadStatus = "Preparing image...",
            estimatedTime = "Approx. 10-15s",
            error = null
        )
        viewModelScope.launch {
            try {
                updateProgress(0.2f, "Sending to Jarvis Intelligence...")
                startSmoothProgress(0.2f, 0.8f, 8000)

                val response = generativeModel.generateContent(
                    content {
                        image(bitmap)
                        text(generateSyllabusPrompt(subject))
                    }
                )

                progressJob?.cancel()
                updateProgress(0.9f, "Organizing topics...")
                parseAndSaveSyllabus(subjectId, response.text ?: "")
            } catch (e: Exception) {
                Log.e("JarvisViewModel", "Extraction failed", e)
                _uiState.value = _uiState.value.copy(error = "Extraction failed: ${e.localizedMessage ?: "Jarvis couldn't read the image clearly."}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, uploadProgress = 0f, uploadStatus = "", estimatedTime = "")
            }
        }
    }

    fun extractSyllabusFromPdf(context: Context, subjectId: String, pdfUri: Uri) {
        val subject = _syllabusList.value.find { it.id == subjectId }?.subject ?: "the subject"
        _uiState.value = _uiState.value.copy(isLoading = true, uploadProgress = 0.05f, uploadStatus = "Reading PDF...", error = null)

        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(pdfUri)
                val bytes = inputStream?.use { it.readBytes() }

                if (bytes != null) {
                    val fileSize = bytes.size
                    val sizeMB = fileSize / (1024 * 1024f)
                    
                    if (sizeMB > 20) {
                        _uiState.value = _uiState.value.copy(error = "PDF is too large (max 20MB supported).", isLoading = false)
                        return@launch
                    }

                    val estSeconds = (sizeMB * 15 + 10).toInt() // Rough estimation: 15s per MB + 10s processing
                    val timeStr = if (estSeconds < 60) "~$estSeconds sec" else "~${estSeconds / 60} min"

                    _uiState.value = _uiState.value.copy(
                        uploadStatus = "Uploading ${String.format("%.1f", sizeMB)} MB...",
                        estimatedTime = timeStr
                    )

                    startSmoothProgress(0.1f, 0.9f, estSeconds * 1000L)

                    val response = generativeModel.generateContent(
                        content {
                            blob("application/pdf", bytes)
                            text(generateSyllabusPrompt(subject))
                        }
                    )

                    progressJob?.cancel()
                    updateProgress(0.95f, "Finalizing analysis...")
                    parseAndSaveSyllabus(subjectId, response.text ?: "")
                } else {
                    _uiState.value = _uiState.value.copy(error = "Unable to access the PDF file.")
                }
            } catch (e: Exception) {
                Log.e("JarvisViewModel", "PDF Extraction failed", e)
                _uiState.value = _uiState.value.copy(error = "Failed to process the PDF: ${e.localizedMessage ?: "It might be too large."}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, uploadProgress = 0f, uploadStatus = "", estimatedTime = "")
            }
        }
    }

    private fun updateProgress(progress: Float, status: String) {
        _uiState.value = _uiState.value.copy(uploadProgress = progress, uploadStatus = status)
    }

    private fun parseAndSaveSyllabus(subjectId: String, extractedText: String) {
        if (extractedText.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Jarvis couldn't find any syllabus details in that file.")
            return
        }

        val jsonStartIndex = extractedText.indexOf('{')
        val jsonEndIndex = extractedText.lastIndexOf('}')

        if (jsonStartIndex == -1 || jsonEndIndex == -1 || jsonEndIndex <= jsonStartIndex) {
            Log.e("JarvisViewModel", "No JSON block found in AI response")
            _uiState.value = _uiState.value.copy(error = "Formatting error. Please try a clearer file.")
            return
        }

        val jsonString = extractedText.substring(jsonStartIndex, jsonEndIndex + 1)

        try {
            val rawData = Json { ignoreUnknownKeys = true }.decodeFromString<RawSyllabusData>(jsonString)
            val mappedUnits = rawData.units.map { rawUnit ->
                SyllabusUnit(
                    unit_title = rawUnit.unit_title,
                    topics = rawUnit.topics.map { SyllabusTopic(title = it) }
                )
            }

            val updatedList = _syllabusList.value.map { syllabus ->
                if (syllabus.id == subjectId) {
                    syllabus.copy(units = mappedUnits)
                } else syllabus
            }
            _syllabusList.value = updatedList
            saveSyllabusToCloud()
            _uiState.value = _uiState.value.copy(error = null)
        } catch (e: Exception) {
            Log.e("JarvisViewModel", "JSON parsing failed", e)
            _uiState.value = _uiState.value.copy(error = "Parsing failed. Please try again.")
        }
    }

    fun toggleTopic(subjectId: String, topicId: String) {
        val updatedList = _syllabusList.value.map { syllabus ->
            if (syllabus.id == subjectId) {
                val updatedUnits = syllabus.units.map { unit ->
                    val updatedTopics = unit.topics.map { topic ->
                        if (topic.id == topicId) topic.copy(isCompleted = !topic.isCompleted) else topic
                    }
                    unit.copy(topics = updatedTopics)
                }
                syllabus.copy(units = updatedUnits)
            } else syllabus
        }
        _syllabusList.value = updatedList
        saveSyllabusToCloud()
    }

    fun saveProfileImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val destinationFile = File(context.filesDir, "profile_image.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val newPath = destinationFile.toURI().toString()
                saveLocalPhotoPath(context, newPath)
                _uiState.value = _uiState.value.copy(photoUrl = newPath)
            } catch (e: Exception) {
                Log.e("JarvisViewModel", "Error saving profile image", e)
            }
        }
    }

    fun removeProfileImage(context: Context) {
        val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            remove("profile_photo_path")
            apply()
        }
        _uiState.value = _uiState.value.copy(photoUrl = auth.currentUser?.photoUrl?.toString()) // Revert to original or null
    }

    private fun saveLocalPhotoPath(context: Context, path: String) {
        val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("profile_photo_path", path)
            apply()
        }
    }

    private fun getLocalPhotoPath(context: Context): String? {
        val sharedPref = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPref.getString("profile_photo_path", null)
    }

    fun saveUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            try {
                userRepository.saveUserProfile(profile)
                _uiState.value = _uiState.value.copy(userProfile = profile, error = null)
            } catch (e: Exception) {
                Log.e("JarvisViewModel", "Error saving user profile", e)
                _uiState.value = _uiState.value.copy(error = "Failed to save profile.")
            }
        }
    }

    fun saveWakeWordEnabled(isEnabled: Boolean) {
        settingsRepository.saveWakeWordEnabled(isEnabled)
        _uiState.value = uiState.value.copy(appSettings = uiState.value.appSettings.copy(isWakeWordEnabled = isEnabled))
    }

    fun saveVoiceReplyEnabled(isEnabled: Boolean) {
        settingsRepository.saveVoiceReplyEnabled(isEnabled)
        _uiState.value = uiState.value.copy(appSettings = uiState.value.appSettings.copy(isVoiceReplyEnabled = isEnabled))
    }

    fun saveSpeechRate(rate: Float) {
        settingsRepository.saveSpeechRate(rate)
        _uiState.value = uiState.value.copy(appSettings = uiState.value.appSettings.copy(speechRate = rate))
    }

    fun saveSpeechPitch(pitch: Float) {
        settingsRepository.saveSpeechPitch(pitch)
        _uiState.value = uiState.value.copy(appSettings = uiState.value.appSettings.copy(speechPitch = pitch))
    }

    fun signOut() {
        auth.signOut()
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + message
        )
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }

    fun handleQuery(query: String, context: Context): String {
        val userMessage = ChatMessage(text = query, isFromUser = true)
        addMessage(userMessage)

        val lowerCaseQuery = query.lowercase(Locale.getDefault())

        var detectedIntent = "unknown"
        var executionType = "local"

        val response = when {
            lowerCaseQuery.matches(Regex("remind me to (.+) after (\\d+) (second|seconds|minute|minutes|hour|hours)")) -> {
                detectedIntent = "set_reminder"
                handleReminder(lowerCaseQuery, context)
            }
            lowerCaseQuery.matches(Regex("open( the)? youtube")) -> {
                detectedIntent = "open_app_youtube"
                AppLauncher.launch(context, AppLauncher.openYouTube(context))
                "Opening YouTube"
            }
            lowerCaseQuery.matches(Regex("open( the)? google")) -> {
                detectedIntent = "open_app_google"
                AppLauncher.launch(context, AppLauncher.openGoogle(context))
                "Opening Google"
            }
            lowerCaseQuery.contains("google") -> {
                detectedIntent = "search_google"
                val searchTerm = lowerCaseQuery.removePrefix("search").replace("google", "").trim()
                if (searchTerm.isNotEmpty()) {
                    AppLauncher.launch(context, AppLauncher.searchGoogle(context, searchTerm))
                    "Searching Google for $searchTerm"
                } else {
                    "What would you like to search on Google?"
                }
            }
            lowerCaseQuery.contains("youtube") -> {
                detectedIntent = "search_youtube"
                val searchTerm = lowerCaseQuery.removePrefix("search").replace("youtube", "").trim()
                if (searchTerm.isNotEmpty()) {
                    AppLauncher.launch(context, AppLauncher.searchYouTube(context, searchTerm))
                    "Searching YouTube for $searchTerm"
                } else {
                    "What would you like to search on YouTube?"
                }
            }
            lowerCaseQuery.startsWith("play") -> {
                detectedIntent = "play_music"
                val songName = lowerCaseQuery.removePrefix("play").trim()
                if (songName.isNotEmpty()) {
                    AppLauncher.launch(context, AppLauncher.searchYouTube(context, songName))
                    "Playing $songName on YouTube"
                } else {
                    "What song would you like to play?"
                }
            }
            lowerCaseQuery.startsWith("call") -> {
                detectedIntent = "make_call"
                val contactName = lowerCaseQuery.removePrefix("call").trim()
                if (contactName.isNotEmpty()) {
                    AppLauncher.launch(context, AppLauncher.initiateCall(context, contactName))
                    "Calling $contactName"
                } else {
                    "Who would you like to call?"
                }
            }
            lowerCaseQuery.contains("alarm") -> {
                detectedIntent = "open_alarm"
                AppLauncher.launch(context, AppLauncher.setAlarm(context))
                "Opening alarms"
            }
            lowerCaseQuery.contains("Turn on the light") -> {
                detectedIntent = "flashlight_on"
                AppLauncher.toggleFlashlight(context, true)
                "Turning on the light"
            }
            lowerCaseQuery.contains("turn of the light") -> {
                detectedIntent = "flashlight_off"
                AppLauncher.toggleFlashlight(context, false)
                "Turning off the light"
            }
            lowerCaseQuery.contains("wi-fi") -> {
                detectedIntent = "open_wifi_settings"
                AppLauncher.launch(context, AppLauncher.openWifiSettings(context))
                "Opening Wi-Fi settings"
            }
            lowerCaseQuery.contains("download") -> {
                detectedIntent = "open_playstore"
                AppLauncher.launch(context, AppLauncher.openPlayStore(context))
                "Opening the Play Store"
            }
            lowerCaseQuery.contains("camera") -> {
                detectedIntent = "open_camera"
                AppLauncher.launch(context, AppLauncher.openCamera(context))
                "Opening the camera"
            }
            lowerCaseQuery.contains("volume up") -> {
                detectedIntent = "volume_up"
                AppLauncher.volumeUp(context)
                "Increasing volume"
            }
            lowerCaseQuery.contains("volume down") -> {
                detectedIntent = "volume_down"
                AppLauncher.volumeDown(context)
                "Decreasing volume"
            }
            lowerCaseQuery.contains("whatsapp") -> {
                detectedIntent = "open_whatsapp"
                AppLauncher.launch(context, AppLauncher.openWhatsApp(context))
                "Opening WhatsApp"
            }
            else -> {
                executionType = "AI"
                detectedIntent = "ai_query"
                sendQuery(query)
                ""
            }
        }

        if (response != "") {
            viewModelScope.launch {
                userRepository.logUserEvent(query, response, detectedIntent, executionType)
            }
        }

        addMessage(ChatMessage(response, isFromUser = false))
        return response
    }

    private fun handleReminder(query: String, context: Context): String {
        val reminderPattern = "remind me to (.+) after (\\d+) (second|seconds|minute|minutes|hour|hours)".toRegex()
        val matchResult = reminderPattern.find(query)

        if (matchResult != null) {
            val (reminder, timeValue, timeUnit) = matchResult.destructured
            val timeInMillis = when (timeUnit) {
                "second", "seconds" -> TimeUnit.SECONDS.toMillis(timeValue.toLong())
                "minute", "minutes" -> TimeUnit.MINUTES.toMillis(timeValue.toLong())
                "hour", "hours" -> TimeUnit.HOURS.toMillis(timeValue.toLong())
                else -> 0
            }

            if (timeInMillis > 0) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
                    putExtra("REMINDER_TEXT", reminder)
                }
                val pendingIntent = PendingIntent.getBroadcast(context, System.currentTimeMillis().toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val triggerAtMillis = System.currentTimeMillis() + timeInMillis
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    return "I need permission to schedule exact alarms for reminders."
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    return "Okay, I will remind you to $reminder in $timeValue $timeUnit."
                }
            }
        }

        return "I'm sorry, I didn't understand the reminder format."
    }

    fun sendQuery(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            val result = JarvisService.getResponse(query)
            result.onSuccess {
                addMessage(ChatMessage(text = it, isFromUser = false))
                _speakFlow.emit(it)
                userRepository.logUserEvent(query, it, "ai_query", "AI")
            }.onFailure {
                val errorText = it.message ?: "An unknown error occurred"
                addMessage(ChatMessage(text = errorText, isFromUser = false))
                _speakFlow.emit(errorText)
                userRepository.logUserEvent(query, "FAILED: $errorText", "ai_query_failed", "AI")
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun triggerSpeechRecognition() {
        viewModelScope.launch {
            _eventFlow.emit(UiEvent.StartSpeechRecognition)
        }
    }
}
