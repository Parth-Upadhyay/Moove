package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.Achievement
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AchievementsUiState(
    val achievements: List<Achievement> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AchievementsViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    init {
        loadAchievements()
    }

    fun loadAchievements() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // In a real app, you'd fetch defined achievements and user progress separately
                // For this demo, we'll use some hardcoded ones and check user progress in Firestore
                val userDoc = db.collection("users").document(uid).get().await()
                val sessionCount = userDoc.getLong("totalSessions")?.toInt() ?: 0
                val streak = userDoc.getLong("streak")?.toInt() ?: 0

                val list = listOf(
                    Achievement("1", "First Step", "Complete your first exercise session", "", 1, progress = if(sessionCount >= 1) 1 else 0),
                    Achievement("2", "Consistency King", "Complete 5 sessions", "", 5, progress = sessionCount.coerceAtMost(5)),
                    Achievement("3", "On Fire", "Maintain a 3-day streak", "", 3, progress = streak.coerceAtMost(3))
                )
                
                // Mark as claimed if already in a 'claimedAchievements' collection
                val claimed = db.collection("users").document(uid).collection("claimedAchievements").get().await()
                val claimedIds = claimed.documents.map { it.id }
                
                val finalIcons = list.map { it.copy(isClaimed = claimedIds.contains(it.id)) }

                _uiState.value = AchievementsUiState(achievements = finalIcons, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = AchievementsUiState(error = e.message, isLoading = false)
            }
        }
    }

    fun claimAchievement(achievement: Achievement) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("claimedAchievements")
                    .document(achievement.id).set(mapOf("claimedAt" to System.currentTimeMillis()))
                    .await()
                loadAchievements()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to claim: ${e.message}")
            }
        }
    }
}
