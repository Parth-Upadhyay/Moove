package com.example.kinetiq.utils

import com.example.kinetiq.exercises.Severity

object VoiceFeedbackProvider {

    fun getRepCountMessage(count: Int, totalReps: Int): String {
        return when (count) {
            1 -> "One — nice start."
            3 -> "Three — good rhythm."
            totalReps / 2 -> "Halfway — keep it steady."
            totalReps - 2 -> "Two more."
            totalReps -> "Set done — take a rest."
            else -> count.toString()
        }
    }

    fun getFormGuidance(flag: String): String {
        return when (flag) {
            "ELBOW_BEND" -> "Relax the arm — let it hang."
            "SHOULDER_HIKE" -> "Shoulder down — just the arm."
            "TRUNK_ROTATION" -> "Keep the shoulders square."
            "ELBOW_WINGING" -> "Tuck that elbow in."
            "ELBOW_NOT_AT_90" -> "Right angle at the elbow."
            "TRUNK_LEAN" -> "Stand tall — don't lean."
            "LATERAL_LEAN" -> "Nice and straight — don't lean."
            else -> ""
        }
    }

    fun getPositiveFeedback(type: String): String {
        return when (type) {
            "new_rom_record" -> "New personal best — great work."
            "clean_set" -> "Perfect form on that set."
            "streak_7" -> "7 days in a row — brilliant."
            "streak_14" -> "Two weeks straight — you're building real momentum."
            "streak_30" -> "30 days. That's extraordinary. Your physio will be thrilled."
            else -> ""
        }
    }

    fun getSafetyMessage(type: String): String {
        return when (type) {
            "pain_stop" -> "Let's stop there. Rest now."
            "fatigue" -> "Take a minute — you've earned it."
            "low_confidence" -> "Step back a little — I can't see you clearly."
            "high_hr" -> "Your heart rate is up — let's pause for a moment."
            "medication_warn" -> "Remember medication can mask pain — listen to your body."
            else -> ""
        }
    }
}
