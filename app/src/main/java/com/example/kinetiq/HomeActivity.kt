package com.example.kinetiq

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kinetiq.models.*
import com.example.kinetiq.ui.auth.AuthScreen
import com.example.kinetiq.ui.components.GridCalendar
import com.example.kinetiq.ui.dashboard.*
import com.example.kinetiq.viewmodel.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.tasks.await
import java.util.*

data class ExerciseItem(val name: String, val id: String, val icon: ImageVector)

@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            KinetiqTheme {
                val authViewModel: AuthViewModel = hiltViewModel()
                val authState by authViewModel.uiState.collectAsState()
                
                var currentUserRole by remember { mutableStateOf<UserRole?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var selectedPatientId by remember { mutableStateOf<String?>(null) }
                var showRomAnalytics by remember { mutableStateOf<Pair<String, String>?>(null) }
                var activeChatPartner by remember { mutableStateOf<Pair<String, String>?>(null) }

                // Global Back Navigation Handling
                BackHandler(enabled = activeChatPartner != null || showRomAnalytics != null || selectedPatientId != null) {
                    when {
                        activeChatPartner != null -> activeChatPartner = null
                        showRomAnalytics != null -> showRomAnalytics = null
                        selectedPatientId != null -> selectedPatientId = null
                    }
                }

                LaunchedEffect(Unit) {
                    val auth = FirebaseAuth.getInstance()
                    val db = FirebaseFirestore.getInstance()
                    
                    val user = auth.currentUser
                    if (user != null) {
                        try {
                            val doc = db.collection("users").document(user.uid).get().await()
                            val roleStr = doc.getString("role")?.uppercase()
                            if (roleStr != null) {
                                currentUserRole = try { UserRole.valueOf(roleStr) } catch(e: Exception) { UserRole.PATIENT }
                            } else {
                                auth.signOut()
                            }
                        } catch (e: Exception) {
                            Log.e("HomeActivity", "Error fetching role", e)
                            auth.signOut()
                        }
                    }
                    isLoading = false
                }

                LaunchedEffect(authState.isSuccess) {
                    if (authState.isSuccess && authState.userRole != null) {
                        currentUserRole = authState.userRole
                    }
                }

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (currentUserRole == null) {
                    AuthScreen(
                        onAuthSuccess = { role -> currentUserRole = role },
                        viewModel = authViewModel
                    )
                } else {
                    when (currentUserRole!!) {
                        UserRole.DOCTOR -> {
                            when {
                                activeChatPartner != null -> {
                                    ChatScreen(
                                        otherUserId = activeChatPartner!!.first,
                                        otherUserName = activeChatPartner!!.second,
                                        onBack = { activeChatPartner = null }
                                    )
                                }
                                showRomAnalytics != null -> {
                                    RomDashboardScreen(
                                        patientId = showRomAnalytics!!.first,
                                        exerciseType = showRomAnalytics!!.second,
                                        onBack = { showRomAnalytics = null }
                                    )
                                }
                                selectedPatientId != null -> {
                                    PatientDetailScreen(
                                        patientId = selectedPatientId!!,
                                        onBack = { selectedPatientId = null }
                                    )
                                }
                                else -> {
                                    DoctorDashboardContainer(
                                        onLogout = {
                                            FirebaseAuth.getInstance().signOut()
                                            authViewModel.resetState()
                                            currentUserRole = null
                                        },
                                        onPatientClick = { patientId ->
                                            selectedPatientId = patientId
                                        }
                                    )
                                }
                            }
                        }
                        UserRole.PATIENT -> {
                            if (activeChatPartner != null) {
                                ChatScreen(
                                    otherUserId = activeChatPartner!!.first,
                                    otherUserName = activeChatPartner!!.second,
                                    onBack = { activeChatPartner = null }
                                )
                            } else if (showRomAnalytics != null) {
                                RomDashboardScreen(
                                    patientId = showRomAnalytics!!.first,
                                    exerciseType = showRomAnalytics!!.second,
                                    onBack = { showRomAnalytics = null }
                                )
                            } else {
                                PatientDashboard(
                                    onLogout = {
                                        FirebaseAuth.getInstance().signOut()
                                        authViewModel.resetState()
                                        currentUserRole = null
                                    },
                                    onChatWithDoctor = { doctorId, name ->
                                        activeChatPartner = doctorId to name
                                    },
                                    onViewProgress = { exerciseType ->
                                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                                        if (uid != null) {
                                            showRomAnalytics = uid to exerciseType
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PatientDashboard(
        onLogout: () -> Unit, 
        onChatWithDoctor: (String, String) -> Unit,
        onViewProgress: (String) -> Unit
    ) {
        var selectedTab by remember { mutableIntStateOf(0) }
        var selectedDate by remember { mutableStateOf(Date()) }
        
        val connectionViewModel: ConnectionViewModel = hiltViewModel()
        val analyticsViewModel: AnalyticsViewModel = hiltViewModel()
        val appointmentViewModel: AppointmentViewModel = hiltViewModel()
        
        val connectionState by connectionViewModel.uiState.collectAsState()
        val analyticsState by analyticsViewModel.uiState.collectAsState()
        val appointmentState by appointmentViewModel.uiState.collectAsState()
        
        var doctorEmail by remember { mutableStateOf("") }
        var showRequestDialog by remember { mutableStateOf(false) }
        var showAppointmentDialog by remember { mutableStateOf(false) }
        
        var assignedDoctorId by remember { mutableStateOf<String?>(null) }
        var assignedDoctorName by remember { mutableStateOf("Doctor") }
        var patientData by remember { mutableStateOf<Patient?>(null) }

        LaunchedEffect(Unit) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
            val db = FirebaseFirestore.getInstance()
            val patientDoc = db.collection("patients").document(uid).get().await()
            val p = patientDoc.toObject(Patient::class.java)
            patientData = p
            val docId = p?.doctorId
            if (docId != null && docId.isNotEmpty()) {
                assignedDoctorId = docId
                val doctorUserDoc = db.collection("users").document(docId).get().await()
                assignedDoctorName = doctorUserDoc.getString("displayName")?.ifEmpty { "Doctor" } ?: "Doctor"
            }
            analyticsViewModel.loadSessionsForDate(uid, selectedDate)
            analyticsViewModel.loadOverallSummary(uid)
            appointmentViewModel.loadAppointmentsForPatient()
        }

        LaunchedEffect(selectedDate) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
            analyticsViewModel.loadSessionsForDate(uid, selectedDate)
            if (assignedDoctorId != null) {
                appointmentViewModel.loadBookedSlots(assignedDoctorId!!, selectedDate)
            }
        }

        LaunchedEffect(connectionState.successMessage) {
            if (connectionState.successMessage != null) {
                showRequestDialog = false
                connectionViewModel.clearStatusMessages()
            }
        }
        
        LaunchedEffect(appointmentState.successMessage) {
            if (appointmentState.successMessage != null) {
                showAppointmentDialog = false
                appointmentViewModel.clearMessages()
            }
        }

        // Intercept back button to return to first tab if on another tab
        BackHandler(enabled = selectedTab != 0) {
            selectedTab = 0
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Kinetiq Rehab") },
                    actions = {
                        IconButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Search, null) },
                        label = { Text("Progress") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.DateRange, null) },
                        label = { Text("Calendar") }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
                    0 -> PatientHomeContent(
                        assignedDoctorId = assignedDoctorId,
                        assignedDoctorName = assignedDoctorName,
                        onChatWithDoctor = onChatWithDoctor,
                        onConnectClick = { showRequestDialog = true },
                        patientData = patientData
                    )
                    1 -> LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                        item {
                            Text("Your Recovery Journey", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            
                            // Overall Summary Card with Weighted Percentage
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Recovery Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "${analyticsState.overallSummary.weightedRecoveryPercentage.toInt()}%",
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text("Overall Weighted Progress", style = MaterialTheme.typography.bodySmall)
                                    
                                    Spacer(Modifier.height(16.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                                    Spacer(Modifier.height(16.dp))
                                    
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${analyticsState.overallSummary.avgOptimalityImprovement.toInt()}%", fontWeight = FontWeight.Bold)
                                            Text("ROM Imp.", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("-${analyticsState.overallSummary.avgPainReduction.toInt()} pts", fontWeight = FontWeight.Bold, color = Color.Red)
                                            Text("Pain Red.", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${analyticsState.overallSummary.totalSessions}", fontWeight = FontWeight.Bold)
                                            Text("Sessions", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            Text("Exercise Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Select an exercise to view detailed trends.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                        }
                        
                        val exercises = listOf(
                            ExerciseItem("Pendulum", "pendulum", Icons.Default.Refresh),
                            ExerciseItem("Wall Climb", "wall_climb", Icons.Default.KeyboardArrowUp),
                            ExerciseItem("External Rotation", "external_rotation", Icons.Default.PlayArrow),
                            ExerciseItem("Crossover", "crossover", Icons.Default.Close)
                        )

                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.height(300.dp) // Fixed height within LazyColumn item
                            ) {
                                items(exercises) { exercise ->
                                    ExerciseCard(exercise) {
                                        onViewProgress(exercise.id)
                                    }
                                }
                            }
                        }
                    }
                    2 -> Column(Modifier.fillMaxSize()) {
                        GridCalendar(
                            selectedDate = selectedDate, 
                            onDateSelected = { selectedDate = it },
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Appointments",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (assignedDoctorId != null) {
                                TextButton(onClick = { 
                                    appointmentViewModel.loadBookedSlots(assignedDoctorId!!, selectedDate)
                                    showAppointmentDialog = true 
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Text("Request")
                                }
                            }
                        }
                        
                        val dailyAppointments = appointmentState.appointments.filter { 
                            val cal1 = Calendar.getInstance().apply { time = it.date }
                            val cal2 = Calendar.getInstance().apply { time = selectedDate }
                            cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                        }

                        if (dailyAppointments.isEmpty()) {
                            Text(
                                "No appointments scheduled for this day.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                items(dailyAppointments) { appt ->
                                    AppointmentCard(appt)
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }

                        HorizontalDivider()

                        Text(
                            "Sessions for ${java.text.SimpleDateFormat("MMM dd, yyyy").format(selectedDate)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        if (analyticsState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else if (analyticsState.dailySessions.isEmpty()) {
                            Text(
                                "No sessions recorded for this day.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                items(analyticsState.dailySessions) { session ->
                                    SessionHistoryCard(session)
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showRequestDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showRequestDialog = false 
                    connectionViewModel.clearStatusMessages()
                },
                title = { Text("Connect to Doctor") },
                text = {
                    Column {
                        Text("Ask your doctor for their registered Kinetiq email.")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = doctorEmail,
                            onValueChange = { doctorEmail = it },
                            label = { Text("Doctor's Email") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        connectionState.error?.let { 
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) 
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { connectionViewModel.sendRequest(doctorEmail) },
                        enabled = doctorEmail.isNotBlank() && !connectionState.isLoading
                    ) {
                        if (connectionState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Send Request")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showRequestDialog = false 
                        connectionViewModel.clearStatusMessages()
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAppointmentDialog && assignedDoctorId != null) {
            var note by remember { mutableStateOf("") }
            var selectedTimeSlot by remember { mutableStateOf<String?>(null) }
            val timeSlots = listOf("09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM")
            val availableSlots = timeSlots.filter { it !in appointmentState.bookedSlots }

            AlertDialog(
                onDismissRequest = { showAppointmentDialog = false },
                title = { Text("Request Appointment") },
                text = {
                    Column {
                        Text("Request for ${java.text.SimpleDateFormat("MMM dd, yyyy").format(selectedDate)} with $assignedDoctorName")
                        Spacer(Modifier.height(16.dp))
                        
                        if (availableSlots.isEmpty()) {
                            Text("No slots available for this day. Please choose another date.", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Select Available Time Slot:", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(8.dp))
                            
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.height(120.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(availableSlots) { slot ->
                                    val isSelected = selectedTimeSlot == slot
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                            .clickable { selectedTimeSlot = slot }
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            slot,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Note (Optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (selectedTimeSlot != null) {
                                appointmentViewModel.requestAppointment(assignedDoctorId!!, assignedDoctorName, selectedDate, selectedTimeSlot!!, note)
                            }
                        }, 
                        enabled = !appointmentState.isLoading && selectedTimeSlot != null
                    ) {
                        Text("Send Request")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAppointmentDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    @Composable
    fun SummaryItem(label: String, value: String, color: Color) {
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }

    @Composable
    fun AppointmentCard(
        appointment: Appointment, 
        isDoctorView: Boolean = false,
        showActions: Boolean = false, 
        onStatusUpdate: (AppointmentStatus) -> Unit = {}
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when(appointment.status) {
                    AppointmentStatus.ACCEPTED -> Color(0xFFE8F5E9)
                    AppointmentStatus.PENDING -> Color(0xFFFFF3E0)
                    AppointmentStatus.REJECTED -> Color(0xFFFFEBEE)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            if (isDoctorView) appointment.patientName else "With ${appointment.doctorName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            appointment.timeSlot,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        appointment.status.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = when(appointment.status) {
                            AppointmentStatus.ACCEPTED -> Color(0xFF2E7D32)
                            AppointmentStatus.PENDING -> Color(0xFFEF6C00)
                            else -> Color.Gray
                        }
                    )
                }
                if (appointment.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(appointment.note, style = MaterialTheme.typography.bodySmall)
                }
                
                if (showActions && appointment.status == AppointmentStatus.PENDING) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onStatusUpdate(AppointmentStatus.REJECTED) }) {
                            Text("Reject", color = MaterialTheme.colorScheme.error)
                        }
                        Button(onClick = { onStatusUpdate(AppointmentStatus.ACCEPTED) }) {
                            Text("Accept")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SessionHistoryCard(session: SessionResult) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        session.exercise.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (session.exercise.lowercase() == "crossover") "${session.results.peak_rom_degrees.toInt()}%" else "${session.results.peak_rom_degrees.toInt()}°",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    // Fetch real average pain if possible, otherwise use pre_session
                    val avgPain = if (session.pain_log.isNotEmpty()) {
                        session.pain_log.mapNotNull { it.level.toDoubleOrNull() }.average()
                    } else session.pre_session.pain_score.toDouble()
                    
                    Text("Avg Pain: ${String.format("%.1f", avgPain)}/10", style = MaterialTheme.typography.bodyMedium)
                }

                if (session.form_flags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Form Feedback:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    session.form_flags.forEach { flag ->
                        Text("• ${flag.flag}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (session.journal_entry != null && session.journal_entry.text.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Notes:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(session.journal_entry.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    @Composable
    fun PatientHomeContent(
        assignedDoctorId: String?,
        assignedDoctorName: String,
        onChatWithDoctor: (String, String) -> Unit,
        onConnectClick: () -> Unit,
        patientData: Patient?
    ) {
        val context = LocalContext.current
        val prescribedExercises = patientData?.clinicalPrescription?.exercises?.filter { it.isActive } ?: emptyList()

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Welcome Back!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (prescribedExercises.isEmpty()) "Select an exercise to begin your session." else "Complete your prescribed exercises for today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
            
            item {
                val availableExercises = listOf(
                    ExerciseItem("Pendulum", "pendulum", Icons.Default.Refresh),
                    ExerciseItem("Wall Climb", "wall_climb", Icons.Default.KeyboardArrowUp),
                    ExerciseItem("External Rotation", "external_rotation", Icons.Default.PlayArrow),
                    ExerciseItem("Crossover", "crossover", Icons.Default.Close)
                )

                val displayExercises = if (prescribedExercises.isEmpty()) {
                    availableExercises
                } else {
                    availableExercises.filter { avail -> prescribedExercises.any { it.exerciseId == avail.id } }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.height(300.dp).padding(horizontal = 16.dp)
                ) {
                    items(displayExercises) { exercise ->
                        val prescription = prescribedExercises.find { it.exerciseId == exercise.id }
                        ExerciseCard(exercise, prescription) {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                putExtra("EXERCISE_TYPE", exercise.id)
                                putExtra("SELECTED_SIDE", "right")
                                prescription?.let {
                                    putExtra("TARGET_SETS", it.sets)
                                    putExtra("TARGET_REPS", it.reps)
                                }
                            }
                            context.startActivity(intent)
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (assignedDoctorId != null) {
                        Button(
                            onClick = { onChatWithDoctor(assignedDoctorId, assignedDoctorName) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Chat with $assignedDoctorName")
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = onConnectClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (assignedDoctorId == null) "Connect to Doctor" else "Change Doctor")
                    }
                }
            }
        }
    }

    @Composable
    fun ExerciseCard(exercise: ExerciseItem, prescription: PrescribedExercise? = null, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    exercise.icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (prescription != null) {
                    Text(
                        "${prescription.sets} sets x ${prescription.reps} reps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun KinetiqTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF2E7D32),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF1F8E9)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeActivity.DoctorDashboardContainer(onLogout: () -> Unit, onPatientClick: (String) -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(Date()) }
    
    val appointmentViewModel: AppointmentViewModel = hiltViewModel()
    val appointmentState by appointmentViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        appointmentViewModel.loadAppointmentsForDoctor()
    }

    // Intercept back button to return to first tab if on another tab
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doctor Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Notifications, null) },
                    label = { Text("Alerts") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text("Patients") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Email, null) },
                    label = { Text("Requests") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    label = { Text("Calendar") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> AlertTriageScreen(hiltViewModel<DoctorDashboardViewModel>(), onPatientClick)
                1 -> PatientListScreen(hiltViewModel<DoctorDashboardViewModel>(), onPatientClick)
                2 -> RequestsScreen(hiltViewModel<ConnectionViewModel>())
                3 -> Column(Modifier.fillMaxSize()) {
                    GridCalendar(selectedDate = selectedDate, onDateSelected = { selectedDate = it }, modifier = Modifier.padding(16.dp))
                    
                    var subTab by remember { mutableIntStateOf(0) }
                    TabRow(selectedTabIndex = subTab) {
                        Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Scheduled") })
                        Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Requests") })
                    }

                    val filteredAppointments = appointmentState.appointments.filter {
                        val cal1 = Calendar.getInstance().apply { time = it.date }
                        val cal2 = Calendar.getInstance().apply { time = selectedDate }
                        val isSameDay = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                        
                        isSameDay && if (subTab == 0) it.status == AppointmentStatus.ACCEPTED else it.status == AppointmentStatus.PENDING
                    }

                    if (filteredAppointments.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No ${if (subTab == 0) "scheduled appointments" else "pending requests"} for this day.")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            items(filteredAppointments) { appt ->
                                AppointmentCard(
                                    appointment = appt, 
                                    isDoctorView = true,
                                    showActions = subTab == 1
                                ) { newStatus ->
                                    appointmentViewModel.updateStatus(appt.id, newStatus)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RequestsScreen(viewModel: ConnectionViewModel) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.observeIncomingRequests()
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Pending Connections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
        }
        
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.incomingRequests.isEmpty()) {
            item { 
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text("No pending patient requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(state.incomingRequests) { request ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(request.fromName, style = MaterialTheme.typography.titleLarge)
                        Text(request.fromEmail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        FilledIconButton(
                            onClick = { viewModel.acceptRequest(request) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Accept")
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.rejectRequest(request.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        
        state.error?.let {
            item {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
