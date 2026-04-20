package com.example.kinetiq.ui.dashboard

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kinetiq.models.AiAlert
import com.example.kinetiq.models.SessionResult
import com.example.kinetiq.ui.components.MooveToast
import com.example.kinetiq.ui.theme.*
import com.example.kinetiq.viewmodel.DoctorDashboardViewModel
import com.example.kinetiq.utils.DataSeeder
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun AlertTriageScreen(
    viewModel: DoctorDashboardViewModel,
    onPatientClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var docSummaryExpanded by remember { mutableStateOf(true) }
    var sessionAlertsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage, state.successMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { 
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MoovePrimary,
                    contentColor = MooveOnPrimary,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        },
        containerColor = MooveBackground
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { docSummaryExpanded = !docSummaryExpanded }
                        ) {
                            Text(
                                "Doc Summary",
                                style = MaterialTheme.typography.titleLarge,
                                color = MooveOnBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                if (docSummaryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MoovePrimary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        
                        Button(
                            onClick = { viewModel.refreshAiSummaries() },
                            enabled = state.canRefreshAi && !state.isAiLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MoovePrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            if (state.isAiLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.6.dp))
                                Text("Refresh", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    
                    if (!state.canRefreshAi) {
                        Text(
                            "Next refresh available in 3 days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MooveOnSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                item {
                    AnimatedVisibility(
                        visible = docSummaryExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            if (state.aiAlerts.isNotEmpty()) {
                                state.aiAlerts.forEach { alert ->
                                    AiAlertItem(alert = alert, onClick = { onPatientClick(alert.patientId) })
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            } else if (!state.isAiLoading) {
                                MooveCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "No clinical summaries found. Click 'Refresh' to generate summaries based on the last 14 days of session data.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MooveOnSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { sessionAlertsExpanded = !sessionAlertsExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Session Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MooveOnBackground,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Icon(
                            if (sessionAlertsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MoovePrimary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                item {
                    AnimatedVisibility(
                        visible = sessionAlertsExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            state.alertSessions.forEach { session ->
                                val patient = state.patients.find { it.id == session.patient_id }
                                AlertItem(
                                    session = session, 
                                    patientName = patient?.displayName ?: "Patient ${session.patient_id.take(8)}",
                                    onClick = { onPatientClick(session.patient_id) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MoovePrimary)
            }
        }
    }
}

@Composable
fun AiAlertItem(alert: AiAlert, onClick: () -> Unit) {
    val tintColor = when(alert.priority.uppercase()) {
        "HIGH" -> Color(0xFFE57373)
        "MEDIUM" -> Color(0xFFFFB703)
        "LOW" -> Color(0xFF4CAF50)
        else -> MoovePrimary
    }

    MooveCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                color = tintColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val icon = if (alert.type == "OUTLIER") Icons.Default.Warning else Icons.Default.AutoAwesome
                    Icon(icon, contentDescription = null, tint = tintColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(alert.patientName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    
                    Surface(
                        color = tintColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            alert.priority.uppercase(), 
                            style = MaterialTheme.typography.labelSmall, 
                            color = tintColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    alert.message, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MooveOnSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2
                )
            }
        }
    }
}

@Composable
fun AlertItem(session: SessionResult, patientName: String, onClick: () -> Unit) {
    val maxPain = session.pain_log.mapNotNull { it.level.toIntOrNull() }.maxOrNull() ?: session.pre_session.pain_score
    val isHighPriority = session.doctor_alerts.any { it.priority == "HIGH" } || maxPain >= 8
    val priorityColor = if (isHighPriority) Color(0xFFE57373) else Color(0xFFFFB703)
    
    MooveCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = priorityColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = priorityColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = patientName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MooveOnBackground
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.exercise.replace("_", " ").uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MooveOnSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = priorityColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "PAIN: $maxPain",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = session.timestamp_end.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDisabled
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MooveBackground.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                if (session.doctor_alerts.isEmpty()) {
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("•", color = priorityColor, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        val reason = when {
                            maxPain >= 8 -> "Critical pain level reported ($maxPain/10)"
                            (maxPain - session.pre_session.pain_score) > 2 -> "Significant pain spike during session (+${maxPain - session.pre_session.pain_score})"
                            session.rom_trend.delta < 0 -> "Decrease in Range of Motion detected"
                            else -> "Automatic clinical alert"
                        }
                        Text(
                            text = reason,
                            color = MooveOnBackground,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    session.doctor_alerts.forEach { alert ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("•", color = priorityColor, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = alert.reason,
                                color = MooveOnBackground,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
