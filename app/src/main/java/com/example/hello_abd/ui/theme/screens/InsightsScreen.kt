package com.example.hello_abd.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hello_abd.viewmodel.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onBack: () -> Unit, viewModel: HistoryViewModel = viewModel()) {
    val uiState = viewModel.uiState.value

    LaunchedEffect(Unit) {
        viewModel.loadChatHistory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jarvis Intelligence Insights", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Cyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117),
                    titleContentColor = Color.Cyan
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0D1117), Color(0xFF161B22))
                    )
                )
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Cyan
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            "SYSTEM USAGE ANALYSIS",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // Main Stats Row
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SmallInsightCard(
                                modifier = Modifier.weight(1f),
                                title = "Interactions",
                                value = "${uiState.insights.totalInteractions}",
                                icon = Icons.Default.ChatBubbleOutline,
                                color = Color.Cyan
                            )
                            SmallInsightCard(
                                modifier = Modifier.weight(1f),
                                title = "AI Processing",
                                value = "${uiState.insights.aiRatio}%",
                                icon = Icons.Default.Psychology,
                                color = Color(0xFFBB86FC)
                            )
                        }
                    }

                    item {
                        InsightCard(
                            title = "Primary Capability",
                            value = uiState.insights.topIntent,
                            subtitle = "Most frequently used automation",
                            icon = Icons.Default.ElectricBolt,
                            color = Color(0xFF03DAC6)
                        )
                    }

                    item {
                        Text(
                            "BEHAVIORAL PATTERNS",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SmallInsightCard(
                                modifier = Modifier.weight(1f),
                                title = "Peak Hour",
                                value = uiState.insights.peakUsageHour,
                                icon = Icons.Default.AccessTime,
                                color = Color(0xFFF44336)
                            )
                            SmallInsightCard(
                                modifier = Modifier.weight(1f),
                                title = "Active Day",
                                value = uiState.insights.mostActiveDay.take(3),
                                icon = Icons.Default.CalendarToday,
                                color = Color(0xFF2196F3)
                            )
                        }
                    }

                    item {
                        InsightCard(
                            title = "Command Complexity",
                            value = String.format("%.1f Words", uiState.insights.avgWordCount),
                            subtitle = "Average length per command",
                            icon = Icons.Default.ShortText,
                            color = Color(0xFFFFC107)
                        )
                    }

                    item {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Insights, null, tint = Color.Cyan, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Neural Adaptation Report", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Your interaction model shows a strong preference for ${if(uiState.insights.aiRatio > 50) "Natural Language Intelligence" else "Direct System Automation"}. Jarvis has identified your most active period as ${uiState.insights.peakUsageHour} on ${uiState.insights.mostActiveDay}s. Continued usage will refine the contextual response engine.",
                                    color = Color.LightGray,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun InsightCard(title: String, value: String, subtitle: String, icon: ImageVector, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D))
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.DarkGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
fun SmallInsightCard(modifier: Modifier = Modifier, title: String, value: String, icon: ImageVector, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF21262D))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}
