package com.example.hello_abd.repository

import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Keep
data class UserProfile(
    val name: String? = null,
    val email: String? = null,
    val dob: String? = null,
    val age: String? = null,
    val place: String? = null,
    val education: String? = null,
    val gender: String? = null
)

/**
 * Model for the User's Chat History (Shown in App)
 */
@Keep
data class ChatHistory(
    val id: String = "",
    val userId: String = "",
    val command: String = "",
    val response: String = "",
    val intent: String = "",
    val execution: String = "",
    val timestamp: Long = 0L,
    val formattedDate: String = "",
    val formattedTime: String = ""
)

/**
 * Rich Model for Developer Analytics (Data Science)
 */
@Keep
data class UserEvent(
    val id: String = "",
    val userId: String = "",
    val userEmail: String? = "",
    val command: String = "",
    val response: String = "",
    val intent: String = "",
    val execution: String = "",
    val timestamp: Long = 0L,
    val formattedDate: String = "",
    val formattedTime: String = "",

    // --- Data Science Professional Insights ---
    val eventSerialNumber: Long = 0L,
    val commandLength: Int = 0,
    val commandWordCount: Int = 0,
    val responseLength: Int = 0,
    val responseWordCount: Int = 0,
    val dayOfWeek: String = "",
    val hourOfDay: Int = 0,
    val isWeekend: Boolean = false,
    val deviceModel: String = "",
    val androidVersion: String = "",
    val language: String = "",
    val timeZone: String = "",
    val platform: String = "Android"
)

class UserRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val usersCollection = db.collection("users")
    private val historyCollection = db.collection("chat_history")
    private val eventsCollection = db.collection("user_events")
    private val syllabusCollection = db.collection("syllabus")

    suspend fun getUserProfile(): UserProfile? {
        val userId = auth.currentUser?.uid ?: return null
        return try {
            usersCollection.document(userId).get().await().toObject(UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val userId = auth.currentUser?.uid ?: return
        usersCollection.document(userId).set(profile).await()
    }

    /**
     * Logs interaction to both 'chat_history' (for user) and 'user_events' (for analytics).
     */
    suspend fun logUserEvent(command: String, response: String, intent: String, execution: String) {
        val user = auth.currentUser ?: return

        val timestamp = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp

        val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dayFormatter = SimpleDateFormat("EEEE", Locale.ENGLISH)

        val fDate = dateFormatter.format(calendar.time)
        val fTime = timeFormatter.format(calendar.time)
        val logId = historyCollection.document().id

        // 1. Prepare User History
        val historyEntry = ChatHistory(
            id = logId,
            userId = user.uid,
            command = command,
            response = response,
            intent = intent,
            execution = execution,
            timestamp = timestamp,
            formattedDate = fDate,
            formattedTime = fTime
        )

        // 2. Prepare Rich Analytics
        val wordCountCommand = command.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        val wordCountResponse = response.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        val dayOfWeek = dayFormatter.format(calendar.time)

        val analyticsEvent = UserEvent(
            id = logId,
            userId = user.uid,
            userEmail = user.email,
            command = command,
            response = response,
            intent = intent,
            execution = execution,
            timestamp = timestamp,
            formattedDate = fDate,
            formattedTime = fTime,
            eventSerialNumber = timestamp,
            commandLength = command.length,
            commandWordCount = wordCountCommand,
            responseLength = response.length,
            responseWordCount = wordCountResponse,
            dayOfWeek = dayOfWeek,
            hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
            isWeekend = dayOfWeek == "Saturday" || dayOfWeek == "Sunday",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            language = Locale.getDefault().toString(),
            timeZone = TimeZone.getDefault().id
        )

        try {
            // Save to both collections
            historyCollection.document(logId).set(historyEntry)
            eventsCollection.document(logId).set(analyticsEvent).await()
            Log.d("UserRepository", "Logged to history and analytics: $intent")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error logging event: ${e.message}")
        }
    }

    suspend fun getChatHistory(): List<ChatHistory> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val result = historyCollection
                .whereEqualTo("userId", userId)
                .limit(100)
                .get()
                .await()
                .toObjects(ChatHistory::class.java)

            result.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e("UserRepository", "Error fetching history: ${e.message}")
            emptyList()
        }
    }

    suspend fun deleteHistoryItem(id: String) {
        try {
            historyCollection.document(id).delete().await()
            // Also delete from analytics as previously requested
            eventsCollection.document(id).delete().await()
            Log.d("UserRepository", "Deleted from history and analytics")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error deleting event: ${e.message}")
        }
    }

    // --- Syllabus Persistence Methods ---

    suspend fun saveSyllabusList(syllabusList: List<SyllabusData>) {
        val userId = auth.currentUser?.uid ?: return
        try {
            // We store the entire list as a map for this user to keep it simple,
            // or we could store each subject as a document. Documents are better for scalability.
            syllabusCollection.document(userId).set(mapOf("subjects" to syllabusList)).await()
            Log.d("UserRepository", "Syllabus list saved to Firestore")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error saving syllabus: ${e.message}")
        }
    }

    suspend fun getSyllabusList(): List<SyllabusData> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        return try {
            val document = syllabusCollection.document(userId).get().await()
            if (document.exists()) {
                val data = document.toObject(SyllabusListWrapper::class.java)
                data?.subjects ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Error fetching syllabus: ${e.message}")
            emptyList()
        }
    }

    // Wrapper for Firestore to handle the list
    @Keep
    data class SyllabusListWrapper(
        val subjects: List<SyllabusData> = emptyList()
    )
}
