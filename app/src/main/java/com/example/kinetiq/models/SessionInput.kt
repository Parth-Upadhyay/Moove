package com.example.kinetiq.models

data class Keypoint(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val conf: Float = 0f
)

data class SessionInput(
    val frame_id: Int = 0,
    val timestamp_ms: Long = 0,
    val keypoints: Map<String, Keypoint> = emptyMap(),
    val pre_session: PreSession = PreSession(),
    val patient_context: PatientContext = PatientContext(),
    val prescription: Prescription = Prescription(),
    val session_history_summary: SessionHistorySummary = SessionHistorySummary()
)

data class PreSession(
    val pain_score: Int = 0,
    val fatigue_score: Int = 0,
    val sleep_hours: Int = 0,
    val mood: String = "",
    val medication_taken_mins_ago: Int = 0,
    val swelling_reported: Boolean = false,
    val stiffness_reported: Boolean = false,
    val notes: String = ""
)

data class PatientContext(
    val condition: String = "",
    val surgery_date: String = "",
    val protocol_stage: Int = 0,
    val dominant_side: String = "",
    val age: Int = 0,
    val comorbidities: List<String> = emptyList(),
    val language: String = "",
    val wearable_connected: Boolean = false,
    val heart_rate_bpm: Int = 0,
    val hrv_ms: Int = 0,
    val daily_steps: Int = 0
)

data class Prescription(
    val exercise: String = "",
    val sets: Int = 0,
    val reps: Int = 0,
    val hold_seconds: Int? = null,
    val side: String = "",
    val target_angle_min: Float? = null,
    val target_angle_max: Float? = null,
    val rom_target_degrees: Float? = null,
    val rest_between_sets_seconds: Int = 0,
    val progression_unlocked: Boolean = false,
    val protocol_notes: String = ""
)

data class SessionHistorySummary(
    val sessions_this_week: Int = 0,
    val streak_days: Int = 0,
    val last_session_peak_rom: Float = 0f,
    val sessions_at_plateau: Int = 0,
    val previous_session_compensated_reps: Int = 0,
    val adherence_rate_30d: Float = 0f
)
