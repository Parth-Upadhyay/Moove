package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.UserRole
import com.example.kinetiq.repository.AuthRepository
import com.example.kinetiq.repository.AuthResult
import com.example.kinetiq.utils.InputSanitizer
import com.example.kinetiq.utils.RateLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
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

    // Reduced for testing: 999 attempts per second (effectively disabled)
    private val authRateLimiter = RateLimiter(maxAttempts = 999, windowMillis = 1000L)

    private fun checkRateLimit(): Boolean {
        if (!authRateLimiter.shouldAllow()) {
            val waitMins = TimeUnit.MILLISECONDS.toMinutes(authRateLimiter.getRemainingTimeMillis()) + 1
            _uiState.value = AuthUiState(error = "Too many attempts. Please try again in $waitMins minutes.")
            return false
        }
        return true
    }

    fun login(email: String, pass: String) {
        if (!InputSanitizer.isValidEmail(email)) {
            _uiState.value = AuthUiState(error = "Invalid email format")
            return
        }
        if (!checkRateLimit()) return
        
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            when (val result = repository.login(email.trim(), pass)) {
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
        if (!InputSanitizer.isValidEmail(email)) {
            _uiState.value = AuthUiState(error = "Invalid email format")
            return
        }
        if (!InputSanitizer.isValidPassword(pass)) {
            _uiState.value = AuthUiState(error = "Password must be at least 8 characters and contain both letters and digits")
            return
        }
        if (displayName.length > 100) {
            _uiState.value = AuthUiState(error = "Name is too long")
            return
        }
        
        if (!checkRateLimit()) return

        val sanitizedName = InputSanitizer.sanitizeString(displayName, 100)

        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            when (val result = repository.signUp(email.trim(), pass, role, sanitizedName)) {
                is AuthResult.Success -> {
                    repository.logout()
                    _uiState.value = AuthUiState(
                        isSuccess = false,
                        message = "Account created! A verification email has been sent to $email. Please verify your email before logging in."
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = AuthUiState(error = result.message)
                }
            }
        }
    }

    fun resetPassword(email: String) {
        if (!InputSanitizer.isValidEmail(email)) {
            _uiState.value = AuthUiState(error = "Invalid email format")
            return
        }
        if (!checkRateLimit()) return

        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            when (val result = repository.resetPassword(email.trim())) {
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
