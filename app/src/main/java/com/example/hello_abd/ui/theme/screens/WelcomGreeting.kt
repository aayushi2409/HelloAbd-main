package com.example.hello_abd.ui.theme.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.example.hello_abd.R
import com.example.hello_abd.repository.UserProfile
import com.example.hello_abd.services.WakeWordService
import com.example.hello_abd.utils.AppLauncher
import com.example.hello_abd.viewmodel.ChatMessage
import com.example.hello_abd.viewmodel.JarvisViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

fun cleanTextForSpeech(text: String): String {
    val nonSpokenCharactersRegex = "[^\\p{L}\\p{N}\\s.,?!]".toRegex()
    return text
        .replace(nonSpokenCharactersRegex, "")
        .replace("\\s+".toRegex(), " ")
        .trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AncientJarvisScreen(viewModel: JarvisViewModel = viewModel()) {
    val uiState by viewModel.uiState
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.ai_ai))

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var pendingCall by remember { mutableStateOf<String?>(null) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var confirmationMessage by remember { mutableStateOf("") }
    
    // Navigation States
    var showHistory by remember { mutableStateOf(false) }
    var showInsights by remember { mutableStateOf(false) }
    var showSyllabus by remember { mutableStateOf(false) }

    val alarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
    }

    LaunchedEffect(key1 = Unit) { viewModel.init(context) }

    LaunchedEffect(key1 = context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            if (alarmManager?.canScheduleExactAlarms() == false) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                alarmPermissionLauncher.launch(intent)
            }
        }
    }

    LaunchedEffect(uiState.appSettings) {
        tts?.let {
            it.setSpeechRate(uiState.appSettings.speechRate)
            it.setPitch(uiState.appSettings.speechPitch)
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions.all { it.value }) {
                pendingCall?.let {
                    tts?.speak("Calling $it", TextToSpeech.QUEUE_FLUSH, null, null)
                    AppLauncher.launch(context, AppLauncher.initiateCall(context, it))
                    pendingCall = null
                }
            }
        }
    )

    val wakeWordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                val serviceIntent = Intent(context, WakeWordService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    )

    LaunchedEffect(uiState.appSettings.isWakeWordEnabled) {
        val serviceIntent = Intent(context, WakeWordService::class.java)
        if (uiState.appSettings.isWakeWordEnabled) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                wakeWordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        } else {
            context.stopService(serviceIntent)
        }
    }

    DisposableEffect(Unit) {
        val ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag("en-IN")
            }
        }
        tts = ttsInstance

        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    LaunchedEffect(uiState.messages) {
        val lastMessage = uiState.messages.lastOrNull()
        if (lastMessage != null && !lastMessage.isFromUser && uiState.appSettings.isVoiceReplyEnabled) {
            val cleanedText = cleanTextForSpeech(lastMessage.text)
            if (cleanedText.isNotBlank()) {
                tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    fun handleQuery(query: String) {
        viewModel.handleQuery(query, context)
    }

    // Navigation Logic
    if (showHistory) {
        BackHandler { showHistory = false }
        HistoryScreen(onBack = { showHistory = false })
    } else if (showInsights) {
        BackHandler { showInsights = false }
        InsightsScreen(onBack = { showInsights = false })
    } else if (showSyllabus) {
        BackHandler { showSyllabus = false }
        SyllabusScreen(viewModel = viewModel, onBack = { showSyllabus = false })
    }
    else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ProfileDrawerContent(
                    viewModel = viewModel,
                    onOpenHistory = {
                        scope.launch {
                            drawerState.close()
                            showHistory = true
                        }
                    },
                    onOpenInsights = {
                        scope.launch {
                            drawerState.close()
                            showInsights = true
                        }
                    },
                    onOpenSyllabus = {
                        scope.launch {
                            drawerState.close()
                            showSyllabus = true
                        }
                    },
                    onProfileSaved = { name ->
                        scope.launch { drawerState.close() }
                        confirmationMessage = "Welcome ${name.takeIf { it.isNotBlank() }?.plus(" ") ?: ""}"
                        showSaveConfirmation = true
                    }
                )
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                uiState.userProfile?.name?.takeIf { it.isNotBlank() }?.let { "" } ?: "Virtual Assistant",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = TextStyle(
                                    color = Color(0xFF9C27B0),
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                containerColor = Color.Black
            ) { innerPadding ->
                Box(modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()) {

                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            items(uiState.messages.reversed()) { message -> ChatItem(message) }
                        }

                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(16.dp),
                                color = Color.Cyan
                            )
                        }

                        AncientSearchBar(onQuery = { handleQuery(it) })
                    }

                    AnimatedVisibility(
                        visible = showSaveConfirmation,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = confirmationMessage,
                                color = Color.White,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                            )
                        }
                    }

                    LaunchedEffect(showSaveConfirmation) {
                        if (showSaveConfirmation) {
                            delay(5000)
                            showSaveConfirmation = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileDrawerContent(
    viewModel: JarvisViewModel, 
    onOpenHistory: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenSyllabus: () -> Unit,
    onProfileSaved: (String) -> Unit
) {
    val uiState by viewModel.uiState
    val context = LocalContext.current

    var isProfileExpanded by remember { mutableStateOf(false) }
    var isSettingsExpanded by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val cropImage = rememberLauncherForActivityResult(contract = CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { viewModel.saveProfileImage(context, it) }
        }
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val cropOptions = CropImageContractOptions(it, CropImageOptions())
            cropImage.launch(cropOptions)
        }
    }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var education by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }

    LaunchedEffect(uiState.userProfile, uiState.email) {
        name = uiState.userProfile?.name ?: ""
        email = uiState.userProfile?.email ?: uiState.email ?: ""
        dob = uiState.userProfile?.dob ?: ""
        age = uiState.userProfile?.age ?: ""
        place = uiState.userProfile?.place ?: ""
        education = uiState.userProfile?.education ?: ""
        gender = uiState.userProfile?.gender ?: ""
    }

    ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.85f)) {
        if (showAbout) {
            AboutScreen(onBack = { showAbout = false })
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1C1C1E))
                    .padding(16.dp)
            ) {
                item {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                         AsyncImage(
                            model = uiState.photoUrl,
                            contentDescription = "Profile Picture",
                            error = rememberVectorPainter(image = Icons.Default.AccountCircle),
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Text("Change Photo")
                        }
                        TextButton(onClick = { viewModel.removeProfileImage(context) }) {
                            Text("Remove Photo")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    val greeting = uiState.userProfile?.name?.takeIf { it.isNotBlank() }?.let { "👋 Welcome $it" } ?: uiState.email ?: ""
                    Text(
                        text = greeting,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                }

                item {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        selected = isProfileExpanded,
                        onClick = { isProfileExpanded = !isProfileExpanded },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                item {
                    AnimatedVisibility(visible = isProfileExpanded) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            ProfileTextField(value = name, onValueChange = { name = it }, label = "Name", icon = Icons.Default.Person)
                            Spacer(modifier = Modifier.height(12.dp))
                            ProfileTextField(value = email, onValueChange = { email = it }, label = "Email", icon = Icons.Default.Email)
                            Spacer(modifier = Modifier.height(12.dp))
                            ProfileTextField(value = dob, onValueChange = { dob = it }, label = "Date of Birth", icon = Icons.Default.Cake)
                            Spacer(modifier = Modifier.height(12.dp))
                            ProfileTextField(value = age, onValueChange = { age = it }, label = "Age", keyboardType = KeyboardType.Number)
                            Spacer(modifier = Modifier.height(12.dp))
                            ProfileTextField(value = place, onValueChange = { place = it }, label = "Place", icon = Icons.Default.LocationOn)
                            Spacer(modifier = Modifier.height(12.dp))
                            ProfileTextField(value = education, onValueChange = { education = it }, label = "Education", icon = Icons.Default.School)
                            Spacer(modifier = Modifier.height(12.dp))
                            ProfileTextField(value = gender, onValueChange = { gender = it }, label = "Gender", icon = Icons.Default.Wc)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val updatedProfile = UserProfile(name, email, dob, age, place, education, gender)
                                    viewModel.saveUserProfile(updatedProfile)
                                    onProfileSaved(name)
                                    isProfileExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
                            ) {
                                Text("Save Profile")
                            }
                        }
                    }
                }

                item {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = isSettingsExpanded,
                        onClick = { isSettingsExpanded = !isSettingsExpanded },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                item {
                    AnimatedVisibility(visible = isSettingsExpanded) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable Wake Word", modifier = Modifier.weight(1f), color = Color.White)
                                Switch(
                                    checked = uiState.appSettings.isWakeWordEnabled,
                                    onCheckedChange = { viewModel.saveWakeWordEnabled(it) }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Voice Reply", modifier = Modifier.weight(1f), color = Color.White)
                                Switch(
                                    checked = uiState.appSettings.isVoiceReplyEnabled,
                                    onCheckedChange = { viewModel.saveVoiceReplyEnabled(it) }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Speech Speed", color = Color.White)
                            Slider(
                                value = uiState.appSettings.speechRate,
                                onValueChange = { viewModel.saveSpeechRate(it) },
                                valueRange = 0.5f..2.0f
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Speech Pitch", color = Color.White)
                            Slider(
                                value = uiState.appSettings.speechPitch,
                                onValueChange = { viewModel.saveSpeechPitch(it) },
                                valueRange = 0.5f..2.0f
                            )
                        }
                    }
                }

                item { 
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.School, contentDescription = "Student Planner") }, 
                        label = { Text("Student Planner") }, 
                        selected = false, 
                        onClick = onOpenSyllabus, 
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    ) 
                }

                item { 
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.QueryStats, contentDescription = "Insights") }, 
                        label = { Text("Jarvis Insights") }, 
                        selected = false, 
                        onClick = onOpenInsights, 
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    ) 
                }

                item { 
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.History, contentDescription = "Chat History") }, 
                        label = { Text("Chat History") }, 
                        selected = false, 
                        onClick = onOpenHistory, 
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    ) 
                }

                item {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Info, contentDescription = "About") },
                        label = { Text("About") },
                        selected = false,
                        onClick = { showAbout = true },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                item { Spacer(modifier = Modifier.weight(1f)) }

                item {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out") },
                        label = { Text("Sign Out") },
                        selected = false,
                        onClick = {
                            val serviceIntent = Intent(context, WakeWordService::class.java)
                            context.stopService(serviceIntent)
                            viewModel.signOut()
                            val intent = Intent(context, EmailPasswordActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Hello-Abd") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = Color.Black.copy(alpha = 0.9f)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(painter = rememberVectorPainter(Icons.Default.AutoAwesome), contentDescription = "App Logo", tint = Color(0xFF9C27B0), modifier = Modifier.size(60.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Meet Your Assistant", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("To get started, simply say the wake word (Hey ABD) Before Every Command:", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Color.LightGray)
                Spacer(modifier = Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = Color.DarkGray.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                    Text("Wake Word: \"Hey Abd\"", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text("What Can I Do?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
            }

            item { CommandSection() }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Copyright © ${Calendar.getInstance().get(Calendar.YEAR)} Hello-Abd. All rights reserved.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = Color.Gray, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun CommandSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CommandCategoryCard(
            icon = Icons.Default.Apps,
            title = "General Commands",
            commands = listOf(
                "Examples: \"Open YouTube\"",
                "Search  \"Ind vs Aus Match \" on Google",
                "Search \" DataScience Roadmap\" on Youtube",
                "Play \"Dhurandhar Song\""
            )
        )
        CommandCategoryCard(
            icon = Icons.Default.Smartphone,
            title = "Device Control",
            commands = listOf(
                "Say \"turn on the light\"(Flashlight)",
                "Say \"turn off the light\"",
                "Open Wi-Fi settings",
                "Increase volume",
                "Decrease volume",
                "Open camera"
            )
        )
        CommandCategoryCard(
            icon = Icons.Default.Call,
            title = "Communication",
            commands = listOf(
                "Call Abhyuday",
                "Open WhatsApp"
            )
        )
        CommandCategoryCard(
            icon = Icons.Default.Alarm,
            title = "Productivity",
            commands = listOf(
                "Set alarm for 7 AM",
                "Say \"Remind me to Study after 10 minutes\""
            )
        )
    }
}

@Composable
fun CommandCategoryCard(icon: ImageVector, title: String, commands: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = title, tint = Color(0xFF9C27B0))
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            commands.forEach { command ->
                Text(
                    text = "• $command",
                    style = MaterialTheme. typography.bodyLarge,
                    color = Color.LightGray,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
            }
        }
    }
}


@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = icon?.let { @Composable { Icon(it, contentDescription = null) } },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFFCE93D8),
            focusedBorderColor = Color(0xFFCE93D8),
            unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
            focusedLabelColor = Color(0xFFCE93D8),
            unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
            focusedLeadingIconColor = Color(0xFFCE93D8),
            unfocusedLeadingIconColor = Color.White.copy(alpha = 0.7f)
        )
    )
}


