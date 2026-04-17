package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.AiAlert
import com.example.kinetiq.models.ClinicalPrescription
import com.example.kinetiq.models.Patient
import com.example.kinetiq.models.SessionResult
import com.example.kinetiq.repository.AiRepository
import com.example.kinetiq.repository.AuthRepository
import com.example.kinetiq.repository.FirebaseRepository
import com.example.kinetiq.utils.InputSanitizer
import com.example.kinetiq.utils.RateLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isAiLoading: Boolean = false,
    val alertSessions: List<SessionResult> = emptyList(),
    val aiAlerts: List<AiAlert> = emptyList(),
    val patients: List<Patient> = emptyList(),
    val selectedPatientSessions: List<SessionResult> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val canRefreshAi: Boolean = true
)

@HiltViewModel
class DoctorDashboardViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val aiRepository: AiRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val updateRateLimiter = RateLimiter(maxAttempts = 10, windowMillis = TimeUnit.MINUTES.toMillis(5))

    init {
        fetchDashboardData()
    }

    private fun fetchDashboardData() {
        val doctorId = authRepo.getCurrentUserId() ?: return
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            val canRefresh = aiRepository.canDoctorRefresh(doctorId)
            _uiState.update { it.copy(canRefreshAi = canRefresh) }
        }

        combine(
            repository.getAlertSessions(),
            repository.getDoctorPatients(),
            aiRepository.getAiAlerts(doctorId)
        ) { alerts, patients, aiAlerts ->
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    alertSessions = alerts.getOrDefault(emptyList()),
                    patients = patients.getOrDefault(emptyList()),
                    aiAlerts = aiAlerts.getOrDefault(emptyList()),
                    errorMessage = (alerts.exceptionOrNull() ?: patients.exceptionOrNull() ?: aiAlerts.exceptionOrNull())?.message
                )
            }
        }.launchIn(viewModelScope)
    }

    fun refreshAiSummaries() {
        val doctorId = authRepo.getCurrentUserId() ?: return
        val patients = uiState.value.patients
        
        if (patients.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAiLoading = true, errorMessage = null) }
            val result = aiRepository.generateBulkSummaries(doctorId, patients)
            
            if (result.isSuccess) {
                _uiState.update { 
                    it.copy(
                        isAiLoading = false, 
                        successMessage = "Successfully generated ${result.getOrNull()} summaries",
                        // canRefreshAi = false // Disabled for testing: allow immediate re-refresh
                    ) 
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isAiLoading = false, 
                        errorMessage = result.exceptionOrNull()?.message 
                    ) 
                }
            }
        }
    }

    fun fetchPatientSessions(patientId: String) {
        viewModelScope.launch {
            repository.getPatientSessions(patientId).collect { result ->
                _uiState.update { it.copy(selectedPatientSessions = result.getOrDefault(emptyList())) }
            }
        }
    }

    fun updatePrescription(patientId: String, prescription: ClinicalPrescription) {
        if (!updateRateLimiter.shouldAllow()) {
            _uiState.update { it.copy(errorMessage = "Too many updates. Please wait a moment.") }
            return
        }

        val sanitizedPrescription = prescription.copy(
            notes = InputSanitizer.sanitizeString(prescription.notes, 1000)
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.updatePrescription(patientId, sanitizedPrescription)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Prescription saved") }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun updatePatientNotes(patientId: String, age: Int, sex: String, medicalNotes: String, injuryType: String) {
        if (!updateRateLimiter.shouldAllow()) {
            _uiState.update { it.copy(errorMessage = "Too many updates. Please wait a moment.") }
            return
        }

        if (age !in 0..150) {
            _uiState.update { it.copy(errorMessage = "Invalid age") }
            return
        }

        val sanitizedNotes = InputSanitizer.sanitizeString(medicalNotes, 2000)
        val sanitizedInjury = InputSanitizer.sanitizeString(injuryType, 100)
        val sanitizedSex = InputSanitizer.sanitizeString(sex, 20)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.updatePatientNotes(patientId, age, sanitizedSex, sanitizedNotes, sanitizedInjury)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Information saved") }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
