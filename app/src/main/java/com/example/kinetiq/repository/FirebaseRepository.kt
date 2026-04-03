package com.example.kinetiq.repository

import android.util.Log
import com.example.kinetiq.models.ClinicalPrescription
import com.example.kinetiq.models.Patient
import com.example.kinetiq.models.SessionResult
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val authRepo: AuthRepository
) {
    private val TAG = "FirebaseRepo"

    fun getDoctorPatients(): Flow<Result<List<Patient>>> = callbackFlow {
        val doctorId = authRepo.getCurrentUserId()
        if (doctorId == null) {
            trySend(Result.failure(Exception("Not authenticated")))
            close()
            return@callbackFlow
        }

        val registration = db.collection("patients")
            .whereEqualTo("doctorId", doctorId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                
                val patients = mutableListOf<Patient>()
                snapshot?.documents?.forEach { doc ->
                    try {
                        doc.toObject(Patient::class.java)?.let { 
                            it.id = doc.id 
                            patients.add(it) 
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing patient ${doc.id}", e)
                    }
                }
                trySend(Result.success(patients))
            }
        awaitClose { registration.remove() }
    }

    fun getAlertSessions(): Flow<Result<List<SessionResult>>> = callbackFlow {
        val doctorId = authRepo.getCurrentUserId()
        if (doctorId == null) {
            trySend(Result.failure(Exception("Not authenticated")))
            close()
            return@callbackFlow
        }

        val registration = db.collection("sessions")
            .whereEqualTo("doctor_id", doctorId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                
                val alertSessions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(SessionResult::class.java)?.takeIf { it.doctor_alerts.isNotEmpty() }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing session ${doc.id}", e)
                        null
                    }
                }?.sortedByDescending { it.timestamp_end } ?: emptyList()

                trySend(Result.success(alertSessions))
            }
        awaitClose { registration.remove() }
    }

    fun getPatientSessions(patientId: String): Flow<Result<List<SessionResult>>> = callbackFlow {
        val registration = db.collection("sessions")
            .whereEqualTo("patient_id", patientId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val sessions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(SessionResult::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.timestamp_end } ?: emptyList()
                trySend(Result.success(sessions))
            }
        awaitClose { registration.remove() }
    }

    suspend fun updatePrescription(patientId: String, prescription: ClinicalPrescription): Result<Unit> {
        return try {
            db.collection("patients").document(patientId)
                .update("clinicalPrescription", prescription)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating prescription", e)
            Result.failure(e)
        }
    }

    suspend fun updatePatientNotes(patientId: String, age: Int, sex: String, medicalNotes: String, injuryType: String): Result<Unit> {
        return try {
            db.collection("patients").document(patientId)
                .update(
                    mapOf(
                        "age" to age,
                        "sex" to sex,
                        "medicalNotes" to medicalNotes,
                        "injuryType" to injuryType
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notes", e)
            Result.failure(e)
        }
    }

    fun getPatientDetails(patientId: String): Flow<Result<Patient>> = callbackFlow {
        val registration = db.collection("patients").document(patientId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                try {
                    val patient = snapshot?.toObject(Patient::class.java)
                    if (patient != null) {
                        patient.id = snapshot.id
                        trySend(Result.success(patient))
                    } else {
                        trySend(Result.failure(Exception("Patient not found")))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing patient details", e)
                    trySend(Result.failure(e))
                }
            }
        awaitClose { registration.remove() }
    }
}
