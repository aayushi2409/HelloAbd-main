package com.example.hello_abd.ui.theme.screens

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.hello_abd.services.ReminderAlarmService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Data classes for parsing the API response
@Serializable
data class TriviaResponse(val results: List<TriviaQuestion>)

@Serializable
data class TriviaQuestion(val question: String, val correct_answer: String, val incorrect_answers: List<String>)

// Data model for the quiz UI
data class QuizQuestion(val question: String, val options: List<String>, val answer: String)

class QuizActivity : ComponentActivity() {

    // Holds the state of the quiz questions
    private var questionsState by mutableStateOf<List<QuizQuestion>>(emptyList())
    private var isLoading by mutableStateOf(true)
    private var currentQuestionIndex by mutableStateOf(0)
    private var attempts by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fetch questions when the activity is created
        lifecycleScope.launch {
            fetchQuestions()
        }

        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (questionsState.isNotEmpty()) {
                    // Use key() to force recomposition when the question index changes
                    key(currentQuestionIndex) {
                        QuizScreen(questionsState[currentQuestionIndex]) { selectedOption ->
                            handleAnswer(selectedOption)
                        }
                    }
                } else {
                    // Show an error message if questions couldn't be fetched
                    Box(contentAlignment = Alignment.Center) {
                        Text("Failed to load questions. Stopping alarm.")
                        stopAlarmAndFinish() // Stop the alarm if we can't show a quiz
                    }
                }
            }
        }
    }

    private suspend fun fetchQuestions() {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        try {
            val response: TriviaResponse = client.get("https://opentdb.com/api.php?amount=5&category=18&type=multiple").body()
            questionsState = response.results.map {
                // Decode HTML entities and shuffle options
                val decodedQuestion = Html.fromHtml(it.question, Html.FROM_HTML_MODE_LEGACY).toString()
                val correctAnswer = Html.fromHtml(it.correct_answer, Html.FROM_HTML_MODE_LEGACY).toString()
                val incorrectAnswers = it.incorrect_answers.map { ans -> Html.fromHtml(ans, Html.FROM_HTML_MODE_LEGACY).toString() }
                val allOptions = (incorrectAnswers + correctAnswer).shuffled()
                QuizQuestion(decodedQuestion, allOptions, correctAnswer)
            }
        } catch (e: Exception) {
            // Handle exceptions
        } finally {
            isLoading = false
            client.close()
        }
    }

    private fun handleAnswer(selectedOption: String) {
        if (selectedOption == questionsState[currentQuestionIndex].answer) {
            Toast.makeText(this, "Correct! Alarm stopped.", Toast.LENGTH_SHORT).show()
            stopAlarmAndFinish()
        } else {
            attempts++
            if (attempts < 5) {
                Toast.makeText(this, "Incorrect. Try the next one!", Toast.LENGTH_SHORT).show()
                // Simply update the index to show the next question
                currentQuestionIndex = (currentQuestionIndex + 1) % questionsState.size
            } else {
                Toast.makeText(this, "Its okay !", Toast.LENGTH_LONG).show()
                stopAlarmAndFinish()
            }
        }
    }

    private fun stopAlarmAndFinish() {
        val serviceIntent = Intent(this, ReminderAlarmService::class.java)
        stopService(serviceIntent)
        finish()
    }
}

@Composable
fun QuizScreen(question: QuizQuestion, onAnswerSelected: (String) -> Unit) {
    var selectedOption by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(question.question, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))

        question.options.forEach { option ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    selected = (option == selectedOption),
                    onClick = { selectedOption = option }
                )
                Text(text = option, fontSize = 18.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { selectedOption?.let { onAnswerSelected(it) } },
            enabled = selectedOption != null
        ) {
            Text("Submit")
        }
    }
}
