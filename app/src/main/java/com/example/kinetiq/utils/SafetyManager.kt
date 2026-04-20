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
                "High pain",
                Severity.CRITICAL,
                blockSession = true
            )
        }

        if (context.heart_rate_bpm > 100) {
            return SafetyAction(
                "Pulse high",
                Severity.WARNING,
                pauseSession = true
            )
        }

        return null
    }

    fun checkMidSession(input: SessionInput, recentFormErrors: Int): SafetyAction? {
        if (input.patient_context.heart_rate_bpm > 130) {
            return SafetyAction(
                "Pulse high",
                Severity.WARNING,
                pauseSession = true
            )
        }
        // Removed "Tiring. Take rest" as per request to remove weird advice
        return null
    }
}
