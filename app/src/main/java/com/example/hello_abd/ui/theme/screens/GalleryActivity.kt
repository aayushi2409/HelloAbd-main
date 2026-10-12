package com.example.hello_abd.ui.theme.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hello_abd.MainActivity
import com.example.hello_abd.R
import com.example.hello_abd.ui.theme.HelloAbdTheme
import kotlinx.coroutines.delay

data class GalleryItem(val imageRes: Int, val description: String)

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HelloAbdTheme {
                GalleryScreen(onFinish = {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                })
            }
        }
    }
}

@Composable
fun GalleryScreen(onFinish: () -> Unit) {
    // List of your images with their descriptions
    val galleryItems = listOf(
        GalleryItem(R.drawable.p_1, "Shruti jr. Nahi Rohit hain woh\uD83E\uDD23"),
        GalleryItem(R.drawable.p_2, "Gemini se Banai yeh sab"),
        GalleryItem(R.drawable.p_3, "Yeh Interface Gemini ka..."),
        GalleryItem(R.drawable.p_4, "Yeh Ai"),
        GalleryItem(R.drawable.p_5, "Yeh Uski Real"),
        GalleryItem(R.drawable.p_6, "Ai"),
        GalleryItem(R.drawable.p_7, "Rohit hi tha woh Bs Naam Change kiya tha Mene"),
        GalleryItem(R.drawable.p_8, "Aur Yeh Theater Wali Real...")
    )

    var showButton by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(5000) // Show for 5 seconds then show button
        showButton = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Proof Saari activity ka 🤣🥳",
                color = Color(0xFFFF4081),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(galleryItems) { item ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = item.imageRes),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.description,
                            color = Color.White,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            if (showButton) {
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 32.dp)
                        .height(56.dp)
                ) {
                    Text("Enter Jarvis World", fontSize = 18.sp)
                }
            }
        }
    }
}
