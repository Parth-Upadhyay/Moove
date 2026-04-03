package com.example.kinetiq.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kinetiq.models.Patient
import com.example.kinetiq.viewmodel.DoctorDashboardViewModel

@Composable
fun PatientListScreen(
    viewModel: DoctorDashboardViewModel,
    onPatientClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "My Patients",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
        }
        
        items(state.patients) { patient ->
            PatientItem(patient = patient, onClick = { onPatientClick(patient.id) })
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun PatientItem(patient: Patient, onClick: () -> Unit) {
    ListItem(
        headlineContent = { 
            Text(
                text = patient.displayName.ifEmpty { "Anonymous Patient" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            ) 
        },
        supportingContent = { Text("Injury: ${patient.injuryType}") },
        overlineContent = { Text("Patient Name", style = MaterialTheme.typography.labelSmall) },
        trailingContent = { 
            Column {
                Text("Last Session", style = MaterialTheme.typography.labelSmall)
                Text(patient.lastSessionDate?.toString()?.take(10) ?: "None") 
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
