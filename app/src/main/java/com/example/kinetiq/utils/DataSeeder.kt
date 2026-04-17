package com.example.kinetiq.utils

import android.util.Log
import com.example.kinetiq.models.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class DataSeeder(private val db: FirebaseFirestore) {

    private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    suspend fun seedMockData() {
        // --- 1. FORCE THE UIDS FROM CONSOLE ---
        val doctorId = "UiYcaqyWvDNJYSfXIPFS87KyUD12"
        val doctorEmail = "uparth38@gmail.com"

        val clients = listOf(
            Triple("parthup2216@gmail.com", "cCxbri9vo0cI7VK20MeK8EsajDr2", "HIGH_PAIN"),
            Triple("crystaronaldo@gmail.com", "dppMiSa9tmM0uIG0yzNf9rNl5Aq2", "PROGRESS"),
            Triple("moovemit@gmail.com", "JjaO85WEI8XjBybKljtxxakULVl1", "LOW_ADHERENCE")
        )

        Log.d("DataSeeder", "Starting repeatable seed process for $doctorEmail and clients...")

        // Seed Doctor Profile
        db.collection("users").document(doctorId).set(
            mapOf("email" to doctorEmail, "role" to "DOCTOR", "displayName" to "Dr. Parth Upadhyay"),
            SetOptions.merge()
        ).await()
        db.collection("doctors").document(doctorId).set(mapOf("specialization" to "Sports Medicine"), SetOptions.merge()).await()

        for ((email, pUid, type) in clients) {
            Log.d("DataSeeder", "Seeding Patient: $email (UID: $pUid)")

            // Update User record
            db.collection("users").document(pUid).set(
                mapOf("email" to email, "role" to "PATIENT", "displayName" to email.split("@")[0]),
                SetOptions.merge()
            ).await()

            val prescription = ClinicalPrescription(
                exercises = listOf(
                    PrescribedExercise("lateral_arm_raise", "Lateral Arm Raise", 3, 10, true),
                    PrescribedExercise("pendulum", "Pendulum", 3, 15, true),
                    PrescribedExercise("external_rotation", "External Rotation", 3, 12, true),
                    PrescribedExercise("wall_climb", "Wall Climb", 3, 10, true)
                ),
                frequencyPerWeek = 5,
                assignedAt = Date(),
                notes = "Clinical test case for $type. Monitor ROM trends closely."
            )

            val patient = Patient(
                id = pUid,
                doctorId = doctorId,
                displayName = email.split("@")[0],
                injuryType = if (type == "HIGH_PAIN") "Post-Op Labral Repair" else "Rotator Cuff Tendinopathy",
                clinicalPrescription = prescription,
                age = 25 + Random().nextInt(20),
                sex = if (Random().nextBoolean()) "M" else "F",
                medicalNotes = "Seeded data for $type evaluation."
            )
            db.collection("patients").document(pUid).set(patient, SetOptions.merge()).await()

            // Seed a "Connection Request" so it shows as accepted
            val request = hashMapOf(
                "fromId" to pUid,
                "fromName" to email.split("@")[0],
                "fromEmail" to email,
                "toId" to doctorId,
                "toEmail" to doctorEmail,
                "status" to "ACCEPTED",
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            db.collection("connection_requests").document("req_$pUid").set(request).await()

            // 3. Clear and Generate 30 days of session history
            val existingSessions = db.collection("sessions").whereEqualTo("patient_id", pUid).get().await()
            for (doc in existingSessions.documents) {
                doc.reference.delete()
            }

            generateSessionHistory(pUid, doctorId, type)
        }
        
        Log.d("DataSeeder", "Seeding complete. History generated for all accounts.")
    }

    private suspend fun generateSessionHistory(patientId: String, doctorId: String, type: String) {
        val random = Random()
        val calendar = Calendar.getInstance()
        val exercises = listOf("lateral_arm_raise", "pendulum", "external_rotation", "wall_climb")

        // Generate 30 days of data
        for (day in 0..30) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -day)
            val sessionDate = calendar.time
            
            // Adherence logic: LOW_ADHERENCE skips most days
            if (type == "LOW_ADHERENCE" && day % 4 != 0 && day != 1) continue 
            
            // Each day they might do 1-3 of their prescribed exercises
            val sessionCount = if (type == "LOW_ADHERENCE") 1 else 1 + random.nextInt(3)
            val selectedExercises = exercises.shuffled().take(sessionCount)

            for (exId in selectedExercises) {
                val dayFactor = (30 - day).toDouble() / 30.0 // 0.0 at day 30, 1.0 at day 0 (today)
                
                // Peak ROM Progress
                val baseRom = when(exId) {
                    "pendulum" -> 90.0
                    "wall_climb" -> 110.0
                    else -> 45.0
                }
                
                val rom = when(type) {
                    "PROGRESS" -> baseRom + (dayFactor * 60.0) + (random.nextDouble() * 15)
                    "HIGH_PAIN" -> baseRom + (random.nextDouble() * 20) // Erratic/Slow
                    else -> baseRom + (dayFactor * 30.0)
                }

                // Pain Logic
                val prePain = when(type) {
                    "PROGRESS" -> (6 - (dayFactor * 5)).toInt().coerceAtLeast(1)
                    "HIGH_PAIN" -> 4 + random.nextInt(3)
                    else -> 3
                }
                
                var maxPain = prePain + random.nextInt(2)
                
                // Specific Alert Trigger for Parth (HIGH_PAIN)
                if (type == "HIGH_PAIN" && day <= 2) {
                    maxPain = 9 + random.nextInt(2) // 9 or 10
                }

                val session = SessionResult(
                    session_id = "${patientId}_${exId}_d$day",
                    patient_id = patientId,
                    doctor_id = doctorId,
                    exercise = exId,
                    timestamp_start = sdf.format(sessionDate),
                    timestamp_end = sdf.format(sessionDate),
                    side = "right",
                    pre_session = PreSession(pain_score = prePain),
                    pain_log = listOf(
                        PainEntry(rep = 1, level = prePain.toString()),
                        PainEntry(rep = 10, level = maxPain.toString())
                    ),
                    results = PerformanceResults(
                        sets_completed = 3,
                        valid_reps = 30,
                        peak_rom_degrees = rom,
                        session_duration_ms = (450000 + random.nextInt(300000)).toLong()
                    ),
                    rom_trend = RomTrend(rom, rom - 5.0, 5.0, 0, 0),
                    adherence = AdherenceSummary(
                        adherence_rate_30d = if (type == "LOW_ADHERENCE") 0.25f else 0.85f,
                        sessions_this_week = if (day <= 7) 1 else 4
                    ),
                    journal_entry = JournalEntry(
                        text = if (maxPain >= 8) "Ouch! This really hurt today." else "Good session.",
                        concern_flagged = maxPain >= 8
                    )
                )

                db.collection("sessions").document(session.session_id).set(session).await()
            }
        }
    }
}
