package com.example.kinetiq.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Appointment(
    val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val date: Date = Date(),
    val timeSlot: String = "", // Added timeSlot
    val status: AppointmentStatus = AppointmentStatus.PENDING,
    val note: String = "",
    @ServerTimestamp
    val createdAt: Date? = null
)

enum class AppointmentStatus {
    PENDING, ACCEPTED, REJECTED, CANCELLED
}
