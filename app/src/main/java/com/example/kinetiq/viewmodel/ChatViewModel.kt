package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.Message
import com.example.kinetiq.models.MessageType
import com.example.kinetiq.repository.ChatRepository
import com.example.kinetiq.utils.InputSanitizer
import com.example.kinetiq.utils.RateLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepo: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    private var currentPatientId: String? = null
    
    // Rate limit sending messages: 10 messages per minute
    private val messageRateLimiter = RateLimiter(maxAttempts = 10, windowMillis = TimeUnit.MINUTES.toMillis(1))

    fun setPatient(patientId: String) {
        if (currentPatientId == patientId) return
        currentPatientId = patientId
        observeMessages(patientId)
    }

    private fun observeMessages(patientId: String) {
        chatRepo.getMessages(patientId)
            .onStart { _uiState.update { it.copy(isLoading = true) } }
            .onEach { result ->
                result.onSuccess { msgs ->
                    _uiState.update { it.copy(messages = msgs, isLoading = false) }
                }.onFailure { err ->
                    _uiState.update { it.copy(error = err.message, isLoading = false) }
                }
            }.launchIn(viewModelScope)
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        if (content.length > 2000) {
            _uiState.update { it.copy(error = "Message is too long (max 2000 characters)") }
            return
        }

        if (!messageRateLimiter.shouldAllow()) {
            _uiState.update { it.copy(error = "Sending messages too fast. Please wait a moment.") }
            return
        }

        val sanitizedContent = InputSanitizer.sanitizeString(content, 2000)
        val patientId = currentPatientId ?: return
        
        viewModelScope.launch {
            chatRepo.sendMessage(patientId, sanitizedContent)
        }
    }
}
