package com.example.hello_abd.ui.theme.screens

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.hello_abd.MainActivity
import com.example.hello_abd.R
import com.example.hello_abd.ui.theme.HelloAbdTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChecklistActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setContent {
            HelloAbdTheme {
                PromiseDayScreen(
                    onContinue = {
                        stopAudio()
                        // Instead of going to MainActivity, go to GalleryActivity first
                        startActivity(Intent(this, GalleryActivity::class.java))
                        finish()
                    },
                    onPlayAudio = { playFullVolumeAudio() }
                )
            }
        }
    }

    private fun playFullVolumeAudio() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.space_ride)
            mediaPlayer?.isLooping = true
        }
        mediaPlayer?.start()

        lifecycleScope.launch {
            while (mediaPlayer?.isPlaying == true) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                delay(500)
            }
        }
    }

    private fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (mediaPlayer?.isPlaying == true) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                return true 
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }
}

@Composable
fun PromiseDayScreen(onContinue: () -> Unit, onPlayAudio: () -> Unit) {
    var q1Answer by remember { mutableStateOf("") }
    
    var q2Gaal by remember { mutableStateOf(false) }
    var q2Eyes by remember { mutableStateOf(false) }
    var q2Voice by remember { mutableStateOf(false) }
    var q2Nothing by remember { mutableStateOf(false) }

    var q3SpecialText by remember { mutableStateOf("") }
    var q3Promise1 by remember { mutableStateOf(false) }
    var q3Promise2 by remember { mutableStateOf(false) }
    var q3Promise3 by remember { mutableStateOf(false) }
    var q3Promise4 by remember { mutableStateOf(false) }

    var q4SelectedOption by remember { mutableStateOf("") }
    val q4Options = listOf("Mardaani 3", "Border 2", "Tap to Reveal (Surprise)", "Avatar")

    var name by remember { mutableStateOf("My Love") }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { name = it.getString("name") ?: "My Love" }
        }
    }

    val isQ1Correct = q1Answer.trim().lowercase() == "tumbbad"
    val isQ2Correct = q2Gaal && q2Eyes && q2Voice && !q2Nothing
    val isQ3Correct = q3SpecialText.trim().length > 20 && q3Promise1 && q3Promise2 && q3Promise3 && q3Promise4
    val isQ4Correct = q4SelectedOption == "Tap to Reveal (Surprise)"

    val allCorrect = isQ1Correct && isQ2Correct && isQ3Correct && isQ4Correct

    val gradientBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1A1A), Color(0xFF2D0A31))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBg)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Special Gift For My Aayu Baccha❤️",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFFF4081),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "I know Aap Gussa ho and I'm really sorryyy❤️ But Tumhe bhi pata h, M majak krra Hun and Now Finally it's Time to Reveal the Truth... Let's complete these tasks.",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        QuestionTitle("1. Which movie we first saw?(Spelling Google Kar Sakti Ho...)")
        OutlinedTextField(
            value = q1Answer,
            onValueChange = { q1Answer = it },
            placeholder = { Text("Type movie name...", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFFF4081),
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        QuestionTitle("2. Which things u like most about me?")
        Column(modifier = Modifier.fillMaxWidth()) {
            CheckboxOption("My gaal", q2Gaal) { q2Gaal = it; if (it) q2Nothing = false }
            CheckboxOption("My eyes", q2Eyes) { q2Eyes = it; if (it) q2Nothing = false }

            CheckboxOption("My Hot Hot Bum!...", q2Voice) { q2Voice = it; if (it) q2Nothing = false }
            CheckboxOption("Nothings", q2Nothing) { 
                q2Nothing = it
                if (it) { q2Gaal = false; q2Eyes = false; q2Voice = false }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        QuestionTitle("3. Promise me By Writing Somethings Special for me")
        OutlinedTextField(
            value = q3SpecialText,
            onValueChange = { q3SpecialText = it },
            placeholder = { Text("Type something special...", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFFFF4081),
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
            )
        )
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            CheckboxOption("Aap humesha Saath rhogii Mere saath.", q3Promise1) { q3Promise1 = it }
            CheckboxOption("Mujhpe Humesha Trust Karogi", q3Promise2) { q3Promise2 = it }
            CheckboxOption("JabTak Proof Na dekhlo bhagwan bhi aaye toh unki baat nhi manogi cox im ur God and u r my Devi", q3Promise3) { q3Promise3 = it }
            CheckboxOption("Mujhse Shaadi Karogi.", q3Promise4) { q3Promise4 = it }
        }

        Spacer(modifier = Modifier.height(24.dp))

        QuestionTitle("4. According to you, which movie I saw with that junior girl?")
        Column(modifier = Modifier.fillMaxWidth()) {
            q4Options.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = (q4SelectedOption == option),
                        onClick = { 
                            q4SelectedOption = option
                            if (option == "Tap to Reveal (Surprise)") {
                                onPlayAudio()
                            }
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF4081), unselectedColor = Color.White)
                    )
                    Text(text = option, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onContinue,
            enabled = allCorrect,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
            modifier = Modifier.fillMaxWidth().height(76.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                if (allCorrect) "Happy promise day my Motilululu.." else "Answer correctly to Unlock...",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun QuestionTitle(title: String) {
    Text(
        text = title,
        color = Color(0xFFFF4081),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

@Composable
fun CheckboxOption(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF4081), uncheckedColor = Color.White)
        )
        Text(text = label, color = Color.White, modifier = Modifier.padding(start = 8.dp))
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PromiseDayScreenPreview() {
    HelloAbdTheme {
        PromiseDayScreen(onContinue = {}, onPlayAudio = {})
    }
}
