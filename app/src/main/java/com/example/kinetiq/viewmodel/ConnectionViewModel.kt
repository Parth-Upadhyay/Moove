package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.ConnectionRequest
import com.example.kinetiq.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    fun observeIncomingRequests() {
        // Cancel existing job if any to ensure we are using the latest auth state
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            repository.sendRequestToDoctor(doctorEmail)
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
