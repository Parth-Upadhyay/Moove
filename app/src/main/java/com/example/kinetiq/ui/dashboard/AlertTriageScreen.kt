package com.example.kinetiq.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kinetiq.models.SessionResult
import com.example.kinetiq.ui.theme.*
import com.example.kinetiq.viewmodel.DoctorDashboardViewModel

@Composable
fun AlertTriageScreen(
    viewModel: DoctorDashboardViewModel,
    onPatientClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(MooveBackground)) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MoovePrimary)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
        ) {
            item {
                Text(
                    "Priority Alerts",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MooveOnBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            
            items(state.alertSessions) { session ->
                val patient = state.patients.find { it.id == session.patient_id }
                AlertItem(
                    session = session, 
                    patientName = patient?.displayName ?: "Patient ${session.patient_id.take(8)}",
                    onClick = { onPatientClick(session.patient_id) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            if (state.alertSessions.isEmpty() && !state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "All clear! No urgent alerts.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MooveOnSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertItem(session: SessionResult, patientName: String, onClick: () -> Unit) {
    val isHighPriority = session.doctor_alerts.any { it.priority == "HIGH" }
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
                    Text(
                        text = session.exercise.replace("_", " ").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MooveOnSurfaceVariant
                    )
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
