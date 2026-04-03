package com.example.kinetiq.models

data class SessionResult(
    val session_id: String = "",
    val patient_id: String = "",
    val doctor_id: String = "",
    val timestamp_start: String = "",
    val timestamp_end: String = "",
    val exercise: String = "",
    val side: String = "",
    val protocol_stage: Int = 0,
    val prescription: Prescription = Prescription(),
    val pre_session: PreSession = PreSession(),
    val context: SessionContext = SessionContext(),
    val results: PerformanceResults = PerformanceResults(),
    val rom_trend: RomTrend = RomTrend(),
    val pain_log: List<PainEntry> = emptyList(),
    val form_flags: List<FormFlag> = emptyList(),
    val doctor_alerts: List<DoctorAlert> = emptyList(),
    val wearable_data: WearableSessionData = WearableSessionData(),
    val journal_entry: JournalEntry? = null,
    val adherence: AdherenceSummary = AdherenceSummary(),
    val benchmarking: BenchmarkingSummary? = null
)

data class SessionContext(
    val readiness_score: Float = 0f,
    val wearable_connected: Boolean = false,
    val hr_pre_session: Int = 0,
    val hrv_pre_session: Int = 0,
    val session_tags: List<String> = emptyList()
)

data class PerformanceResults(
    val sets_completed: Int = 0,
    val reps_per_set: List<Int> = emptyList(),
    val valid_reps: Int = 0,
    val compensated_reps: Int = 0,
    val invalid_reps: Int = 0,
    val invalid_rep_reasons: List<String> = emptyList(),
    val peak_rom_degrees: Double = 0.0,
    val mean_rep_duration_ms: Long = 0,
    val session_duration_ms: Long = 0,
    val calories_burned: Int = 0
)

data class RomTrend(
    val this_session_peak: Double = 0.0,
    val last_session_peak: Double = 0.0,
    val delta: Double = 0.0,
    val sessions_at_plateau: Int = 0,
    val trajectory_estimate_weeks: Int = 0
)

data class PainEntry(
    val rep: Int = 0,
    val set: Int = 0,
    val level: String = "",
    val timestamp_ms: Long = 0
)

data class FormFlag(
    val flag: String = "",
    val rep: Int = 0,
    val set: Int = 0,
    val count: Int = 0,
    val severity: String = ""
)

data class DoctorAlert(
    val priority: String = "",
    val reason: String = "",
    val rep_at_onset: Int? = null
)

data class WearableSessionData(
    val hr_peak_mid_session: Int = 0,
    val hr_mean_mid_session: Int = 0,
    val active_calories: Int = 0
)

data class JournalEntry(
    val text: String = "",
    val tags: List<String> = emptyList(),
    val concern_flagged: Boolean = false
)

data class AdherenceSummary(
    val days_since_last_session: Int = 0,
    val sessions_this_week: Int = 0,
    val streak_days: Int = 0,
    val adherence_rate_30d: Float = 0f
)

data class BenchmarkingSummary(
    val cohort_median_rom: Double = 0.0,
    val patient_percentile: Int = 0
)
