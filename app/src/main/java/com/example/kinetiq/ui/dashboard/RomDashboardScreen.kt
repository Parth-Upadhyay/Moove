package com.example.kinetiq.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kinetiq.ui.theme.*
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
    val isOptimality = true 

    LaunchedEffect(patientId, exerciseType) {
        viewModel.loadExerciseProgress(patientId, exerciseType)
    }

    Scaffold(
        containerColor = MooveBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(exerciseType.replace("_", " ").uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MooveOnBackground)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MooveBackground,
                    titleContentColor = MooveOnBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            val isDataLoading = state.loadingExercises.contains(exerciseType)
            val exerciseData = state.filteredTrendData[exerciseType] ?: emptyList()
            val comparison = state.comparisons[exerciseType] ?: ""

            if (isDataLoading && exerciseData.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MoovePrimary)
                }
            } else if (state.error != null) {
                Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            } else if (exerciseData.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No session data available.", color = MooveOnSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(16.dp))
                
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
                            TimeFilter.ALL -> "All"
                        }
                        val isSelected = state.selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setTimeFilter(filter) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MoovePrimary,
                                selectedLabelColor = MooveOnPrimary,
                                containerColor = MooveSurface,
                                labelColor = MooveOnSurfaceVariant
                            ),
                            border = null,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                MooveCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Recovery Performance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MooveOnBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Ability Score vs Pain Level",
                        style = MaterialTheme.typography.bodySmall,
                        color = MooveOnSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    
                    ProgressChart(
                        data = exerciseData,
                        isOptimality = isOptimality,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        ChartLegend("Ability (%)", MoovePrimary)
                        Spacer(Modifier.width(24.dp))
                        ChartLegend("Pain (0-10)", Color(0xFFE57373))
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                ComparisonSummary(comparison)
                
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ProgressChart(data: List<ExerciseTrendPoint>, isOptimality: Boolean, modifier: Modifier = Modifier) {
    val primaryColor = MoovePrimary
    val painColor = Color(0xFFE57373)
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        val maxAbility = 100f
        val maxPain = 10f
        
        fun normalize(value: Double, max: Float): Float {
            return (value.toFloat().coerceIn(0f, max) / max) * height
        }

        if (data.size > 1) {
            // Draw Ability Score Path
            val metricPath = Path()
            data.forEachIndexed { index, point ->
                val x = (index.toFloat() / (data.size - 1)) * width
                val y = height - normalize(point.optimality, maxAbility)
                if (index == 0) metricPath.moveTo(x, y) else metricPath.lineTo(x, y)
            }
            drawPath(metricPath, primaryColor, style = Stroke(width = 3.dp.toPx()))

            // Draw Pain Path
            val painPath = Path()
            data.forEachIndexed { index, point ->
                val x = (index.toFloat() / (data.size - 1)) * width
                val y = height - normalize(point.pain, maxPain)
                if (index == 0) painPath.moveTo(x, y) else painPath.lineTo(x, y)
            }
            drawPath(painPath, painColor, style = Stroke(width = 3.dp.toPx()))
            
            // Draw data points
            data.forEachIndexed { index, point ->
                val x = (index.toFloat() / (data.size - 1)) * width
                
                val yMetric = height - normalize(point.optimality, maxAbility)
                drawCircle(MooveBackground, radius = 5.dp.toPx(), center = Offset(x, yMetric))
                drawCircle(primaryColor, radius = 3.dp.toPx(), center = Offset(x, yMetric))
                
                val yPain = height - normalize(point.pain, maxPain)
                drawCircle(MooveBackground, radius = 5.dp.toPx(), center = Offset(x, yPain))
                drawCircle(painColor, radius = 3.dp.toPx(), center = Offset(x, yPain))
            }
        }
    }
}

@Composable
fun ChartLegend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MooveOnSurfaceVariant)
    }
}

@Composable
fun ComparisonSummary(summary: String) {
    MooveCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                color = MoovePrimary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MoovePrimary)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Clinical Insights", style = MaterialTheme.typography.titleSmall, color = MooveOnBackground, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MooveOnBackground, fontWeight = FontWeight.Medium)
            }
        }
    }
}
