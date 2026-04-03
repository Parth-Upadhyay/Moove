package com.example.kinetiq.ui.dashboard

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kinetiq.models.ClinicalPrescription
import com.example.kinetiq.models.Patient
import com.example.kinetiq.models.PrescribedExercise
import com.example.kinetiq.models.SessionResult
import com.example.kinetiq.viewmodel.AnalyticsViewModel
import com.example.kinetiq.viewmodel.DoctorDashboardViewModel
import com.example.kinetiq.viewmodel.ExerciseTrendPoint

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
    val tabs = listOf("Overview", "Notes", "Analysis", "Prescription")
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
        topBar = {
            TopAppBar(
                title = { Text(patient?.displayName ?: "Patient Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (patient == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
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
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Recovery Progress", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Overall Recovery Score", style = MaterialTheme.typography.labelLarge)
                Text(
                    "${analyticsState.overallSummary.weightedRecoveryPercentage.toInt()}%",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Adherence", style = MaterialTheme.typography.titleMedium)
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdherenceCard("Recent Adherence", "${(analyticsState.overallSummary.adherenceRate * 100).toInt()}%", Modifier.weight(1f))
            AdherenceCard("Total Sessions", "${analyticsState.overallSummary.totalSessions}", Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))
        Text("Post-Session Reports", style = MaterialTheme.typography.titleMedium)
        
        sessions.take(10).forEach { session ->
            SessionReportItem(session)
        }
    }
}

@Composable
fun AdherenceCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SessionReportItem(session: SessionResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(session.exercise, fontWeight = FontWeight.Bold)
                Text(session.timestamp_end.take(10), style = MaterialTheme.typography.bodySmall)
            }
            Text("Peak ROM: ${session.results.peak_rom_degrees}°", style = MaterialTheme.typography.bodyMedium)
            if (session.journal_entry?.text?.isNotEmpty() == true) {
                Text("Patient Note: ${session.journal_entry.text}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Clinical Profile", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = injuryType,
            onValueChange = { injuryType = it },
            label = { Text("Injury Label") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = age,
                onValueChange = { if (it.all { c -> c.isDigit() }) age = it },
                label = { Text("Age") },
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            OutlinedTextField(
                value = sex,
                onValueChange = { sex = it },
                label = { Text("Sex") },
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = medicalNotes,
            onValueChange = { medicalNotes = it },
            label = { Text("Medical History & Notes") },
            modifier = Modifier.fillMaxWidth().height(200.dp),
            maxLines = 10
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                viewModel.updatePatientNotes(patient.id, age.toIntOrNull() ?: 0, sex, medicalNotes, injuryType)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Profile & Notes")
        }
    }
}

@Composable
fun AnalysisTab(patient: Patient, sessions: List<SessionResult>, analyticsViewModel: AnalyticsViewModel) {
    val analyticsState by analyticsViewModel.uiState.collectAsState()
    val exercises = sessions.map { it.exercise }.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Improvement Analysis", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("OVR Score", style = MaterialTheme.typography.labelMedium)
                    Text("${analyticsState.overallSummary.weightedRecoveryPercentage.toInt()}%", style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Sessions", style = MaterialTheme.typography.labelMedium)
                    Text("${analyticsState.overallSummary.totalSessions}", style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Exercise Specific Trends", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        exercises.forEach { exercise ->
            ExerciseAnalysisSection(exercise, patient.id, analyticsViewModel)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ExerciseAnalysisSection(exercise: String, patientId: String, viewModel: AnalyticsViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(exercise.replace("_", " ").uppercase(), fontWeight = FontWeight.Bold)
                IconButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "▲" else "▼")
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (exerciseData.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    PatientProgressChart(
                        data = exerciseData,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(comparison, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("No trend data available", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
fun PatientProgressChart(data: List<ExerciseTrendPoint>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val painColor = Color.Red
    
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ChartLegendItem("ROM (°)", primaryColor)
            ChartLegendItem("Pain", painColor)
        }
        Spacer(Modifier.height(8.dp))
        
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (data.isEmpty()) return@Canvas
            val width = size.width
            val height = size.height
            
            val maxMetric = data.maxOf { it.optimality }.coerceAtLeast(180.0).toFloat()
            
            val metricPath = Path()
            data.forEachIndexed { index, point ->
                val x = if (data.size > 1) (index.toFloat() / (data.size - 1)) * width else width / 2
                val y = height - (point.optimality.toFloat() / maxMetric) * height
                if (index == 0) metricPath.moveTo(x, y) else metricPath.lineTo(x, y)
                drawCircle(primaryColor, radius = 3.dp.toPx(), center = Offset(x, y))
            }
            drawPath(metricPath, primaryColor, style = Stroke(width = 2.dp.toPx()))

            val painPath = Path()
            data.forEachIndexed { index, point ->
                val x = if (data.size > 1) (index.toFloat() / (data.size - 1)) * width else width / 2
                val y = height - (point.pain.toFloat() / 10f) * height
                if (index == 0) painPath.moveTo(x, y) else painPath.lineTo(x, y)
                drawCircle(painColor, radius = 3.dp.toPx(), center = Offset(x, y))
            }
            drawPath(painPath, painColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
fun ChartLegendItem(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PrescriptionTab(patient: Patient, viewModel: DoctorDashboardViewModel) {
    val availableExercises = listOf(
        "pendulum" to "Pendulum",
        "wall_climb" to "Wall Climb",
        "external_rotation" to "External Rotation",
        "crossover" to "Crossover"
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Exercise Prescription", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        prescribedList.forEachIndexed { index, exercise ->
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
                    }
                )
                Text(exercise.exerciseName, modifier = Modifier.weight(1f))
            }

            if (exercise.isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = if (exercise.sets == 0) "" else exercise.sets.toString(),
                        onValueChange = {
                            val newValue = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                            prescribedList = prescribedList.toMutableList().apply {
                                this[index] = exercise.copy(sets = newValue)
                            }
                        },
                        label = { Text("Sets") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = if (exercise.reps == 0) "" else exercise.reps.toString(),
                        onValueChange = {
                            val newValue = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                            prescribedList = prescribedList.toMutableList().apply {
                                this[index] = exercise.copy(reps = newValue)
                            }
                        },
                        label = { Text("Reps") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        OutlinedTextField(
            value = instructions,
            onValueChange = { instructions = it },
            label = { Text("General Instructions") },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            maxLines = 4
        )

        Spacer(Modifier.height(24.dp))
        Button(
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
            Text("Update Prescription")
        }
    }
}
