package com.example.kinetiq.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kinetiq.viewmodel.AnalyticsViewModel
import com.example.kinetiq.viewmodel.ExerciseTrendPoint
import com.example.kinetiq.viewmodel.TimeFilter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomDashboardScreen(
    patientId: String,
    exerciseType: String,
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isOptimality = exerciseType.lowercase() == "crossover"

    LaunchedEffect(patientId, exerciseType) {
        viewModel.loadExerciseProgress(patientId, exerciseType)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress: ${exerciseType.replace("_", " ").uppercase()}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val isDataLoading = state.loadingExercises.contains(exerciseType)
            val exerciseData = state.filteredTrendData[exerciseType] ?: emptyList()
            val comparison = state.comparisons[exerciseType] ?: ""

            if (isDataLoading && exerciseData.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Text("Error: ${state.error}", color = Color.Red)
            } else if (exerciseData.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No session data available.")
                }
            } else {
                // Time Filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeFilter.entries.forEach { filter ->
                        val label = when(filter) {
                            TimeFilter.LAST_3_DAYS -> "3 Days"
                            TimeFilter.LAST_WEEK -> "1 Week"
                            TimeFilter.LAST_MONTH -> "1 Month"
                            TimeFilter.LAST_3_MONTHS -> "3 Months"
                            TimeFilter.ALL -> "All Time"
                        }
                        FilterChip(
                            selected = state.selectedFilter == filter,
                            onClick = { viewModel.setTimeFilter(filter) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    if (isOptimality) "Optimality vs Pain Trends" else "ROM vs Pain Trends",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(Modifier.height(16.dp))
                
                ProgressChart(
                    data = exerciseData,
                    isOptimality = isOptimality,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
                
                Spacer(Modifier.height(24.dp))
                
                ComparisonSummary(comparison)
            }
        }
    }
}

@Composable
fun ProgressChart(data: List<ExerciseTrendPoint>, isOptimality: Boolean, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val painColor = Color.Red
    
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ChartLegend(if (isOptimality) "Optimality (%)" else "ROM (°)", primaryColor)
            ChartLegend("Pain Level", painColor)
        }
        Spacer(Modifier.height(8.dp))
        
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val width = size.width
            val height = size.height
            
            // Dynamic scale for Y axis
            val maxMetric = data.maxOf { it.optimality }.coerceAtLeast(if (isOptimality) 100.0 else 180.0).toFloat()
            
            // Draw Metric (Optimality or ROM)
            val metricPath = Path()
            data.forEachIndexed { index, point ->
                val x = if (data.size > 1) (index.toFloat() / (data.size - 1)) * width else width / 2
                val y = height - (point.optimality.toFloat() / maxMetric) * height
                if (index == 0) metricPath.moveTo(x, y) else metricPath.lineTo(x, y)
                drawCircle(primaryColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
            drawPath(metricPath, primaryColor, style = Stroke(width = 2.dp.toPx()))

            // Draw Pain (0-10 scale)
            val painPath = Path()
            data.forEachIndexed { index, point ->
                val x = if (data.size > 1) (index.toFloat() / (data.size - 1)) * width else width / 2
                val y = height - (point.pain.toFloat() / 10f) * height
                if (index == 0) painPath.moveTo(x, y) else painPath.lineTo(x, y)
                drawCircle(painColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
            drawPath(painPath, painColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
fun ChartLegend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ComparisonSummary(summary: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Insights", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(summary, fontWeight = FontWeight.SemiBold)
        }
    }
}
