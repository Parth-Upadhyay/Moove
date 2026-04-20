package com.example.kinetiq.models

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class UserRole { DOCTOR, PATIENT }

@IgnoreExtraProperties
data class UserProfile(
    var id: String = "", 
    var email: String = "",
    var role: String = "PATIENT",
    var displayName: String = ""
)

@IgnoreExtraProperties
data class Doctor(
    var id: String = "",
    var specialization: String = "General",
    var clinicName: String = "",
    var patientIds: List<String> = emptyList()
)

@IgnoreExtraProperties
data class Patient(
    var id: String = "",
    var doctorId: String = "",
    var injuryType: String = "None",
    var displayName: String = "",
    var clinicalPrescription: ClinicalPrescription? = null,
    var lastSessionDate: Date? = null,
    var age: Int = 0,
    var sex: String = "",
    var medicalNotes: String = ""
)

@IgnoreExtraProperties
data class MedicalNote(
    var id: String = "",
    var patientId: String = "",
    var doctorId: String = "",
    var title: String = "",
    var content: String = "",
    @ServerTimestamp var timestamp: Date? = Date() // Default to current date for local latency compensation
)

@IgnoreExtraProperties
data class ClinicalPrescription(
    var exercises: List<PrescribedExercise> = emptyList(),
    var frequencyPerWeek: Int = 0,
    @ServerTimestamp var assignedAt: Date? = null,
    var notes: String = ""
)

@IgnoreExtraProperties
data class PrescribedExercise(
    var exerciseId: String = "",
    var exerciseName: String = "",
    var sets: Int = 0,
    var reps: Int = 0,
    var isActive: Boolean = true
)

@IgnoreExtraProperties
data class ConnectionRequest(
    var id: String = "",
    var fromId: String = "",
    var fromName: String = "",
    var fromEmail: String = "",
    var toId: String = "",
    var toEmail: String = "",
    var status: String = "PENDING",
    @ServerTimestamp var timestamp: Date? = null
)
