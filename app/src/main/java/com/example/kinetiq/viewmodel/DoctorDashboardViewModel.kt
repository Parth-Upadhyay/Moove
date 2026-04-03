package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.ClinicalPrescription
import com.example.kinetiq.models.Patient
import com.example.kinetiq.models.SessionResult
import com.example.kinetiq.repository.FirebaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = false,
    val alertSessions: List<SessionResult> = emptyList(),
    val patients: List<Patient> = emptyList(),
    val selectedPatientSessions: List<SessionResult> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class DoctorDashboardViewModel @Inject constructor(
    private val repository: FirebaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        fetchDashboardData()
    }

    private fun fetchDashboardData() {
        _uiState.update { it.copy(isLoading = true) }
        
        combine(
            repository.getAlertSessions(),
            repository.getDoctorPatients()
        ) { alerts, patients ->
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    alertSessions = alerts.getOrDefault(emptyList()),
                    patients = patients.getOrDefault(emptyList()),
                    errorMessage = (alerts.exceptionOrNull() ?: patients.exceptionOrNull())?.message
                )
            }
        }.launchIn(viewModelScope)
    }

    fun fetchPatientSessions(patientId: String) {
        viewModelScope.launch {
            repository.getPatientSessions(patientId).collect { result ->
                _uiState.update { it.copy(selectedPatientSessions = result.getOrDefault(emptyList())) }
            }
        }
    }

    fun updatePrescription(patientId: String, prescription: ClinicalPrescription) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.updatePrescription(patientId, prescription)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "Prescription saved") }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun updatePatientNotes(patientId: String, age: Int, sex: String, medicalNotes: String, injuryType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.updatePatientNotes(patientId, age, sex, medicalNotes, injuryType)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, successMessage = "this saved") }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
