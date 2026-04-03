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
                "Your pain is quite high today. Please rest and contact your physiotherapist.",
                Severity.CRITICAL,
                blockSession = true
            )
        }

        if (pre.pain_score >= 5) {
            return SafetyAction(
                "You're reporting moderate pain. Take it easy — stop if it gets worse.",
                Severity.WARNING
            )
        }

        if (pre.medication_taken_mins_ago < 60) {
            return SafetyAction(
                "You've taken pain medication recently. Be careful — medication can mask discomfort.",
                Severity.WARNING
            )
        }

        if (pre.sleep_hours < 5) {
            val adjusted = input.prescription.copy(
                sets = (input.prescription.sets - 1).coerceAtLeast(1),
                reps = (input.prescription.reps * 0.8).toInt()
            )
            return SafetyAction(
                "You had a short night — we've lightened today's session slightly.",
                Severity.GUIDANCE,
                adjustedPrescription = adjusted
            )
        }

        if (context.heart_rate_bpm > 100) {
            return SafetyAction(
                "Your heart rate looks elevated. Take a moment to rest before starting.",
                Severity.WARNING,
                pauseSession = true
            )
        }

        return null
    }

    fun checkMidSession(input: SessionInput, recentFormErrors: Int): SafetyAction? {
        if (input.patient_context.heart_rate_bpm > 130) {
            return SafetyAction(
                "Your heart rate is quite high. Take a rest — don't push through.",
                Severity.WARNING,
                pauseSession = true
            )
        }

        if (recentFormErrors >= 4) {
            return SafetyAction(
                "Your form is changing — you may be tiring. Take a 60-second rest.",
                Severity.WARNING,
                pauseSession = true
            )
        }

        return null
    }
}
