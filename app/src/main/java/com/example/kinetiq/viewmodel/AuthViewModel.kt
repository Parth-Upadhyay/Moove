package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.UserRole
import com.example.kinetiq.repository.AuthRepository
import com.example.kinetiq.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val userRole: UserRole? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            when (val result = repository.login(email, pass)) {
                is AuthResult.Success -> {
                    _uiState.value = AuthUiState(isSuccess = true, userRole = result.role)
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState(error = result.message)
                }
            }
        }
    }

    fun signUp(email: String, pass: String, role: UserRole, displayName: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            when (val result = repository.signUp(email, pass, role, displayName)) {
                is AuthResult.Success -> {
                    _uiState.value = AuthUiState(
                        isSuccess = true, 
                        userRole = result.role,
                        message = "Verification email sent. Please check your inbox."
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState(error = result.message)
                }
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            when (val result = repository.resetPassword(email)) {
                is AuthResult.Success -> {
                    _uiState.value = AuthUiState(message = "Password reset email sent!")
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState(error = result.message)
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState()
    }
}
