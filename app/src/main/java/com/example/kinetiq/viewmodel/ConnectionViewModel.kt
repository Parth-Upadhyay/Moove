package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.ConnectionRequest
import com.example.kinetiq.repository.ConnectionRepository
import com.example.kinetiq.utils.InputSanitizer
import com.example.kinetiq.utils.RateLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class ConnectionUiState(
    val incomingRequests: List<ConnectionRequest> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val repository: ConnectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState = _uiState.asStateFlow()
    
    private var observationJob: Job? = null

    // Rate limit sending connection requests: 5 per 15 minutes
    private val requestRateLimiter = RateLimiter(maxAttempts = 5, windowMillis = TimeUnit.MINUTES.toMillis(15))

    fun observeIncomingRequests() {
        observationJob?.cancel()
        observationJob = repository.getIncomingRequestsForDoctor()
            .onStart { _uiState.update { it.copy(isLoading = true, error = null) } }
            .onEach { result ->
                result.onSuccess { requests ->
                    _uiState.update { it.copy(incomingRequests = requests, isLoading = false) }
                }.onFailure { err ->
                    _uiState.update { it.copy(error = err.message, isLoading = false) }
                }
            }.launchIn(viewModelScope)
    }

    fun sendRequest(doctorEmail: String) {
        val trimmedEmail = doctorEmail.trim()
        if (!InputSanitizer.isValidEmail(trimmedEmail)) {
            _uiState.update { it.copy(error = "Invalid email format") }
            return
        }

        if (!requestRateLimiter.shouldAllow()) {
            _uiState.update { it.copy(error = "Too many requests. Please wait before trying again.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            repository.sendRequestToDoctor(trimmedEmail)
                .onSuccess { 
                    _uiState.update { it.copy(isLoading = false, successMessage = "Request sent successfully!") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoading = false, error = err.message) }
                }
        }
    }

    fun acceptRequest(request: ConnectionRequest) {
        viewModelScope.launch {
            repository.acceptRequest(request)
                .onFailure { err ->
                    _uiState.update { it.copy(error = "Failed to accept: ${err.message}") }
                }
        }
    }

    fun rejectRequest(requestId: String) {
        viewModelScope.launch {
            repository.rejectRequest(requestId)
                .onFailure { err ->
                    _uiState.update { it.copy(error = "Failed to reject: ${err.message}") }
                }
        }
    }
    
    fun clearStatusMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
