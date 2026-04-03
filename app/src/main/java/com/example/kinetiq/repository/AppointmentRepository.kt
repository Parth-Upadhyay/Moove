package com.example.kinetiq.repository

import com.example.kinetiq.models.Appointment
import com.example.kinetiq.models.AppointmentStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppointmentRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    fun getAppointmentsForPatient(patientId: String): Flow<List<Appointment>> = callbackFlow {
        val subscription = firestore.collection("appointments")
            .whereEqualTo("patientId", patientId)
            // Removed .orderBy("date", Query.Direction.DESCENDING) to avoid index error
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val appointments = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Appointment::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.date } ?: emptyList() // Sort in memory
                
                trySend(appointments)
            }
        awaitClose { subscription.remove() }
    }

    fun getAppointmentsForDoctor(doctorId: String): Flow<List<Appointment>> = callbackFlow {
        val subscription = firestore.collection("appointments")
            .whereEqualTo("doctorId", doctorId)
            // Removed .orderBy("date", Query.Direction.DESCENDING) to avoid index error
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val appointments = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Appointment::class.java)?.copy(id = doc.id)
                }?.sortedByDescending { it.date } ?: emptyList() // Sort in memory

                trySend(appointments)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun requestAppointment(doctorId: String, doctorName: String, date: Date, timeSlot: String, note: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Not authenticated"))
            val userDoc = firestore.collection("users").document(userId).get().await()
            val patientName = userDoc.getString("displayName") ?: "Patient"

            val appointment = Appointment(
                patientId = userId,
                patientName = patientName,
                doctorId = doctorId,
                doctorName = doctorName,
                date = date,
                timeSlot = timeSlot,
                status = AppointmentStatus.PENDING,
                note = note
            )
            firestore.collection("appointments").add(appointment).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAppointmentStatus(appointmentId: String, status: AppointmentStatus): Result<Unit> {
        return try {
            firestore.collection("appointments").document(appointmentId)
                .update("status", status)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
