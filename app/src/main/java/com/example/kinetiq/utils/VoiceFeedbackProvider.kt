package com.example.kinetiq.utils

object VoiceFeedbackProvider {

    fun getRepCountMessage(count: Int, totalReps: Int): String {
        return count.toString()
    }

    fun getFormGuidance(flag: String): String {
        return when (flag) {
            "ELBOW_BEND" -> "Straighten arm"
            "SHOULDER_HIKE" -> "Shoulder down"
            "TRUNK_ROTATION" -> "Stay square"
            "ELBOW_WINGING" -> "Tuck elbow"
            "ELBOW_NOT_AT_90" -> "90 degrees"
            "TRUNK_LEAN" -> "Don't lean"
            "LATERAL_LEAN" -> "Stay straight"
            else -> ""
        }
    }

    fun getPositiveFeedback(type: String): String {
        return when (type) {
            "new_rom_record" -> "New record"
            "clean_set" -> "Perfect"
            "streak_7" -> "7 day streak"
            else -> "Good"
        }
    }

    fun getSafetyMessage(type: String): String {
        return when (type) {
            "pain_stop" -> "Stop"
            "fatigue" -> "Rest"
            "low_confidence" -> "Not in view"
            "high_hr" -> "Pulse high"
            else -> ""
        }
    }
}
