package com.example.kinetiq.repository

import android.util.Log
import com.example.kinetiq.models.SessionResult
import com.example.kinetiq.viewmodel.ExerciseTrendPoint
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    private val TAG = "AnalyticsRepo"

    fun getAllSessions(patientId: String): Flow<Result<List<SessionResult>>> = callbackFlow {
        // Removed .orderBy to avoid composite index requirement
        val subscription = db.collection("sessions")
            .whereEqualTo("patient_id", patientId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getAllSessions failed: ${error.message}")
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val sessions = snapshot?.documents?.mapNotNull { doc ->
                    try { doc.toObject(SessionResult::class.java) } catch (e: Exception) { null }
                }?.sortedBy { it.timestamp_end } ?: emptyList() // Sort in memory instead
                
                trySend(Result.success(sessions))
            }
        awaitClose { subscription.remove() }
    }

    fun getExerciseProgress(patientId: String, exerciseType: String): Flow<Result<List<ExerciseTrendPoint>>> = callbackFlow {
        // Removed .orderBy to avoid composite index requirement
        val subscription = db.collection("sessions")
            .whereEqualTo("patient_id", patientId)
            .whereEqualTo("exercise", exerciseType)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getExerciseProgress failed for $exerciseType: ${error.message}")
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                
                val data = snapshot?.documents?.mapNotNull { doc ->
                    val session = try { doc.toObject(SessionResult::class.java) } catch (e: Exception) { null }
                    val optimality = session?.results?.peak_rom_degrees ?: 0.0
                    val painLevels = session?.pain_log?.mapNotNull { it.level.toDoubleOrNull() } ?: emptyList()
                    val avgPain = if (painLevels.isNotEmpty()) painLevels.average() else session?.pre_session?.pain_score?.toDouble() ?: 0.0
                    val date = parseTimestamp(doc, "timestamp_end") ?: Date()
                    ExerciseTrendPoint(date, optimality, avgPain)
                }?.sortedBy { it.date } ?: emptyList() // Sort in memory instead

                trySend(Result.success(data))
            }
        awaitClose { subscription.remove() }
    }

    fun getSessionsByDate(patientId: String, date: Date): Flow<Result<List<SessionResult>>> = callbackFlow {
        val subscription = db.collection("sessions")
            .whereEqualTo("patient_id", patientId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getSessionsByDate failed: ${error.message}")
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                
                val sessions = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val session = doc.toObject(SessionResult::class.java)
                        val sessionDate = parseTimestamp(doc, "timestamp_end")
                        if (session != null && sessionDate != null && isSameDay(sessionDate, date)) {
                            session
                        } else null
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(Result.success(sessions))
            }
        awaitClose { subscription.remove() }
    }

    private fun parseTimestamp(doc: com.google.firebase.firestore.DocumentSnapshot, field: String): Date? {
        try {
            val date = doc.getDate(field)
            if (date != null) return date
        } catch (e: Exception) {}
        val timestampStr = doc.getString(field) ?: return null
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(timestampStr)
        } catch (e: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(timestampStr)
            } catch (e2: Exception) { null }
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
