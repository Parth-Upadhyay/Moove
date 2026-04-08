package com.example.kinetiq.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.example.kinetiq.ui.theme.*
import com.example.kinetiq.viewmodel.DoctorDashboardViewModel

@Composable
fun PatientListScreen(
    viewModel: DoctorDashboardViewModel,
    onPatientClick: (String) -> Unit,
    onChatClick: (String, String) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MooveBackground),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        item {
            Text(
                "My Patients",
                style = MaterialTheme.typography.headlineSmall,
                color = MooveOnBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        
        items(state.patients) { patient ->
            PatientItem(
                patient = patient, 
                onClick = { onPatientClick(patient.id) },
                onChatClick = { onChatClick(patient.id, patient.displayName) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        if (state.patients.isEmpty() && !state.isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Text("No patients assigned yet.", color = MooveOnSurfaceVariant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun PatientItem(patient: Patient, onClick: () -> Unit, onChatClick: () -> Unit) {
    MooveCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar
            Surface(
                color = MoovePrimary.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = patient.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MoovePrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patient.displayName.ifEmpty { "Anonymous Patient" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MooveOnBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = patient.injuryType.ifEmpty { "General Recovery" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MooveOnSurfaceVariant
                )
            }
            
            // Chat Button
            IconButton(onClick = onChatClick) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Chat",
                    tint = MoovePrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(4.dp))
            
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
