package com.example.kinetiq

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kinetiq.models.*
import com.example.kinetiq.ui.auth.AuthScreen
import com.example.kinetiq.ui.components.GridCalendar
import com.example.kinetiq.ui.components.MooveToast
import com.example.kinetiq.ui.dashboard.*
import com.example.kinetiq.ui.theme.*
import com.example.kinetiq.viewmodel.*
import com.example.kinetiq.utils.DataSeeder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

data class ExerciseItem(val name: String, val id: String, val icon: ImageVector)

@AndroidEntryPoint
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MooveTheme {
                val authViewModel: AuthViewModel = hiltViewModel()
                val authState by authViewModel.uiState.collectAsState()
                
                var currentUserRole by remember { mutableStateOf<UserRole?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                
                // Navigation States
                var selectedPatientId by remember { mutableStateOf<String?>(null) }
                var showRomAnalytics by remember { mutableStateOf<Pair<String, String>?>(null) }
                var activeChatPartner by remember { mutableStateOf<Pair<String, String>?>(null) }
                var showAchievements by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                
                // Local state for dashboard tabs
                var selectedTab by remember { mutableIntStateOf(0) }

                // Improved Back Button Handling
                BackHandler(enabled = activeChatPartner != null || showRomAnalytics != null || selectedPatientId != null || showAchievements || showSettings) {
                    when {
                        activeChatPartner != null -> {
                            activeChatPartner = null
                            if (currentUserRole == UserRole.DOCTOR) selectedTab = 1
                        }
                        showRomAnalytics != null -> {
                            showRomAnalytics = null
                            selectedTab = 1
                        }
                        selectedPatientId != null -> {
                            selectedPatientId = null
                            if (currentUserRole == UserRole.DOCTOR) selectedTab = 1
                        }
                        showAchievements -> showAchievements = false
                        showSettings -> showSettings = false
                    }
                }

                LaunchedEffect(Unit) {
                    val auth = FirebaseAuth.getInstance()
                    val db = FirebaseFirestore.getInstance()
                    
                    launch {
                        try {
                            DataSeeder(db).seedMockData()
                        } catch (e: Exception) {
                            Log.e("HomeActivity", "Error seeding mock data", e)
                        }
                    }

                    var user = auth.currentUser
                    if (user != null) {
                        try {
                            user.reload().await()
                            user = auth.currentUser
                            
                            if (user != null && !user.isEmailVerified) {
                                auth.signOut()
                                currentUserRole = null
                            } else if (user != null) {
                                val doc = db.collection("users").document(user.uid).get().await()
                                val roleStr = doc.getString("role")?.uppercase()
                                if (roleStr != null) {
                                    currentUserRole = try { UserRole.valueOf(roleStr) } catch(e: Exception) { UserRole.PATIENT }
                                } else {
                                    auth.signOut()
                                }
                            }
                        } catch (e: Exception) {
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
                    Box(Modifier.fillMaxSize().background(MooveBackground), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MoovePrimary)
                    }
                } else if (currentUserRole == null) {
                    AuthScreen(
                        onAuthSuccess = { role -> currentUserRole = role },
                        viewModel = authViewModel
                    )
                } else {
                    val settingsViewModel: SettingsViewModel = hiltViewModel()
                    
                    // Smooth Screen Transition Container
                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = Triple(activeChatPartner, showRomAnalytics, selectedPatientId) to (showAchievements to showSettings),
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "MainNavigation"
                        ) { (primaryNav, secondaryNav) ->
                            val (chat, rom, patientId) = primaryNav
                            val (achievements, settings) = secondaryNav

                            when {
                                chat != null -> ChatScreen(otherUserId = chat.first, otherUserName = chat.second, onBack = { activeChatPartner = null })
                                rom != null -> RomDashboardScreen(patientId = rom.first, exerciseType = rom.second, onBack = { showRomAnalytics = null })
                                patientId != null -> PatientDetailScreen(patientId = patientId, onBack = { selectedPatientId = null })
                                achievements -> AchievementsScreen(viewModel = hiltViewModel(), onBack = { showAchievements = false })
                                settings -> SettingsScreen(viewModel = settingsViewModel, onBack = { showSettings = false })
                                else -> {
                                    if (currentUserRole == UserRole.DOCTOR) {
                                        DoctorDashboardContainer(
                                            selectedTab = selectedTab,
                                            onTabSelected = { selectedTab = it },
                                            onLogout = {
                                                FirebaseAuth.getInstance().signOut()
                                                authViewModel.resetState()
                                                currentUserRole = null
                                            },
                                            onPatientClick = { selectedPatientId = it },
                                            onChatWithPatient = { id, name -> activeChatPartner = id to name }
                                        )
                                    } else {
                                        PatientDashboard(
                                            selectedTab = selectedTab,
                                            onTabSelected = { selectedTab = it },
                                            onLogout = {
                                                FirebaseAuth.getInstance().signOut()
                                                authViewModel.resetState()
                                                currentUserRole = null
                                            },
                                            onChatWithDoctor = { id, name -> activeChatPartner = id to name },
                                            onViewProgress = { type -> 
                                                val uid = FirebaseAuth.getInstance().currentUser?.uid
                                                if (uid != null) showRomAnalytics = uid to type
                                            },
                                            onShowAchievements = { showAchievements = true },
                                            onShowSettings = { showSettings = true },
                                            settingsViewModel = settingsViewModel
                                        )
                                    }
                                }
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
        selectedTab: Int,
        onTabSelected: (Int) -> Unit,
        onLogout: () -> Unit, 
        onChatWithDoctor: (String, String) -> Unit,
        onViewProgress: (String) -> Unit,
        onShowAchievements: () -> Unit,
        onShowSettings: () -> Unit,
        settingsViewModel: SettingsViewModel
    ) {
        val context = LocalContext.current
        var selectedDate by remember { mutableStateOf(Date()) }
        
        val connectionViewModel: ConnectionViewModel = hiltViewModel()
        val analyticsViewModel: AnalyticsViewModel = hiltViewModel()
        val appointmentViewModel: AppointmentViewModel = hiltViewModel()
        
        val connectionState by connectionViewModel.uiState.collectAsState()
        val analyticsState by analyticsViewModel.uiState.collectAsState()
        val appointmentState by appointmentViewModel.uiState.collectAsState()
        val settingsState by settingsViewModel.uiState.collectAsState()
        
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
            connectionState.successMessage?.let {
                MooveToast.show(context, it)
                showRequestDialog = false
                connectionViewModel.clearStatusMessages()
            }
        }

        LaunchedEffect(appointmentState.successMessage) {
            appointmentState.successMessage?.let {
                MooveToast.show(context, it)
                showAppointmentDialog = false
                appointmentViewModel.clearMessages()
            }
        }

        BackHandler(enabled = selectedTab != 0) {
            onTabSelected(0)
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Moove", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MooveBackground,
                        titleContentColor = MooveOnBackground
                    ),
                    navigationIcon = {
                        IconButton(onClick = onShowAchievements) {
                            Icon(Icons.Default.Star, contentDescription = "Achievements", tint = MoovePrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = onShowSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MooveOnSurfaceVariant)
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color(0xFFE57373))
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MooveSurface, tonalElevation = 8.dp) {
                    val tabs = listOf("Home" to Icons.Default.Home, "Progress" to Icons.Default.Search, "Calendar" to Icons.Default.DateRange)
                    tabs.forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            icon = { Icon(icon, null) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MoovePrimary,
                                selectedTextColor = MoovePrimary,
                                indicatorColor = MooveSurfaceVariant
                            )
                        )
                    }
                }
            },
            containerColor = MooveBackground
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                        } else {
                            (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                        }
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> PatientHomeContent(
                            assignedDoctorId = assignedDoctorId,
                            assignedDoctorName = assignedDoctorName,
                            onChatWithDoctor = onChatWithDoctor,
                            onConnectClick = { showRequestDialog = true },
                            patientData = patientData,
                            isVoiceEnabled = settingsState.notificationSettings.isVoiceFeedbackEnabled
                        )
                        1 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                            item {
                                Spacer(Modifier.height(16.dp))
                                Text("Your Recovery Journey", style = MaterialTheme.typography.headlineSmall, color = MooveOnBackground, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(24.dp))
                                
                                MooveCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Recovery Status", style = MaterialTheme.typography.titleMedium, color = MooveOnSurfaceVariant)
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "${analyticsState.overallSummary.weightedRecoveryPercentage.toInt()}%",
                                            style = MaterialTheme.typography.displayMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MoovePrimary
                                        )
                                        Text("Overall Weighted Progress", style = MaterialTheme.typography.bodySmall, color = MooveOnSurfaceVariant)
                                        
                                        Spacer(Modifier.height(24.dp))
                                        HorizontalDivider(color = MooveSurfaceVariant)
                                        Spacer(Modifier.height(24.dp))
                                        
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                            StatItem("${analyticsState.overallSummary.avgOptimalityImprovement.toInt()}%", "ROM Imp.", MooveOnBackground)
                                            StatItem("-${analyticsState.overallSummary.avgPainReduction.toInt()} pts", "Pain Red.", Color(0xFFE57373))
                                            StatItem("${analyticsState.overallSummary.totalSessions}", "Sessions", MooveOnBackground)
                                        }
                                    }
                                }
                                
                                Spacer(Modifier.height(32.dp))
                                Text("Exercise Details", style = MaterialTheme.typography.titleLarge, color = MooveOnBackground, fontWeight = FontWeight.Bold)
                                Text("Select an exercise to view detailed trends.", style = MaterialTheme.typography.bodyMedium, color = MooveOnSurfaceVariant)
                                Spacer(Modifier.height(20.dp))
                            }
                            
                            item {
                                val exercises = listOf(
                                    ExerciseItem("Pendulum", "pendulum", Icons.Default.Refresh),
                                    ExerciseItem("Wall Climb", "wall_climb", Icons.Default.KeyboardArrowUp),
                                    ExerciseItem("External Rotation", "external_rotation", Icons.Default.PlayArrow),
                                    ExerciseItem("Crossover", "crossover", Icons.Default.Close),
                                    ExerciseItem("Lateral Arm Raise", "lateral_arm_raise", Icons.Default.Add),
                                    ExerciseItem("Forward Arm Raise", "forward_arm_raise", Icons.AutoMirrored.Filled.KeyboardArrowRight),
                                    ExerciseItem("Hitchhiker", "hitchhiker", Icons.Default.ThumbUp)
                                )

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.height(750.dp) // Increased height to accommodate taller cards
                                ) {
                                    items(exercises) { exercise ->
                                        ExerciseCard(exercise) { onViewProgress(exercise.id) }
                                    }
                                }
                            }
                        }
                        2 -> {
                            var appointmentsExpanded by remember { mutableStateOf(true) }
                            var sessionsExpanded by remember { mutableStateOf(false) }
                            
                            LazyColumn(Modifier.fillMaxSize()) {
                                item {
                                    GridCalendar(
                                        selectedDate = selectedDate, 
                                        onDateSelected = { selectedDate = it },
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                                
                                item {
                                    ExpandableSectionHeader("Appointments", appointmentsExpanded, { appointmentsExpanded = it }) {
                                        if (assignedDoctorId != null) {
                                            TextButton(onClick = { 
                                                appointmentViewModel.loadBookedSlots(assignedDoctorId!!, selectedDate)
                                                showAppointmentDialog = true 
                                            }) {
                                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = MoovePrimary)
                                                Spacer(Modifier.width(4.dp))
                                                Text("Request", color = MoovePrimary, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }

                                item {
                                    AnimatedVisibility(visible = appointmentsExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                                        val dailyAppointments = appointmentState.appointments.filter { 
                                            isSameDay(it.date, selectedDate)
                                        }
                                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                            if (dailyAppointments.isEmpty()) {
                                                EmptyStateMessage("No appointments scheduled for this day.")
                                            } else {
                                                dailyAppointments.forEach { appt ->
                                                    AppointmentCard(appt)
                                                    Spacer(Modifier.height(12.dp))
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                        }
                                    }
                                }

                                item { HorizontalDivider(color = MooveSurface, thickness = 8.dp) }

                                item {
                                    ExpandableSectionHeader("Sessions for ${java.text.SimpleDateFormat("MMM dd").format(selectedDate)}", sessionsExpanded, { sessionsExpanded = it })
                                }
                                
                                item {
                                    AnimatedVisibility(visible = sessionsExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                            if (analyticsState.isLoading) {
                                                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(color = MoovePrimary)
                                                }
                                            } else if (analyticsState.dailySessions.isEmpty()) {
                                                EmptyStateMessage("No sessions recorded for this day.")
                                            } else {
                                                analyticsState.dailySessions.forEach { session ->
                                                    SessionHistoryCard(session)
                                                    Spacer(Modifier.height(12.dp))
                                                }
                                            }
                                            Spacer(Modifier.height(24.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showRequestDialog) {
            AlertDialog(
                onDismissRequest = { showRequestDialog = false; connectionViewModel.clearStatusMessages() },
                containerColor = MooveBackground,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Connect to Doctor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Ask your doctor for their registered Moove email.", color = MooveOnSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        MooveTextField(value = doctorEmail, onValueChange = { doctorEmail = it }, label = "Doctor's Email")
                        connectionState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    MoovePrimaryButton(onClick = { connectionViewModel.sendRequest(doctorEmail) }, enabled = doctorEmail.isNotBlank() && !connectionState.isLoading) {
                        if (connectionState.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MooveOnPrimary)
                        else Text("Send Request")
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
                onDismissRequest = { showAppointmentDialog = false; appointmentViewModel.clearMessages() },
                containerColor = MooveBackground,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Request Appointment", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("For ${java.text.SimpleDateFormat("MMM dd").format(selectedDate)} with $assignedDoctorName", color = MooveOnSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        if (availableSlots.isEmpty()) {
                            Text("No slots available.", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Select Time Slot", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(140.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(availableSlots) { slot ->
                                    val isSelected = selectedTimeSlot == slot
                                    Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (isSelected) MoovePrimary else MooveSurfaceVariant).clickable { selectedTimeSlot = slot }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text(slot, color = if (isSelected) MooveOnPrimary else MooveOnBackground)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        MooveTextField(value = note, onValueChange = { note = it }, label = "Note (Optional)")
                    }
                },
                confirmButton = {
                    MoovePrimaryButton(onClick = { if (selectedTimeSlot != null) appointmentViewModel.requestAppointment(assignedDoctorId!!, assignedDoctorName, selectedDate, selectedTimeSlot!!, note) }, enabled = !appointmentState.isLoading && selectedTimeSlot != null) {
                        Text("Send Request")
                    }
                }
            )
        }
    }

    @Composable
    private fun StatItem(value: String, label: String, color: Color) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MooveOnSurfaceVariant)
        }
    }

    @Composable
    private fun ExpandableSectionHeader(title: String, expanded: Boolean, onToggle: (Boolean) -> Unit, actions: @Composable () -> Unit = {}) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle(!expanded) }.padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MooveOnBackground, fontWeight = FontWeight.Bold)
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, modifier = Modifier.padding(start = 8.dp).size(20.dp), tint = MooveOnSurfaceVariant)
            }
            actions()
        }
    }

    @Composable
    private fun EmptyStateMessage(message: String) {
        Text(message, modifier = Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MooveOnSurfaceVariant)
    }

    private fun isSameDay(d1: Date, d2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = d1 }
        val cal2 = Calendar.getInstance().apply { time = d2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    @Composable
    fun AppointmentCard(
        appointment: Appointment, 
        isDoctorView: Boolean = false,
        showActions: Boolean = false, 
        onStatusUpdate: (AppointmentStatus) -> Unit = {}
    ) {
        val statusColor = when(appointment.status) {
            AppointmentStatus.ACCEPTED -> Color(0xFF4CAF50)
            AppointmentStatus.PENDING -> Color(0xFFFFB703)
            AppointmentStatus.REJECTED -> Color(0xFFE57373)
            else -> MooveOnSurfaceVariant
        }

        MooveCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(if (isDoctorView) appointment.patientName else "Dr. ${appointment.doctorName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, null, modifier = Modifier.size(14.dp), tint = MoovePrimary)
                            Spacer(Modifier.width(4.dp))
                            Text(appointment.timeSlot, color = MoovePrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                    Surface(color = statusColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Text(appointment.status.name, style = MaterialTheme.typography.labelSmall, color = statusColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold)
                    }
                }
                if (appointment.note.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(appointment.note, style = MaterialTheme.typography.bodySmall, color = MooveOnSurfaceVariant)
                }
                
                if (showActions && appointment.status == AppointmentStatus.PENDING) {
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onStatusUpdate(AppointmentStatus.REJECTED) }) { Text("Reject", color = Color(0xFFE57373)) }
                        Button(onClick = { onStatusUpdate(AppointmentStatus.ACCEPTED) }, colors = ButtonDefaults.buttonColors(containerColor = MoovePrimary)) { Text("Accept") }
                    }
                }
            }
        }
    }

    @Composable
    fun SessionHistoryCard(session: SessionResult) {
        MooveCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(session.exercise.replace("_", " ").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${session.results.peak_rom_degrees.toInt()}°", style = MaterialTheme.typography.titleMedium, color = MoovePrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = MooveOnSurfaceVariant)
                    Spacer(Modifier.width(6.6.dp))
                    val avgPain = if (session.pain_log.isNotEmpty()) session.pain_log.mapNotNull { it.level.toDoubleOrNull() }.average() else session.pre_session.pain_score.toDouble()
                    Text("Avg Pain Score: ${String.format("%.1f", avgPain)}", color = MooveOnSurfaceVariant)
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
        patientData: Patient?,
        isVoiceEnabled: Boolean
    ) {
        val context = LocalContext.current
        val prescribedExercises = patientData?.clinicalPrescription?.exercises?.filter { it.isActive } ?: emptyList()

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text("Welcome Back!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(if (prescribedExercises.isEmpty()) "Select an exercise to begin." else "Complete today's exercises.", color = MooveOnSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                }
            }
            
            item {
                val availableExercises = listOf(
                    ExerciseItem("Pendulum", "pendulum", Icons.Default.Refresh),
                    ExerciseItem("Wall Climb", "wall_climb", Icons.Default.KeyboardArrowUp),
                    ExerciseItem("External Rotation", "external_rotation", Icons.Default.PlayArrow),
                    ExerciseItem("Crossover", "crossover", Icons.Default.Close),
                    ExerciseItem("Lateral Arm Raise", "lateral_arm_raise", Icons.Default.Add),
                    ExerciseItem("Forward Arm Raise", "forward_arm_raise", Icons.AutoMirrored.Filled.KeyboardArrowRight),
                    ExerciseItem("Hitchhiker", "hitchhiker", Icons.Default.ThumbUp)
                )

                val displayExercises = if (prescribedExercises.isEmpty()) availableExercises else availableExercises.filter { avail -> prescribedExercises.any { it.exerciseId == avail.id } }

                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.height(if (displayExercises.size > 4) 750.dp else 380.dp).padding(horizontal = 24.dp)) {
                    items(displayExercises) { exercise ->
                        val prescription = prescribedExercises.find { it.exerciseId == exercise.id }
                        ExerciseCard(exercise, prescription) {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                putExtra("EXERCISE_TYPE", exercise.id)
                                putExtra("SELECTED_SIDE", "right")
                                putExtra("VOICE_FEEDBACK_ENABLED", isVoiceEnabled)
                                prescription?.let { putExtra("TARGET_SETS", it.sets); putExtra("TARGET_REPS", it.reps) }
                            }
                            context.startActivity(intent)
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(24.dp)) {
                    if (assignedDoctorId != null) {
                        MoovePrimaryButton(onClick = { onChatWithDoctor(assignedDoctorId, assignedDoctorName) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Email, null, modifier = Modifier.size(20.dp), tint = MooveOnPrimary)
                            Spacer(Modifier.width(12.dp))
                            Text("Chat with $assignedDoctorName", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    OutlinedButton(onClick = onConnectClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MoovePrimary)) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp), tint = MoovePrimary)
                        Spacer(Modifier.width(12.dp))
                        Text(if (assignedDoctorId == null) "Connect to Doctor" else "Change Doctor", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    @Composable
    fun ExerciseCard(exercise: ExerciseItem, prescription: PrescribedExercise? = null, onClick: () -> Unit) {
        MooveCard(modifier = Modifier.fillMaxWidth().height(180.dp).clickable(onClick = onClick)) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Surface(color = MooveBackground, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(56.dp).border(1.dp, CardBorder, RoundedCornerShape(12.dp))) {
                    Box(contentAlignment = Alignment.Center) { Icon(exercise.icon, null, modifier = Modifier.size(28.dp), tint = MoovePrimary) }
                }
                Spacer(Modifier.height(12.dp))
                Text(exercise.name, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (prescription != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("${prescription.sets} sets • ${prescription.reps} reps", style = MaterialTheme.typography.labelSmall, color = MooveOnSurfaceVariant)
                }
            }
        }
    }

    @Composable
    private fun Icon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color) {
        androidx.compose.material3.Icon(icon, contentDescription, modifier = modifier, tint = tint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeActivity.DoctorDashboardContainer(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onLogout: () -> Unit, 
    onPatientClick: (String) -> Unit,
    onChatWithPatient: (String, String) -> Unit
) {
    var selectedDate by remember { mutableStateOf(Date()) }
    val appointmentViewModel: AppointmentViewModel = hiltViewModel()
    val appointmentState by appointmentViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { appointmentViewModel.loadAppointmentsForDoctor() }

    BackHandler(enabled = selectedTab != 0) { onTabSelected(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Provider Dashboard", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MooveBackground),
                actions = { IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color(0xFFE57373)) } }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MooveSurface) {
                val tabs = listOf("Alerts" to Icons.Default.Notifications, "Patients" to Icons.AutoMirrored.Filled.List, "Requests" to Icons.Default.Email, "Schedule" to Icons.Default.DateRange)
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(selected = selectedTab == index, onClick = { onTabSelected(index) }, icon = { Icon(icon, null) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = MoovePrimary, indicatorColor = MooveSurfaceVariant, selectedTextColor = MoovePrimary))
                }
            }
        },
        containerColor = MooveBackground
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(targetState = selectedTab, label = "DoctorTabTransition") { targetTab ->
                when (targetTab) {
                    0 -> AlertTriageScreen(hiltViewModel(), onPatientClick)
                    1 -> PatientListScreen(viewModel = hiltViewModel(), onPatientClick = onPatientClick, onChatClick = onChatWithPatient)
                    2 -> RequestsScreen(hiltViewModel())
                    3 -> Column(Modifier.fillMaxSize()) {
                        GridCalendar(selectedDate = selectedDate, onDateSelected = { selectedDate = it }, modifier = Modifier.padding(16.dp))
                        var subTab by remember { mutableIntStateOf(0) }
                        TabRow(selectedTabIndex = subTab, containerColor = MooveSurface, contentColor = MoovePrimary) {
                            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Scheduled") })
                            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Pending") })
                        }
                        val filteredAppointments = appointmentState.appointments.filter { appt ->
                            isSameDay(appt.date, selectedDate) && if (subTab == 0) appt.status == AppointmentStatus.ACCEPTED else appt.status == AppointmentStatus.PENDING
                        }
                        if (filteredAppointments.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No appointments.", color = MooveOnSurfaceVariant) }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                items(filteredAppointments) { appt ->
                                    AppointmentCard(appointment = appt, isDoctorView = true, showActions = subTab == 1) { appointmentViewModel.updateStatus(appt.id, it) }
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isSameDay(d1: Date, d2: Date): Boolean {
    val cal1 = Calendar.getInstance().apply { time = d1 }
    val cal2 = Calendar.getInstance().apply { time = d2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun RequestsScreen(viewModel: ConnectionViewModel) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.observeIncomingRequests() }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Pending Connections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
        }
        if (state.isLoading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MoovePrimary) } }
        else if (state.incomingRequests.isEmpty()) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No requests.", color = MooveOnSurfaceVariant) } }
        items(state.incomingRequests) { request ->
            MooveCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(request.fromName, fontWeight = FontWeight.Bold)
                        Text(request.fromEmail, style = MaterialTheme.typography.bodyMedium, color = MooveOnSurfaceVariant)
                    }
                    Row {
                        IconButton(onClick = { viewModel.acceptRequest(request) }, modifier = Modifier.background(Color(0xFF4CAF50).copy(alpha = 0.1f), RoundedCornerShape(12.dp))) { Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50)) }
                        Spacer(Modifier.width(12.dp))
                        IconButton(onClick = { viewModel.rejectRequest(request.id) }, modifier = Modifier.background(Color(0xFFE57373).copy(alpha = 0.1f), RoundedCornerShape(12.dp))) { Icon(Icons.Default.Close, null, tint = Color(0xFFE57373)) }
                    }
                }
            }
        }
    }
}

@Composable
fun MooveCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(modifier = modifier, shape = shape, color = MooveSurface, border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
