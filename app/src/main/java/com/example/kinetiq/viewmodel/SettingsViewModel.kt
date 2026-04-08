package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.NotificationSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val doc = db.collection("settings").document(uid).get().await()
                val settings = doc.toObject(NotificationSettings::class.java) ?: NotificationSettings(userId = uid)
                _uiState.value = SettingsUiState(notificationSettings = settings, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = SettingsUiState(error = e.message, isLoading = false)
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        val settings = _uiState.value.notificationSettings.copy(isEnabled = enabled)
        saveSettings(settings)
    }

    fun addReminderTime(time: String) {
        val currentTimes = _uiState.value.notificationSettings.reminderTimes.toMutableList()
        if (!currentTimes.contains(time)) {
            currentTimes.add(time)
            saveSettings(_uiState.value.notificationSettings.copy(reminderTimes = currentTimes))
        }
    }

    fun removeReminderTime(time: String) {
        val currentTimes = _uiState.value.notificationSettings.reminderTimes.toMutableList()
        currentTimes.remove(time)
        saveSettings(_uiState.value.notificationSettings.copy(reminderTimes = currentTimes))
    }

    private fun saveSettings(settings: NotificationSettings) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("settings").document(uid).set(settings).await()
                _uiState.value = _uiState.value.copy(notificationSettings = settings)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to save: ${e.message}")
            }
        }
    }
}
