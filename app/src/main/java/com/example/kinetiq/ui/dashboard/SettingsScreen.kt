package com.example.kinetiq.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kinetiq.ui.theme.*
import com.example.kinetiq.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MooveBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
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
        // Fade in content once loaded to avoid flickering
        AnimatedVisibility(
            visible = !uiState.isLoading || uiState.notificationSettings.userId.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            modifier = Modifier.padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                
                MooveCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reminders", style = MaterialTheme.typography.titleMedium, color = MooveOnBackground, fontWeight = FontWeight.Bold)
                            Text(
                                "Daily exercise alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MooveOnSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.notificationSettings.isEnabled,
                            onCheckedChange = { viewModel.toggleNotifications(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MoovePrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = TextDisabled.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                MooveCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingSwitchRow(
                            title = "Voice Feedback",
                            subtitle = "Corrections and tips",
                            checked = uiState.notificationSettings.isVoiceFeedbackEnabled,
                            onCheckedChange = { viewModel.toggleVoiceFeedback(it) }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = CardBorder.copy(alpha = 0.5f))
                        
                        SettingSwitchRow(
                            title = "Voice Counting",
                            subtitle = "Rep and rest countdowns",
                            checked = uiState.notificationSettings.isVoiceCountEnabled,
                            onCheckedChange = { viewModel.toggleVoiceCount(it) }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                if (uiState.notificationSettings.isEnabled) {
                    ReminderTimesSection(
                        times = uiState.notificationSettings.reminderTimes,
                        onAddClick = { showTimePicker = true },
                        onRemoveClick = { viewModel.removeReminderTime(it) }
                    )
                }
            }
        }
        
        if (uiState.isLoading && uiState.notificationSettings.userId.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MoovePrimary)
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onTimeSelected = { hour, minute ->
                val time = String.format("%02d:%02d", hour, minute)
                viewModel.addReminderTime(time)
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MooveOnBackground, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MooveOnSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MoovePrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = TextDisabled.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun ReminderTimesSection(
    times: List<String>,
    onAddClick: () -> Unit,
    onRemoveClick: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Reminder Times", style = MaterialTheme.typography.titleMedium, color = MooveOnBackground, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = onAddClick,
                modifier = Modifier.background(MoovePrimary.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Time", tint = MoovePrimary)
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 400.dp)) {
            items(times) { time ->
                MooveCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(time, style = MaterialTheme.typography.titleLarge, color = MooveOnBackground, fontWeight = FontWeight.Medium)
                        IconButton(onClick = { onRemoveClick(time) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE57373))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onTimeSelected: (Int, Int) -> Unit
) {
    var hourStr by remember { mutableStateOf("09") }
    var minStr by remember { mutableStateOf("00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MooveSurface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Set Reminder Time", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                MooveTextField(
                    value = hourStr,
                    onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) hourStr = it },
                    label = "Hour",
                    modifier = Modifier.width(80.dp)
                )
                Text(" : ", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp))
                MooveTextField(
                    value = minStr,
                    onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) minStr = it },
                    label = "Min",
                    modifier = Modifier.width(80.dp)
                )
            }
        },
        confirmButton = {
            MoovePrimaryButton(
                onClick = { 
                    val h = hourStr.toIntOrNull() ?: 9
                    val m = minStr.toIntOrNull() ?: 0
                    onTimeSelected(h, m) 
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MooveOnSurfaceVariant)
            }
        }
    )
}
