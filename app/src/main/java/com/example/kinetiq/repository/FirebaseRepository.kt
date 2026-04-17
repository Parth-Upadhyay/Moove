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
import java.text.SimpleDateFormat
import java.util.*
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

    /**
     * Fetches sessions that should be flagged as "Alerts" for the doctor.
     * Uses mathematical thresholds for pain increases, ROM drops, and low adherence.
     * Limits results to the past 2 days.
     */
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
                
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, -2)
                val twoDaysAgo = calendar.time

                val alertSessions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val session = doc.toObject(SessionResult::class.java) ?: return@mapNotNull null
                        
                        // Filter for sessions in the past 2 days only
                        val sessionDate = parseIsoDate(session.timestamp_end)
                        if (sessionDate == null || sessionDate.before(twoDaysAgo)) return@mapNotNull null

                        // 1. Pain Spike: Any reported pain significantly higher than pre-session
                        val maxPainDuringSession = session.pain_log.mapNotNull { it.level.toIntOrNull() }.maxOrNull() ?: 0
                        val postPain = session.pain_log.lastOrNull()?.level?.toIntOrNull() ?: 0
                        val painSpike = (maxOf(maxPainDuringSession, postPain) - session.pre_session.pain_score) > 2
                        
                        // 2. ROM Drop: Peak ROM decreased compared to last session
                        val romDropped = session.rom_trend.delta < 0
                        
                        // 3. Adherence Issue: Adherence rate below 60%
                        val lowAdherence = session.adherence.adherence_rate_30d < 0.6f
                        
                        // 4. Existing alerts or high absolute pain (either pre or during session)
                        val hasManualAlert = session.doctor_alerts.isNotEmpty()
                        val highAbsolutePain = session.pre_session.pain_score >= 8 || maxPainDuringSession >= 8 || postPain >= 8

                        if (painSpike || romDropped || lowAdherence || hasManualAlert || highAbsolutePain) {
                            session
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing session ${doc.id}", e)
                        null
                    }
                }?.sortedByDescending { it.timestamp_end } ?: emptyList()

                trySend(Result.success(alertSessions))
            }
        awaitClose { registration.remove() }
    }

    private fun parseIsoDate(isoString: String): Date? {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (format in formats) {
            try {
                return SimpleDateFormat(format, Locale.US).parse(isoString)
            } catch (e: Exception) { }
        }
        return null
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
