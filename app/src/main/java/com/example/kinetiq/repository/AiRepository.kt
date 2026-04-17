package com.example.kinetiq.repository

import android.util.Log
import com.example.kinetiq.BuildConfig
import com.example.kinetiq.api.GroqApi
import com.example.kinetiq.api.GroqMessage
import com.example.kinetiq.api.GroqRequest
import com.example.kinetiq.models.AiAlert
import com.example.kinetiq.models.Patient
import com.example.kinetiq.models.SessionResult
import com.example.kinetiq.utils.RateLimiter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val groqApi: GroqApi,
    private val db: FirebaseFirestore
) {
    private val TAG = "AiRepository"
    
    // Hard limit: Disabled for now
    private val apiRateLimiter = RateLimiter(maxAttempts = 999, windowMillis = TimeUnit.MINUTES.toMillis(1))

    suspend fun canDoctorRefresh(doctorId: String): Boolean = true

    suspend fun generateBulkSummaries(doctorId: String, patients: List<Patient>): Result<Int> {
        try {
            var generatedCount = 0
            Log.d(TAG, "Starting bulk summary generation for ${patients.size} patients")
            
            if (BuildConfig.GROQ_API_KEY.isBlank()) {
                Log.e(TAG, "GROQ_API_KEY is missing in BuildConfig! Ensure secret.properties has the key and you've synced Gradle.")
                return Result.failure(Exception("AI Configuration missing (API Key)"))
            }

            for (patient in patients) {
                val result = generatePatientSummaryInternal(doctorId, patient)
                if (result.isSuccess) {
                    if (result.getOrNull() != null) {
                        generatedCount++
                    }
                } else {
                    Log.e(TAG, "Error for patient ${patient.displayName}: ${result.exceptionOrNull()?.message}")
                }
                delay(100) 
            }

            db.collection("doctors").document(doctorId)
                .set(mapOf("lastAiRefresh" to Date()), com.google.firebase.firestore.SetOptions.merge())
                .await()

            return Result.success(generatedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Bulk summary failed", e)
            return Result.failure(e)
        }
    }

    private suspend fun generatePatientSummaryInternal(doctorId: String, patient: Patient): Result<AiAlert?> {
        return try {
            val sessionsQuery = db.collection("sessions")
                .whereEqualTo("patient_id", patient.id)
                .get()
                .await()

            val allSessions = sessionsQuery.documents.mapNotNull { doc ->
                try { doc.toObject(SessionResult::class.java) } catch (e: Exception) { null }
            }
            
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayDate = sdf.format(Date())
            val twoWeeksAgo = Calendar.getInstance().apply { 
                add(Calendar.DAY_OF_YEAR, -14)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            val lastTwoWeeksSessions = allSessions.filter { session ->
                try {
                    val datePart = session.timestamp_end.take(10)
                    val sessionDate = sdf.parse(datePart)
                    sessionDate != null && !sessionDate.before(twoWeeksAgo)
                } catch (e: Exception) { false }
            }.sortedBy { it.timestamp_end }
            
            Log.d(TAG, "Patient ${patient.displayName}: Found ${lastTwoWeeksSessions.size} recent sessions.")

            if (lastTwoWeeksSessions.isEmpty()) return Result.success(null)

            // Enhanced data summary to include both Pre and Post pain levels explicitly
            val sessionDataSummary = lastTwoWeeksSessions.joinToString("\n") { 
                val postPain = it.pain_log.lastOrNull()?.level ?: "N/A"
                "Date:${it.timestamp_end.take(10)}, Exercise:${it.exercise}, ROM:${it.results.peak_rom_degrees.toInt()}°, PrePain:${it.pre_session.pain_score}, PostPain:$postPain"
            }

            val prompt = """
                Role: Physical Therapy Clinical Analyst. Today is $todayDate.
                Patient: ${patient.displayName}.
                Objective: Provide a metrics-driven clinical summary based on the last 14 days of data.
                
                Data:
                $sessionDataSummary
                
                MANDATORY PRIORITY RULES (PAIN TRUMPS ALL):
                1. HIGH: 
                   - Any single 'PostPain' score >= 8.
                   - Average 'PostPain' > 7 in the last 3 days.
                   - ROM decline of > 10%.
                2. MEDIUM:
                   - Average 'PostPain' > 4 in the last 3 days.
                   - ROM progress is stagnant (improvement < 5% over 14 days).
                3. LOW:
                   - Consistent ROM improvement, stable low pain (all PostPain < 4), and high adherence.

                CRITICAL INSTRUCTION: If any 'PostPain' value in the data is 8, 9, or 10, the priority MUST be HIGH. No exceptions.
                
                MESSAGE REQUIREMENTS:
                - Max 50 words.
                - Start with specific numbers: "Sessions: X. ROM improvement: Y%. Avg Pain change: Z."
                - End with a single qualitative sentence summary/conclusion based on the trends observed.
                
                Respond in JSON ONLY:
                {
                  "type": "SUMMARY",
                  "message": "...",
                  "priority": "HIGH" | "MEDIUM" | "LOW"
                }
                
                If data is insufficient (<2 sessions), return {"type": "NONE"}.
            """.trimIndent()

            val response = groqApi.getCompletion(
                apiKey = "Bearer ${BuildConfig.GROQ_API_KEY}",
                request = GroqRequest(
                    model = "llama-3.1-8b-instant", 
                    messages = listOf(
                        GroqMessage(role = "system", content = "You are a PT Clinical Analyst. Priority is HIGH if pain >= 8."),
                        GroqMessage(role = "user", content = prompt)
                    )
                )
            )

            val aiContent = response.choices.firstOrNull()?.message?.content ?: return Result.failure(Exception("AI returned empty content"))
            Log.d(TAG, "AI Response for ${patient.id}: $aiContent")

            if (aiContent.contains("\"NONE\"", ignoreCase = true)) return Result.success(null)

            val alert = AiAlert(
                doctorId = doctorId,
                patientId = patient.id,
                patientName = patient.displayName,
                type = "SUMMARY",
                message = extractJsonValue(aiContent, "message") ?: "New summary available",
                priority = (extractJsonValue(aiContent, "priority") ?: "MEDIUM").uppercase(),
                timestamp = Date()
            )

            val oldAlerts = db.collection("ai_alerts")
                .whereEqualTo("patientId", patient.id)
                .get().await()
            oldAlerts.documents.forEach { it.reference.delete() }

            db.collection("ai_alerts").add(alert).await()
            Result.success(alert)
        } catch (e: Exception) {
            Log.e(TAG, "Error for ${patient.id}", e)
            Result.failure(e)
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex(RegexOption.IGNORE_CASE)
        return pattern.find(json)?.groupValues?.get(1)
    }

    fun getAiAlerts(doctorId: String): Flow<Result<List<AiAlert>>> = callbackFlow {
        val registration = db.collection("ai_alerts")
            .whereEqualTo("doctorId", doctorId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val alerts = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(AiAlert::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                trySend(Result.success(alerts))
            }
        awaitClose { registration.remove() }
    }
}
