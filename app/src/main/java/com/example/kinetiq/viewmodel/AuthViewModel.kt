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
    val userRole: UserRole? = null,
    val isSuccess: Boolean = false,
    val verificationSent: Boolean = false,
    val isUnverified: Boolean = false,
    val passwordResetSent: Boolean = false
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
                is AuthResult.Unverified -> {
                    _uiState.value = AuthUiState(
                        error = "Email not verified. Please check your inbox.",
                        isUnverified = true
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState(error = result.message)
                }
                else -> {}
            }
        }
    }

    fun signUp(email: String, pass: String, role: UserRole, displayName: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            when (val result = repository.signUp(email, pass, role, displayName)) {
                is AuthResult.VerificationSent -> {
                    _uiState.value = AuthUiState(verificationSent = true)
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState(error = result.message)
                }
                else -> {}
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.resendVerification()) {
                is AuthResult.VerificationSent -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        verificationSent = true,
                        error = "Verification email resent!"
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
                else -> {}
            }
        }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter your email address")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.resetPassword(email)) {
                is AuthResult.PasswordResetSent -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        passwordResetSent = true,
                        error = "Password reset link sent to your email!"
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
                else -> {}
            }
        }
    }

    /**
     * Critical for Production: Clears the successful login state.
     * Must be called when logging out to prevent the UI from 
     * immediately re-triggering the dashboard on re-entry.
     */
    fun resetState() {
        _uiState.value = AuthUiState()
    }
}
