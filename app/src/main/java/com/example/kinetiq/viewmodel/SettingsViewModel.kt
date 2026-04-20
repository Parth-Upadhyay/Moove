package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.NotificationSettings
import com.example.kinetiq.utils.RateLimiter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsUiState(
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Rate limit settings updates: 10 per minute (increased for better responsiveness)
    private val settingsRateLimiter = RateLimiter(maxAttempts = 10, windowMillis = TimeUnit.MINUTES.toMillis(1))

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val doc = db.collection("settings").document(uid).get().await()
                val settings = doc.toObject(NotificationSettings::class.java) ?: NotificationSettings(userId = uid)
                _uiState.update { it.copy(notificationSettings = settings, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        val currentSettings = _uiState.value.notificationSettings
        val settings = currentSettings.copy(isEnabled = enabled)
        // Optimistic update
        _uiState.update { it.copy(notificationSettings = settings) }
        saveSettings(settings)
    }

    fun toggleVoiceFeedback(enabled: Boolean) {
        val currentSettings = _uiState.value.notificationSettings
        val settings = currentSettings.copy(isVoiceFeedbackEnabled = enabled)
        // Optimistic update
        _uiState.update { it.copy(notificationSettings = settings) }
        saveSettings(settings)
    }
    
    fun toggleVoiceCount(enabled: Boolean) {
        val currentSettings = _uiState.value.notificationSettings
        val settings = currentSettings.copy(isVoiceCountEnabled = enabled)
        // Optimistic update
        _uiState.update { it.copy(notificationSettings = settings) }
        saveSettings(settings)
    }

    fun addReminderTime(time: String) {
        val currentSettings = _uiState.value.notificationSettings
        val currentTimes = currentSettings.reminderTimes.toMutableList()
        if (!currentTimes.contains(time)) {
            currentTimes.add(time)
            val newSettings = currentSettings.copy(reminderTimes = currentTimes)
            // Optimistic update
            _uiState.update { it.copy(notificationSettings = newSettings) }
            saveSettings(newSettings)
        }
    }

    fun removeReminderTime(time: String) {
        val currentSettings = _uiState.value.notificationSettings
        val currentTimes = currentSettings.reminderTimes.toMutableList()
        if (currentTimes.remove(time)) {
            val newSettings = currentSettings.copy(reminderTimes = currentTimes)
            // Optimistic update
            _uiState.update { it.copy(notificationSettings = newSettings) }
            saveSettings(newSettings)
        }
    }

    private fun saveSettings(settings: NotificationSettings) {
        if (!settingsRateLimiter.shouldAllow()) {
            _uiState.update { it.copy(error = "Too many updates. Please wait a moment.") }
            return
        }

        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("settings").document(uid).set(settings).await()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save: ${e.message}") }
            }
        }
    }
}
