package com.example.kinetiq.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kinetiq.models.Achievement
import com.example.kinetiq.ui.theme.*
import com.example.kinetiq.viewmodel.AchievementsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = CreamyWhite,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Milestones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = CreamyWhite)
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TealPrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Text(
                        "Track your recovery progress and earn rewards for staying consistent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(uiState.achievements) { achievement ->
                    AchievementItem(
                        achievement = achievement,
                        onClaim = { viewModel.claimAchievement(achievement) }
                    )
                }
            }
        }
    }
}

@Composable
fun AchievementItem(achievement: Achievement, onClaim: () -> Unit) {
    val isCompleted = achievement.progress >= achievement.targetValue
    val iconColor = if (achievement.isClaimed) Color(0xFF4CAF50) else if (isCompleted) TealPrimary else TextDisabled
    
    KinetiqCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = iconColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (achievement.isClaimed) Icons.Default.CheckCircle else Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = iconColor
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(achievement.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                
                KinetiqProgressBar(progress = achievement.progress.toFloat() / achievement.targetValue.toFloat())
                
                Spacer(Modifier.height(4.dp))
                Text(
                    "${achievement.progress} / ${achievement.targetValue}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDisabled,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
        
        if (isCompleted && !achievement.isClaimed) {
            Spacer(Modifier.height(16.dp))
            KinetiqPrimaryButton(
                onClick = onClaim,
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Text("Claim Reward", style = MaterialTheme.typography.labelLarge)
            }
        } else if (achievement.isClaimed) {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Reward Claimed", 
                    color = Color(0xFF4CAF50), 
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
