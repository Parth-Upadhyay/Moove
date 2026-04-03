package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.Appointment
import com.example.kinetiq.models.AppointmentStatus
import com.example.kinetiq.repository.AppointmentRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class AppointmentUiState(
    val appointments: List<Appointment> = emptyList(),
    val bookedSlots: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class AppointmentViewModel @Inject constructor(
    private val repository: AppointmentRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppointmentUiState())
    val uiState = _uiState.asStateFlow()

    private var patientAppointmentsJob: Job? = null
    private var doctorAppointmentsJob: Job? = null
    private var bookedSlotsJob: Job? = null

    fun loadAppointmentsForPatient() {
        val userId = auth.currentUser?.uid ?: return
        patientAppointmentsJob?.cancel()
        patientAppointmentsJob = viewModelScope.launch {
            repository.getAppointmentsForPatient(userId)
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { appointments ->
                    _uiState.update { it.copy(appointments = appointments, isLoading = false) }
                }
        }
    }

    fun loadAppointmentsForDoctor() {
        val userId = auth.currentUser?.uid ?: return
        doctorAppointmentsJob?.cancel()
        doctorAppointmentsJob = viewModelScope.launch {
            repository.getAppointmentsForDoctor(userId)
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { appointments ->
                    _uiState.update { it.copy(appointments = appointments, isLoading = false) }
                }
        }
    }

    fun loadBookedSlots(doctorId: String, date: Date) {
        bookedSlotsJob?.cancel()
        bookedSlotsJob = viewModelScope.launch {
            repository.getAppointmentsForDoctor(doctorId)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { appointments ->
                    val slots = appointments.filter {
                        isSameDay(it.date, date) &&
                        it.status != AppointmentStatus.REJECTED &&
                        it.status != AppointmentStatus.CANCELLED
                    }.map { it.timeSlot }
                    _uiState.update { it.copy(bookedSlots = slots) }
                }
        }
    }

    private fun isSameDay(d1: Date?, d2: Date?): Boolean {
        if (d1 == null || d2 == null) return false
        val cal1 = Calendar.getInstance().apply { time = d1 }
        val cal2 = Calendar.getInstance().apply { time = d2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun requestAppointment(doctorId: String, doctorName: String, date: Date, timeSlot: String, note: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            repository.requestAppointment(doctorId, doctorName, date, timeSlot, note)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, successMessage = "Appointment requested successfully!") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun updateStatus(appointmentId: String, status: AppointmentStatus) {
        viewModelScope.launch {
            repository.updateAppointmentStatus(appointmentId, status)
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Failed to update status: ${e.message}") }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
