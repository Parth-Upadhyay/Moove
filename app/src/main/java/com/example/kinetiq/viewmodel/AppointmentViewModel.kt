package com.example.kinetiq.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.Appointment
import com.example.kinetiq.models.AppointmentStatus
import com.example.kinetiq.repository.AppointmentRepository
import com.example.kinetiq.utils.InputSanitizer
import com.example.kinetiq.utils.NotificationHelper
import com.example.kinetiq.utils.RateLimiter
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
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
    application: Application,
    private val repository: AppointmentRepository,
    private val auth: FirebaseAuth
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppointmentUiState())
    val uiState = _uiState.asStateFlow()

    private var patientAppointmentsJob: Job? = null
    private var doctorAppointmentsJob: Job? = null
    private var bookedSlotsJob: Job? = null

    private val appointmentRateLimiter = RateLimiter(maxAttempts = 3, windowMillis = TimeUnit.MINUTES.toMillis(10))

    fun loadAppointmentsForPatient() {
        val userId = auth.currentUser?.uid ?: return
        patientAppointmentsJob?.cancel()
        patientAppointmentsJob = viewModelScope.launch {
            repository.getAppointmentsForPatient(userId)
                .onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { appointments ->
                    val oldAppointments = _uiState.value.appointments
                    _uiState.update { it.copy(appointments = appointments, isLoading = false) }
                    
                    // Notify if an appointment was accepted
                    appointments.forEach { newAppt ->
                        val oldAppt = oldAppointments.find { it.id == newAppt.id }
                        if (oldAppt != null && oldAppt.status == AppointmentStatus.PENDING && newAppt.status == AppointmentStatus.ACCEPTED) {
                            NotificationHelper.showNotification(
                                getApplication(),
                                "Appointment Confirmed",
                                "Your appointment with Dr. ${newAppt.doctorName} on ${newAppt.timeSlot} has been accepted."
                            )
                        }
                    }
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
                    val oldAppointments = _uiState.value.appointments
                    _uiState.update { it.copy(appointments = appointments, isLoading = false) }
                    
                    // Notify doctor of new pending requests
                    if (oldAppointments.isNotEmpty()) {
                        val newRequests = appointments.filter { new -> 
                            new.status == AppointmentStatus.PENDING && oldAppointments.none { it.id == new.id }
                        }
                        if (newRequests.isNotEmpty()) {
                            NotificationHelper.showNotification(
                                getApplication(),
                                "New Appointment Request",
                                "You have ${newRequests.size} new appointment request(s)."
                            )
                        }
                    }
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
        if (!appointmentRateLimiter.shouldAllow()) {
            _uiState.update { it.copy(error = "Too many appointment requests. Please wait a few minutes.") }
            return
        }

        if (note.length > 500) {
            _uiState.update { it.copy(error = "Note is too long (max 500 characters)") }
            return
        }

        val sanitizedNote = InputSanitizer.sanitizeString(note, 500)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, successMessage = null) }
            repository.requestAppointment(doctorId, doctorName, date, timeSlot, sanitizedNote)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, successMessage = "Appointment requested successfully!") }
                    NotificationHelper.showNotification(
                        getApplication(),
                        "Request Sent",
                        "Your appointment request for $timeSlot has been sent."
                    )
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
