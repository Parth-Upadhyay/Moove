package com.example.kinetiq.ui.dashboard

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kinetiq.models.ClinicalPrescription
import com.example.kinetiq.models.Patient
import com.example.kinetiq.models.PrescribedExercise
import com.example.kinetiq.models.SessionResult
import com.example.kinetiq.ui.theme.*
import com.example.kinetiq.viewmodel.AnalyticsViewModel
import com.example.kinetiq.viewmodel.DoctorDashboardViewModel
import com.example.kinetiq.viewmodel.ExerciseTrendPoint
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(
    patientId: String,
    onBack: () -> Unit,
    viewModel: DoctorDashboardViewModel = hiltViewModel(),
    analyticsViewModel: AnalyticsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val patient = state.patients.find { it.id == patientId }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Profile", "Analysis", "Rx")
    val context = LocalContext.current

    LaunchedEffect(patientId) {
        viewModel.fetchPatientSessions(patientId)
        analyticsViewModel.loadOverallSummary(patientId, patient)
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        containerColor = MooveBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(patient?.displayName ?: "Patient Detail", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
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
        Column(modifier = Modifier.padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MooveBackground,
                contentColor = MoovePrimary,
                edgePadding = 24.dp,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MoovePrimary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Text(
                                title, 
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedTab == index) MoovePrimary else MooveOnSurfaceVariant,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                            ) 
                        }
                    )
                }
            }

            if (patient == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MoovePrimary)
                }
            } else {
                when (selectedTab) {
                    0 -> OverviewTab(patient, state.selectedPatientSessions, analyticsViewModel)
                    1 -> NotesTab(patient, viewModel)
                    2 -> AnalysisTab(patient, state.selectedPatientSessions, analyticsViewModel)
                    3 -> PrescriptionTab(patient, viewModel)
                }
            }
        }
    }
}

