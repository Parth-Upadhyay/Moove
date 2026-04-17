package com.example.kinetiq.utils

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.models.Prescription
import com.example.kinetiq.exercises.Severity

object SafetyManager {

    data class SafetyAction(
        val message: String,
        val severity: Severity,
        val blockSession: Boolean = false,
        val pauseSession: Boolean = false,
        val adjustedPrescription: Prescription? = null
    )

    fun checkPreSession(input: SessionInput): SafetyAction? {
        val pre = input.pre_session
        val context = input.patient_context
        
        if (pre.pain_score >= 7) {
            return SafetyAction(
                "High pain. Rest.",
                Severity.CRITICAL,
                blockSession = true
            )
        }

        if (pre.pain_score >= 5) {
            return SafetyAction(
                "Moderate pain. Careful.",
                Severity.WARNING
            )
        }

        if (pre.medication_taken_mins_ago < 60) {
            return SafetyAction(
                "Meds taken. Careful.",
                Severity.WARNING
            )
        }

        if (pre.sleep_hours < 5) {
            val adjusted = input.prescription.copy(
                sets = (input.prescription.sets - 1).coerceAtLeast(1),
                reps = (input.prescription.reps * 0.8).toInt()
            )
            return SafetyAction(
                "Light session today.",
                Severity.GUIDANCE,
                adjustedPrescription = adjusted
            )
        }

        if (context.heart_rate_bpm > 100) {
            return SafetyAction(
                "Pulse high. Rest.",
                Severity.WARNING,
                pauseSession = true
            )
        }

        return null
    }

    fun checkMidSession(input: SessionInput, recentFormErrors: Int): SafetyAction? {
        if (input.patient_context.heart_rate_bpm > 130) {
            return SafetyAction(
                "Pulse high. Rest.",
                Severity.WARNING,
                pauseSession = true
            )
        }

        if (recentFormErrors >= 4) {
            return SafetyAction(
                "Tiring. Take rest.",
                Severity.WARNING,
                pauseSession = true
            )
        }

        return null
    }
}
