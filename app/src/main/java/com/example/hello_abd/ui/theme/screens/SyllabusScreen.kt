package com.example.hello_abd.ui.theme.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hello_abd.repository.SyllabusData
import com.example.hello_abd.repository.SyllabusUnit
import com.example.hello_abd.viewmodel.JarvisViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyllabusScreen(viewModel: JarvisViewModel, onBack: () -> Unit) {
    val syllabusList by viewModel.syllabusList
    val uiState by viewModel.uiState
    val context = LocalContext.current
    
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var newSubjectName by remember { mutableStateOf("") }
    var selectedSubjectId by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedSubjectId?.let { id ->
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                viewModel.extractSyllabusForSubject(id, bitmap)
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            selectedSubjectId?.let { id ->
                viewModel.extractSyllabusFromPdf(context, id, it)
            }
        }
    }

    if (selectedSubjectId != null) {
        val selectedSyllabus = syllabusList.find { it.id == selectedSubjectId }
        if (selectedSyllabus != null) {
            SubjectDetailView(
                syllabus = selectedSyllabus,
                onBack = { selectedSubjectId = null },
                onImageUploadClick = { imagePicker.launch("image/*") },
                onPdfUploadClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                isLoading = uiState.isLoading,
                uploadProgress = uiState.uploadProgress,
                uploadStatus = uiState.uploadStatus,
                error = uiState.error,
                onTopicToggle = { topicId -> viewModel.toggleTopic(selectedSubjectId!!, topicId) }
            )
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Student Planner", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddSubjectDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Subject", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                )
            },
            containerColor = Color.Black,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddSubjectDialog = true },
                    containerColor = Color(0xFF9C27B0),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Subject")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                if (syllabusList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No subjects added. Click + to start!", color = Color.Gray)
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(syllabusList) { syllabus ->
                        SubjectCard(
                            syllabus = syllabus,
                            onClick = { selectedSubjectId = syllabus.id },
                            onDelete = { viewModel.deleteSubject(syllabus.id) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    if (showAddSubjectDialog) {
        AlertDialog(
            onDismissRequest = { showAddSubjectDialog = false },
            title = { Text("Add New Subject") },
            text = {
                OutlinedTextField(
                    value = newSubjectName,
                    onValueChange = { newSubjectName = it },
                    label = { Text("Subject Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newSubjectName.isNotBlank()) {
                        viewModel.addSubject(newSubjectName)
                        newSubjectName = ""
                        showAddSubjectDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SubjectCard(syllabus: SyllabusData, onClick: () -> Unit, onDelete: () -> Unit) {
    val totalTopics = syllabus.units.flatMap { it.topics }.size
    val completedTopics = syllabus.units.flatMap { it.topics }.count { it.isCompleted }
    val progress = if (totalTopics > 0) completedTopics.toFloat() / totalTopics else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(syllabus.subject, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Color.Cyan,
                    trackColor = Color.Gray
                )
                Text(
                    "${(progress * 100).toInt()}% Complete ($completedTopics/$totalTopics Topics)",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailView(
    syllabus: SyllabusData,
    onBack: () -> Unit,
    onImageUploadClick: () -> Unit,
    onPdfUploadClick: () -> Unit,
    isLoading: Boolean,
    uploadProgress: Float,
    uploadStatus: String,
    error: String?,
    onTopicToggle: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(syllabus.subject, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (syllabus.units.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No syllabus data found.", color = Color.Gray)
                        
                        Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = onImageUploadClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload Image")
                            }
                            Button(
                                onClick = onPdfUploadClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload PDF")
                            }
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onImageUploadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Img", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onPdfUploadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontSize = 12.sp)
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(syllabus.units) { unit ->
                        UnitCard(unit = unit, onTopicToggle = onTopicToggle)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            if (isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { uploadProgress },
                                modifier = Modifier.size(24.dp),
                                color = Color.Cyan,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = uploadStatus,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { uploadProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color.Cyan,
                            trackColor = Color.Gray
                        )
                        Text(
                            text = "${(uploadProgress * 100).toInt()}%",
                            modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                            color = Color.Cyan,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            if (error != null) {
                Text(error, color = Color.Red, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
fun UnitCard(unit: SyllabusUnit, onTopicToggle: (String) -> Unit) {
    var isExpanded by remember { mutableStateOf(true) }
    val completedCount = unit.topics.count { it.isCompleted }
    val totalCount = unit.topics.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { isExpanded = !isExpanded }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(unit.unit_title, color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Color.Cyan,
                        trackColor = Color.Gray
                    )
                    Text(
                        "${(progress * 100).toInt()}% Done ($completedCount/$totalCount)",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    unit.topics.forEach { topic ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = topic.isCompleted,
                                onCheckedChange = { onTopicToggle(topic.id) },
                                colors = CheckboxDefaults.colors(checkedColor = Color.Cyan)
                            )
                            Text(
                                text = topic.title,
                                color = if (topic.isCompleted) Color.Gray else Color.White,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