@Composable
fun ChatItem(message: ChatMessage) {
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (message.isFromUser) Color.Cyan.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f)
    val textColor = if (message.isFromUser) Color.White else Color.White.copy(alpha = 0.9f)

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.8f),
            color = backgroundColor,
            shape = RoundedCornerShape(
                topStart = if (message.isFromUser) 16.dp else 0.dp,
                topEnd = if (message.isFromUser) 0.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Text(text = message.text, color = textColor, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun AncientSearchBar(onQuery: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    var isMicActive by remember { mutableStateOf(false) }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            isMicActive = false
            if (it.resultCode == Activity.RESULT_OK) {
                val data: Intent? = it.data
                val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!result.isNullOrEmpty()) {
                    onQuery(result[0])
                }
            }
        }
    )

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            }
            speechRecognizerLauncher.launch(speechIntent)
            isMicActive = true
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        placeholder = { Text("Ask the Virtual Assistant...", color = Color.White.copy(alpha = 0.5f)) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.Cyan,
                    modifier = Modifier.clickable { onQuery(text); text = "" }.padding(8.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Mic",
                    tint = if (isMicActive) Color.Red else Color.Cyan,
                    modifier = Modifier.clickable {
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) -> {
                                val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                                }
                                speechRecognizerLauncher.launch(speechIntent)
                                isMicActive = true
                            }
                            else -> {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }.padding(8.dp)
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color.Cyan,
            focusedBorderColor = Color.Cyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.6f)
        )
    )
}