@Composable
fun OverviewTab(patient: Patient, sessions: List<SessionResult>, analyticsViewModel: AnalyticsViewModel) {
    val analyticsState by analyticsViewModel.uiState.collectAsState()
    var showAllSessions by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Recovery Status", style = MaterialTheme.typography.titleLarge, color = MooveOnBackground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        
        MooveCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Overall Recovery Score", style = MaterialTheme.typography.labelLarge, color = MooveOnSurfaceVariant)
                Text(
                    "${analyticsState.overallSummary.weightedRecoveryPercentage.toInt()}%",
                    style = MaterialTheme.typography.displayMedium,
                    color = MoovePrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                MooveProgressBar(progress = analyticsState.overallSummary.weightedRecoveryPercentage.toFloat() / 100f)
            }
        }

        Spacer(Modifier.height(24.dp))
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AdherenceCard("Weekly Adherence", "${(analyticsState.overallSummary.adherenceRate * 100).toInt()}%", Modifier.weight(1f))
            AdherenceCard("Total Sessions", "${analyticsState.overallSummary.totalSessions}", Modifier.weight(1f))
        }

        if (analyticsState.overallSummary.adherenceBreakdown.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            MooveCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Adherence Breakdown (Last Week)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MooveOnBackground)
                    Spacer(Modifier.height(8.dp))
                    analyticsState.overallSummary.adherenceBreakdown.forEach { entry ->
                        val exerciseId = entry.key
                        val stats = entry.value
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                exerciseId.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }, 
                                style = MaterialTheme.typography.bodySmall, 
                                color = MooveOnSurfaceVariant
                            )
                            Text("${stats.first}/${stats.second} sets", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MoovePrimary)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Recent Sessions", style = MaterialTheme.typography.titleMedium, color = MooveOnBackground, fontWeight = FontWeight.Bold)
            if (sessions.size > 5) {
                TextButton(onClick = { showAllSessions = !showAllSessions }) {
                    Text(if (showAllSessions) "Show Less" else "View All", color = MoovePrimary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        
        val sessionsToDisplay = if (showAllSessions) sessions else sessions.take(5)
        sessionsToDisplay.forEach { session ->
            SessionReportItem(session, sessions)
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AdherenceCard(label: String, value: String, modifier: Modifier = Modifier) {
    MooveCard(modifier = modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MooveOnSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MooveOnBackground)
        }
    }
}

@Composable
fun SessionReportItem(session: SessionResult, allSessions: List<SessionResult>) {
    val improvement = calculateImprovement(session, allSessions)
    val maxPain = session.pain_log.mapNotNull { it.level.toIntOrNull() }.maxOrNull() ?: session.pre_session.pain_score

    MooveCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(session.exercise.replace("_", " ").uppercase(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MooveOnBackground)
                Text(session.timestamp_end.take(10), style = MaterialTheme.typography.bodySmall, color = MooveOnSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Peak ROM: ${session.results.peak_rom_degrees.toInt()}°", style = MaterialTheme.typography.bodyMedium, color = MooveOnBackground)
                
                Surface(
                    color = (if (maxPain >= 7) Color(0xFFE57373) else if (maxPain >= 4) Color(0xFFFFB703) else Color(0xFF4CAF50)).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Pain: $maxPain/10",
                        color = if (maxPain >= 7) Color(0xFFE57373) else if (maxPain >= 4) Color(0xFFFFB703) else Color(0xFF4CAF50),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (improvement != null) {
                    Surface(
                        color = (if (improvement >= 0) Color(0xFF4CAF50) else Color(0xFFE57373)).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${if (improvement >= 0) "+" else ""}${improvement.toInt()}%",
                            color = if (improvement >= 0) Color(0xFF4CAF50) else Color(0xFFE57373),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (session.journal_entry?.text?.isNotEmpty() == true) {
                Spacer(Modifier.height(8.dp))
                Text("Note: ${session.journal_entry.text}", style = MaterialTheme.typography.bodySmall, color = MooveOnSurfaceVariant)
            }
        }
    }
}

fun calculateImprovement(current: SessionResult, allSessions: List<SessionResult>): Double? {
    val exerciseSessions = allSessions.filter { it.exercise == current.exercise }
        .sortedBy { it.timestamp_end }
    val currentIndex = exerciseSessions.indexOfFirst { it.session_id == current.session_id }
    if (currentIndex <= 0) return null
    
    val previous = exerciseSessions[currentIndex - 1]
    val prevAbility = previous.results.peak_rom_degrees
    if (prevAbility == 0.0) return null
    
    return ((current.results.peak_rom_degrees - prevAbility) / prevAbility) * 100.0
}

@Composable
fun NotesTab(patient: Patient, viewModel: DoctorDashboardViewModel) {
    var age by remember { mutableStateOf(patient.age.toString()) }
    var sex by remember { mutableStateOf(patient.sex) }
    var injuryType by remember { mutableStateOf(patient.injuryType) }
    var medicalNotes by remember { mutableStateOf(patient.medicalNotes) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Clinical Profile", style = MaterialTheme.typography.titleLarge, color = MooveOnBackground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        MooveTextField(
            value = injuryType,
            onValueChange = { injuryType = it },
            label = "Injury Label"
        )

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MooveTextField(
                value = age,
                onValueChange = { if (it.all { c -> c.isDigit() }) age = it },
                label = "Age",
                modifier = Modifier.weight(1f)
            )
            MooveTextField(
                value = sex,
                onValueChange = { sex = it },
                label = "Sex",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))

        MooveTextField(
            value = medicalNotes,
            onValueChange = { medicalNotes = it },
            label = "Medical History & Notes",
            singleLine = false,
            modifier = Modifier.height(200.dp)
        )

        Spacer(Modifier.height(32.dp))
        MoovePrimaryButton(
            onClick = {
                viewModel.updatePatientNotes(patient.id, age.toIntOrNull() ?: 0, sex, medicalNotes, injuryType)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Profile & Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AnalysisTab(patient: Patient, sessions: List<SessionResult>, analyticsViewModel: AnalyticsViewModel) {
    val analyticsState by analyticsViewModel.uiState.collectAsState()
    
    val allExercises = listOf(
        "pendulum", "wall_climb", "external_rotation", "crossover",
        "lateral_arm_raise", "forward_arm_raise", "hitchhiker"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Improvement Analysis", style = MaterialTheme.typography.titleLarge, color = MooveOnBackground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        MooveCard(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("OVR Score", style = MaterialTheme.typography.labelSmall, color = MooveOnSurfaceVariant)
                    Text("${analyticsState.overallSummary.weightedRecoveryPercentage.toInt()}%", style = MaterialTheme.typography.titleLarge, color = MoovePrimary, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sessions", style = MaterialTheme.typography.labelSmall, color = MooveOnSurfaceVariant)
                    Text("${analyticsState.overallSummary.totalSessions}", style = MaterialTheme.typography.titleLarge, color = MooveOnBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("Exercise Specific Trends", style = MaterialTheme.typography.titleMedium, color = MooveOnBackground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        allExercises.forEach { exercise ->
            ExerciseAnalysisSection(exercise, patient.id, analyticsViewModel)
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ExerciseAnalysisSection(exercise: String, patientId: String, viewModel: AnalyticsViewModel) {
    var expanded by remember { mutableStateOf(false) }

    MooveCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.replace("_", " ").uppercase(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MooveOnBackground)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MoovePrimary
                    )
                }
            }
            
            if (expanded) {
                val state by viewModel.uiState.collectAsState()
                val isDataLoading = state.loadingExercises.contains(exercise)
                val exerciseData = state.filteredTrendData[exercise] ?: emptyList()
                val comparison = state.comparisons[exercise] ?: ""
                
                LaunchedEffect(expanded) {
                    if (expanded && !state.allTrendData.containsKey(exercise)) {
                        viewModel.loadExerciseProgress(patientId, exercise)
                    }
                }

                if (isDataLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = MoovePrimary)
                } else if (exerciseData.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    PatientProgressChart(
                        data = exerciseData,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = MooveBackground.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    ) {
                        Text(comparison, style = MaterialTheme.typography.bodySmall, color = MooveOnBackground, fontWeight = FontWeight.Medium, modifier = Modifier.padding(12.dp))
                    }
                } else {
                    Text("No trend data available", style = MaterialTheme.typography.bodySmall, color = MooveOnSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
fun PatientProgressChart(data: List<ExerciseTrendPoint>, modifier: Modifier = Modifier) {
    val primaryColor = MoovePrimary
    val painColor = Color(0xFFE57373)
    
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            ChartLegendItem("Peak ROM (°)", primaryColor)
            Spacer(Modifier.width(16.dp))
            ChartLegendItem("Pain", painColor)
        }
        Spacer(Modifier.height(16.dp))
        
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (data.isEmpty()) return@Canvas
            val width = size.width
            val height = size.height
            
            val maxMetric = 180f
            
            if (data.size > 1) {
                val metricPath = Path()
                data.forEachIndexed { index, point ->
                    val x = (index.toFloat() / (data.size - 1)) * width
                    val y = height - (point.optimality.toFloat().coerceIn(0f, 180f) / maxMetric) * height
                    if (index == 0) metricPath.moveTo(x, y) else metricPath.lineTo(x, y)
                    drawCircle(MooveBackground, radius = 4.dp.toPx(), center = Offset(x, y))
                    drawCircle(primaryColor, radius = 2.dp.toPx(), center = Offset(x, y))
                }
                drawPath(metricPath, primaryColor, style = Stroke(width = 2.dp.toPx()))

                val painPath = Path()
                data.forEachIndexed { index, point ->
                    val x = (index.toFloat() / (data.size - 1)) * width
                    val y = height - (point.pain.toFloat().coerceIn(0f, 10f) / 10f) * height
                    if (index == 0) painPath.moveTo(x, y) else painPath.lineTo(x, y)
                    drawCircle(MooveBackground, radius = 4.dp.toPx(), center = Offset(x, y))
                    drawCircle(painColor, radius = 2.dp.toPx(), center = Offset(x, y))
                }
                drawPath(painPath, painColor, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
fun ChartLegendItem(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MooveOnSurfaceVariant)
    }
}

@Composable
fun PrescriptionTab(patient: Patient, viewModel: DoctorDashboardViewModel) {
    val availableExercises = listOf(
        "pendulum" to "Pendulum",
        "wall_climb" to "Wall Climb",
        "external_rotation" to "External Rotation",
        "crossover" to "Crossover",
        "lateral_arm_raise" to "Lateral Arm Raise",
        "forward_arm_raise" to "Forward Arm Raise",
        "hitchhiker" to "Hitchhiker"
    )

    var prescribedList by remember {
        mutableStateOf(
            availableExercises.map { (id, name) ->
                val existing = patient.clinicalPrescription?.exercises?.find { it.exerciseId == id }
                PrescribedExercise(
                    exerciseId = id,
                    exerciseName = name,
                    sets = existing?.sets ?: 0,
                    reps = existing?.reps ?: 0,
                    isActive = existing?.isActive ?: false
                )
            }
        )
    }

    var instructions by remember { mutableStateOf(patient.clinicalPrescription?.notes ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Exercise Prescription", style = MaterialTheme.typography.titleLarge, color = MooveOnBackground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        prescribedList.forEachIndexed { index, exercise ->
            MooveCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = exercise.isActive,
                            onCheckedChange = { checked ->
                                prescribedList = prescribedList.toMutableList().apply {
                                    this[index] = exercise.copy(isActive = checked)
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = MoovePrimary)
                        )
                        Text(exercise.exerciseName, style = MaterialTheme.typography.bodyLarge, color = MooveOnBackground, fontWeight = FontWeight.Bold)
                    }

                    if (exercise.isActive) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MooveTextField(
                                value = if (exercise.sets == 0) "" else exercise.sets.toString(),
                                onValueChange = {
                                    val newValue = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                                    prescribedList = prescribedList.toMutableList().apply {
                                        this[index] = exercise.copy(sets = newValue)
                                    }
                                },
                                label = "Sets",
                                modifier = Modifier.weight(1f)
                            )
                            MooveTextField(
                                value = if (exercise.reps == 0) "" else exercise.reps.toString(),
                                onValueChange = {
                                    val newValue = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                                    prescribedList = prescribedList.toMutableList().apply {
                                        this[index] = exercise.copy(reps = newValue)
                                    }
                                },
                                label = "Reps",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(16.dp))
        MooveTextField(
            value = instructions,
            onValueChange = { instructions = it },
            label = "General Instructions",
            singleLine = false,
            modifier = Modifier.height(120.dp)
        )

        Spacer(Modifier.height(32.dp))
        MoovePrimaryButton(
            onClick = {
                viewModel.updatePrescription(
                    patient.id,
                    ClinicalPrescription(
                        exercises = prescribedList,
                        notes = instructions
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update Prescription", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(32.dp))
    }
}
