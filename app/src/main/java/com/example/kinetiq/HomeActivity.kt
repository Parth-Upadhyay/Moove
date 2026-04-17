package com.example.kinetiq

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.kinetiq.models.*
import com.example.kinetiq.ui.auth.AuthScreen
import com.example.kinetiq.ui.components.GridCalendar
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
                        activeChatPartner != null -> activeChatPartner = null
                        showRomAnalytics != null -> {
                            showRomAnalytics = null
                            selectedTab = 1 // Take back to progress tab
                        }
                        selectedPatientId != null -> selectedPatientId = null
                        showAchievements -> showAchievements = false
                        showSettings -> showSettings = false
                    }
                }

                LaunchedEffect(Unit) {
                    val auth = FirebaseAuth.getInstance()
                    val db = FirebaseFirestore.getInstance()
                    
                    // Trigger Mock Data Seed (Development Only)
                    launch {
                        try {
                            DataSeeder(db).seedMockData()
                            Log.d("HomeActivity", "Mock data seeded successfully")
                        } catch (e: Exception) {
                            Log.e("HomeActivity", "Error seeding mock data", e)
                        }
                    }

                    var user = auth.currentUser
                    if (user != null) {
                        try {
                            // Reload user to get latest isEmailVerified status
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
                            Log.e("HomeActivity", "Error checking auth status", e)
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
                                        onBack = { 
                                            showRomAnalytics = null 
                                        }
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
                                        },
                                        onChatWithPatient = { patientId, name ->
                                            activeChatPartner = patientId to name
                                        }
                                    )
                                }
                            }
                        }
                        UserRole.PATIENT -> {
                            when {
                                showAchievements -> {
                                    AchievementsScreen(
                                        viewModel = hiltViewModel<AchievementsViewModel>(),
                                        onBack = { showAchievements = false }
                                    )
                                }
                                showSettings -> {
                                    SettingsScreen(
                                        viewModel = settingsViewModel,
                                        onBack = { showSettings = false }
                                    )
                                }
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
                                        onBack = { 
                                            showRomAnalytics = null
                                            selectedTab = 1
                                        }
                                    )
                                }
                                else -> {
                                    PatientDashboard(
                                        selectedTab = selectedTab,
                                        onTabSelected = { selectedTab = it },
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

        // Show toast on success
        LaunchedEffect(connectionState.successMessage) {
            connectionState.successMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                showRequestDialog = false
                connectionViewModel.clearStatusMessages()
            }
        }

        LaunchedEffect(appointmentState.successMessage) {
            appointmentState.successMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                showAppointmentDialog = false
                appointmentViewModel.clearMessages()
            }
        }

        // Dashboard level back handler: Reset to first tab
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
                NavigationBar(
                    containerColor = MooveSurface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { onTabSelected(0) },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MoovePrimary,
                            selectedTextColor = MoovePrimary,
                            indicatorColor = MooveSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { onTabSelected(1) },
                        icon = { Icon(Icons.Default.Search, null) },
                        label = { Text("Progress") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MoovePrimary,
                            selectedTextColor = MoovePrimary,
                            indicatorColor = MooveSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { onTabSelected(2) },
                        icon = { Icon(Icons.Default.DateRange, null) },
                        label = { Text("Calendar") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MoovePrimary,
                            selectedTextColor = MoovePrimary,
                            indicatorColor = MooveSurfaceVariant
                        )
                    )
                }
            },
            containerColor = MooveBackground
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
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
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${analyticsState.overallSummary.avgOptimalityImprovement.toInt()}%", style = MaterialTheme.typography.titleMedium, color = MooveOnBackground, fontWeight = FontWeight.Bold)
                                            Text("ROM Imp.", style = MaterialTheme.typography.labelSmall, color = MooveOnSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("-${analyticsState.overallSummary.avgPainReduction.toInt()} pts", style = MaterialTheme.typography.titleMedium, color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                                            Text("Pain Red.", style = MaterialTheme.typography.labelSmall, color = MooveOnSurfaceVariant)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${analyticsState.overallSummary.totalSessions}", style = MaterialTheme.typography.titleMedium, color = MooveOnBackground, fontWeight = FontWeight.Bold)
                                            Text("Sessions", style = MaterialTheme.typography.labelSmall, color = MooveOnSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(32.dp))
                            Text("Exercise Details", style = MaterialTheme.typography.titleLarge, color = MooveOnBackground, fontWeight = FontWeight.Bold)
                            Text("Select an exercise to view detailed trends.", style = MaterialTheme.typography.bodyMedium, color = MooveOnSurfaceVariant)
                            Spacer(Modifier.height(20.dp))
                        }
                        
                        val exercises = listOf(
                            ExerciseItem("Pendulum", "pendulum", Icons.Default.Refresh),
                            ExerciseItem("Wall Climb", "wall_climb", Icons.Default.KeyboardArrowUp),
                            ExerciseItem("External Rotation", "external_rotation", Icons.Default.PlayArrow),
                            ExerciseItem("Crossover", "crossover", Icons.Default.Close),
                            ExerciseItem("Lateral Arm Raise", "lateral_arm_raise", Icons.Default.Add),
                            ExerciseItem("Forward Arm Raise", "forward_arm_raise", Icons.AutoMirrored.Filled.KeyboardArrowRight),
                            ExerciseItem("Hitchhiker", "hitchhiker", Icons.Default.ThumbUp)
                        )

                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.height(650.dp) 
                            ) {
                                items(exercises) { exercise ->
                                    ExerciseCard(exercise) {
                                        onViewProgress(exercise.id)
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        var appointmentsExpanded by remember { mutableStateOf(false) }
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { appointmentsExpanded = !appointmentsExpanded }
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Appointments",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MooveOnBackground,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            if (appointmentsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.padding(start = 8.dp).size(20.dp),
                                            tint = MooveOnSurfaceVariant
                                        )
                                    }
                                    if (assignedDoctorId != null) {
                                        TextButton(onClick = { 
                                            appointmentViewModel.loadBookedSlots(assignedDoctorId!!, selectedDate)
                                            showAppointmentDialog = true 
                                        }) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MoovePrimary)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Request", color = MoovePrimary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            item {
                                AnimatedVisibility(
                                    visible = appointmentsExpanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    val dailyAppointments = appointmentState.appointments.filter { 
                                        val cal1 = Calendar.getInstance().apply { time = it.date }
                                        val cal2 = Calendar.getInstance().apply { time = selectedDate }
                                        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                                        cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                                    }

                                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                        if (dailyAppointments.isEmpty()) {
                                            Text(
                                                "No appointments scheduled for this day.",
                                                modifier = Modifier.padding(vertical = 16.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MooveOnSurfaceVariant
                                            )
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

                            item {
                                HorizontalDivider(color = MooveSurface, thickness = 8.dp)
                            }

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { sessionsExpanded = !sessionsExpanded }
                                        .padding(horizontal = 24.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Sessions for ${java.text.SimpleDateFormat("MMM dd").format(selectedDate)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MooveOnBackground,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Icon(
                                        if (sessionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.padding(start = 8.dp).size(20.dp),
                                        tint = MooveOnSurfaceVariant
                                    )
                                }
                            }
                            
                            item {
                                AnimatedVisibility(
                                    visible = sessionsExpanded,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                        if (analyticsState.isLoading) {
                                            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(color = MoovePrimary)
                                            }
                                        } else if (analyticsState.dailySessions.isEmpty()) {
                                            Text(
                                                "No sessions recorded for this day.",
                                                modifier = Modifier.padding(vertical = 16.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MooveOnSurfaceVariant
                                            )
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

        if (showRequestDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showRequestDialog = false 
                    connectionViewModel.clearStatusMessages()
                },
                containerColor = MooveBackground,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Connect to Doctor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MooveOnBackground) },
                text = {
                    Column {
                        Text("Ask your doctor for their registered Moove email.", style = MaterialTheme.typography.bodyMedium, color = MooveOnSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        MooveTextField(
                            value = doctorEmail,
                            onValueChange = { doctorEmail = it },
                            label = "Doctor's Email",
                            placeholder = "doctor@moove.com"
                        )
                        connectionState.error?.let { 
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) 
                        }
                    }
                },
                confirmButton = {
                    MoovePrimaryButton(
                        onClick = { connectionViewModel.sendRequest(doctorEmail) },
                        enabled = doctorEmail.isNotBlank() && !connectionState.isLoading,
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (connectionState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MooveOnPrimary)
                        } else {
                            Text("Send Request", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showRequestDialog = false 
                        connectionViewModel.clearStatusMessages()
                    }) {
                        Text("Cancel", color = MooveOnSurfaceVariant, fontWeight = FontWeight.Medium)
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
                onDismissRequest = { 
                    showAppointmentDialog = false 
                    appointmentViewModel.clearMessages()
                },
                containerColor = MooveBackground,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Request Appointment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MooveOnBackground) },
                text = {
                    Column {
                        Text("Request for ${java.text.SimpleDateFormat("MMM dd").format(selectedDate)} with $assignedDoctorName", style = MaterialTheme.typography.bodyMedium, color = MooveOnSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        
                        if (availableSlots.isEmpty()) {
                            Text("No slots available for this day. Please choose another date.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Select Time Slot", style = MaterialTheme.typography.labelLarge, color = MooveOnBackground, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(12.dp))
                            
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.height(140.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(availableSlots) { slot ->
                                    val isSelected = selectedTimeSlot == slot
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) MoovePrimary else MooveSurfaceVariant)
                                            .clickable { selectedTimeSlot = slot }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            slot,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MooveOnPrimary else MooveOnBackground,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        MooveTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = "Note (Optional)",
                            placeholder = "Reason for visit..."
                        )
                        appointmentState.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                },
                confirmButton = {
                    MoovePrimaryButton(
                        onClick = {
                            if (selectedTimeSlot != null) {
                                appointmentViewModel.requestAppointment(assignedDoctorId!!, assignedDoctorName, selectedDate, selectedTimeSlot!!, note)
                            }
                        }, 
                        enabled = !appointmentState.isLoading && selectedTimeSlot != null,
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (appointmentState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MooveOnPrimary)
                        } else {
                            Text("Send Request", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showAppointmentDialog = false 
                        appointmentViewModel.clearMessages()
                    }) {
                        Text("Cancel", color = MooveOnSurfaceVariant, fontWeight = FontWeight.Medium)
                    }
                }
            )
        }
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
                        Text(
                            if (isDoctorView) appointment.patientName else "Dr. ${appointment.doctorName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MooveOnBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(14.dp), tint = MoovePrimary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                appointment.timeSlot,
                                style = MaterialTheme.typography.bodySmall,
                                color = MoovePrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            appointment.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (appointment.note.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(appointment.note, style = MaterialTheme.typography.bodySmall, color = MooveOnSurfaceVariant)
                }
                
                if (showActions && appointment.status == AppointmentStatus.PENDING) {
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onStatusUpdate(AppointmentStatus.REJECTED) }) {
                            Text("Reject", color = Color(0xFFE57373), fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onStatusUpdate(AppointmentStatus.ACCEPTED) },
                            colors = ButtonDefaults.buttonColors(containerColor = MoovePrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Accept", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
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
                    Text(
                        session.exercise.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MooveOnBackground,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${session.results.peak_rom_degrees.toInt()}°",
                        style = MaterialTheme.typography.titleMedium,
                        color = MoovePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MooveOnSurfaceVariant)
                    Spacer(Modifier.width(6.6.dp))
                    val avgPain = if (session.pain_log.isNotEmpty()) {
                        session.pain_log.mapNotNull { it.level.toDoubleOrNull() }.average()
                    } else session.pre_session.pain_score.toDouble()
                    
                    Text("Avg Pain Score: ${String.format("%.1f", avgPain)}", style = MaterialTheme.typography.bodySmall, color = MooveOnSurfaceVariant)
                }

                if (session.form_flags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    session.form_flags.take(2).forEach { flag ->
                        Surface(
                            color = Color(0xFFE57373).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                "• ${flag.flag}", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = Color(0xFFE57373),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
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
                    Text(
                        "Welcome Back!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MooveOnBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (prescribedExercises.isEmpty()) "Select an exercise to begin your session." else "Complete your prescribed exercises for today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MooveOnSurfaceVariant
                    )
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

                val displayExercises = if (prescribedExercises.isEmpty()) {
                    availableExercises
                } else {
                    availableExercises.filter { avail -> prescribedExercises.any { it.exerciseId == avail.id } }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.height(if (displayExercises.size > 4) 650.dp else 320.dp).padding(horizontal = 24.dp)
                ) {
                    items(displayExercises) { exercise ->
                        val prescription = prescribedExercises.find { it.exerciseId == exercise.id }
                        ExerciseCard(exercise, prescription) {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                putExtra("EXERCISE_TYPE", exercise.id)
                                putExtra("SELECTED_SIDE", "right")
                                putExtra("VOICE_FEEDBACK_ENABLED", isVoiceEnabled)
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
                Column(modifier = Modifier.padding(24.dp)) {
                    if (assignedDoctorId != null) {
                        MoovePrimaryButton(
                            onClick = { onChatWithDoctor(assignedDoctorId, assignedDoctorName) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp), tint = MooveOnPrimary)
                            Spacer(Modifier.width(12.dp))
                            Text("Chat with $assignedDoctorName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    OutlinedButton(
                        onClick = onConnectClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MoovePrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MoovePrimary)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = MoovePrimary)
                        Spacer(Modifier.width(12.dp))
                        Text(if (assignedDoctorId == null) "Connect to Doctor" else "Change Doctor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    @Composable
    fun ExerciseCard(exercise: ExerciseItem, prescription: PrescribedExercise? = null, onClick: () -> Unit) {
        MooveCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = MooveBackground,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(56.dp).border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            exercise.icon,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MoovePrimary
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MooveOnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (prescription != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${prescription.sets} sets • ${prescription.reps} reps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MooveOnSurfaceVariant,
                        maxLines = 1
                    )
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
    onLogout: () -> Unit, 
    onPatientClick: (String) -> Unit,
    onChatWithPatient: (String, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(Date()) }
    
    val appointmentViewModel: AppointmentViewModel = hiltViewModel()
    val appointmentState by appointmentViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        appointmentViewModel.loadAppointmentsForDoctor()
    }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Provider Dashboard", fontWeight = FontWeight.Bold, color = MooveOnBackground) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MooveBackground),
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color(0xFFE57373))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MooveSurface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Notifications, null) },
                    label = { Text("Alerts") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MoovePrimary, indicatorColor = MooveSurfaceVariant, selectedTextColor = MoovePrimary)
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text("Patients") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MoovePrimary, indicatorColor = MooveSurfaceVariant, selectedTextColor = MoovePrimary)
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Email, null) },
                    label = { Text("Requests") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MoovePrimary, indicatorColor = MooveSurfaceVariant, selectedTextColor = MoovePrimary)
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    label = { Text("Schedule") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MoovePrimary, indicatorColor = MooveSurfaceVariant, selectedTextColor = MoovePrimary)
                )
            }
        },
        containerColor = MooveBackground
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> AlertTriageScreen(hiltViewModel<DoctorDashboardViewModel>(), onPatientClick)
                1 -> PatientListScreen(
                    viewModel = hiltViewModel<DoctorDashboardViewModel>(), 
                    onPatientClick = onPatientClick,
                    onChatClick = onChatWithPatient
                )
                2 -> RequestsScreen(hiltViewModel<ConnectionViewModel>())
                3 -> Column(Modifier.fillMaxSize()) {
                    GridCalendar(selectedDate = selectedDate, onDateSelected = { selectedDate = it }, modifier = Modifier.padding(16.dp))
                    
                    var subTab by remember { mutableIntStateOf(0) }
                    TabRow(
                        selectedTabIndex = subTab,
                        containerColor = MooveSurface,
                        contentColor = MoovePrimary,
                        indicator = { tabPositions ->
                            if (subTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[subTab]),
                                    color = MoovePrimary
                                )
                            }
                        }
                    ) {
                        Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Scheduled", style = MaterialTheme.typography.labelLarge, color = if (subTab == 0) MoovePrimary else MooveOnSurfaceVariant) })
                        Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Pending", style = MaterialTheme.typography.labelLarge, color = if (subTab == 1) MoovePrimary else MooveOnSurfaceVariant) })
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
                            Text("No ${if (subTab == 0) "scheduled appointments" else "pending requests"} for this day.", color = MooveOnSurfaceVariant)
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
                                Spacer(Modifier.height(12.dp))
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

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Pending Connections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MooveOnBackground)
            Spacer(Modifier.height(24.dp))
        }
        
        if (state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MoovePrimary)
                }
            }
        } else if (state.incomingRequests.isEmpty()) {
            item { 
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text("No pending patient requests.", color = MooveOnSurfaceVariant)
                }
            }
        }

        items(state.incomingRequests) { request ->
            MooveCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(request.fromName, style = MaterialTheme.typography.titleLarge, color = MooveOnBackground, fontWeight = FontWeight.Bold)
                        Text(request.fromEmail, style = MaterialTheme.typography.bodyMedium, color = MooveOnSurfaceVariant)
                    }
                    Row {
                        IconButton(
                            onClick = { viewModel.acceptRequest(request) },
                            modifier = Modifier.background(Color(0xFF4CAF50).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color(0xFF4CAF50))
                        }
                        Spacer(Modifier.width(12.dp))
                        IconButton(
                            onClick = { viewModel.rejectRequest(request.id) },
                            modifier = Modifier.background(Color(0xFFE57373).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color(0xFFE57373))
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

@Composable
fun MooveCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MooveSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
