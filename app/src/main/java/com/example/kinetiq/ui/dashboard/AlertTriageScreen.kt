package com.example.kinetiq.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kinetiq.models.Patient
import com.example.kinetiq.models.SessionResult
import com.example.kinetiq.viewmodel.DoctorDashboardViewModel

@Composable
fun AlertTriageScreen(
    viewModel: DoctorDashboardViewModel,
    onPatientClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text(
                    "Priority Alerts",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            items(state.alertSessions) { session ->
                val patient = state.patients.find { it.id == session.patient_id }
                AlertItem(
                    session = session, 
                    patientName = patient?.displayName ?: "Patient ${session.patient_id.take(8)}",
                    onClick = { onPatientClick(session.patient_id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (state.alertSessions.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        "All clear! No urgent alerts.",
                        modifier = Modifier.padding(top = 20.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
fun AlertItem(session: SessionResult, patientName: String, onClick: () -> Unit) {
    val isHighPriority = session.doctor_alerts.any { it.priority == "HIGH" }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighPriority) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = patientName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = session.exercise,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = session.timestamp_end.take(10),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            session.doctor_alerts.forEach { alert ->
                Text(
                    text = "⚠️ ${alert.reason}",
                    color = if (alert.priority == "HIGH") Color.Red else Color.Unspecified,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
