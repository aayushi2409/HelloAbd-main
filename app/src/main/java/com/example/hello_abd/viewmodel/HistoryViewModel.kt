package com.example.hello_abd.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hello_abd.repository.ChatHistory
import com.example.hello_abd.repository.UserRepository
import kotlinx.coroutines.launch
import java.util.Calendar

data class HistoryInsights(
    val topIntent: String = "None",
    val totalInteractions: Int = 0,
    val peakUsageHour: String = "N/A",
    val mostActiveDay: String = "N/A",
    val aiRatio: Int = 0, // Percentage of AI vs Local
    val avgWordCount: Float = 0f
)

data class HistoryUiState(
    val historyItems: List<ChatHistory> = emptyList(),
    val insights: HistoryInsights = HistoryInsights(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HistoryViewModel : ViewModel() {

    private val userRepository = UserRepository()

    private val _uiState = mutableStateOf(HistoryUiState())
    val uiState: State<HistoryUiState> = _uiState

    init {
        loadChatHistory()
    }

    fun loadChatHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val history = userRepository.getChatHistory()
                val insights = calculateInsights(history)
                _uiState.value = _uiState.value.copy(
                    historyItems = history,
                    insights = insights,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to load history.", isLoading = false)
            }
        }
    }

    private fun calculateInsights(items: List<ChatHistory>): HistoryInsights {
        if (items.isEmpty()) return HistoryInsights()
        
        // 1. Top Intent
        val topIntent = items.groupBy { it.intent }
            .maxByOrNull { it.value.size }?.key ?: "Unknown"

        // 2. Peak Hour & Day
        val calendar = Calendar.getInstance()
        val hourCounts = items.groupBy { 
            calendar.timeInMillis = it.timestamp
            calendar.get(Calendar.HOUR_OF_DAY)
        }
        val peakHour = hourCounts.maxByOrNull { it.value.size }?.key ?: 0
        val peakHourStr = if (peakHour > 12) "${peakHour - 12} PM" else if (peakHour == 0) "12 AM" else "$peakHour AM"

        val dayCounts = items.groupBy { 
            calendar.timeInMillis = it.timestamp
            calendar.get(Calendar.DAY_OF_WEEK)
        }
        val mostActiveDayInt = dayCounts.maxByOrNull { it.value.size }?.key ?: 1
        val days = arrayOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val mostActiveDay = days[mostActiveDayInt]

        // 3. AI vs Local Ratio
        val aiCount = items.count { it.execution.equals("AI", ignoreCase = true) }
        val aiRatio = ((aiCount.toFloat() / items.size) * 100).toInt()

        // 4. Avg Word Count
        val totalWords = items.sumOf { it.command.split("\\s+".toRegex()).size }
        val avgWords = totalWords.toFloat() / items.size
            
        return HistoryInsights(
            topIntent = topIntent.replace("_", " ").uppercase(),
            totalInteractions = items.size,
            peakUsageHour = peakHourStr,
            mostActiveDay = mostActiveDay,
            aiRatio = aiRatio,
            avgWordCount = avgWords
        )
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            userRepository.deleteHistoryItem(id)
            loadChatHistory()
        }
    }
}
