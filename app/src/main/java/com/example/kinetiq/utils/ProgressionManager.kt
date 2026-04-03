package com.example.kinetiq.utils

import com.example.kinetiq.models.SessionResult
import com.example.kinetiq.models.Prescription

object ProgressionManager {

    data class ProgressionSuggestion(
        val newPrescription: Prescription,
        val reason: String,
        val priority: String
    )

    fun checkProgression(history: List<SessionResult>, currentPrescription: Prescription): ProgressionSuggestion? {
        if (history.size < 3) return null

        val last3 = history.takeLast(3)
        
        // Progression criteria
        val allValid = last3.all { it.results.valid_reps >= it.prescription.reps }
        val noCompensated = last3.all { it.results.compensated_reps == 0 }
        val lowPain = last3.all { it.pre_session.pain_score <= 3 } // Simplified peak pain check
        
        if (allValid && noCompensated && lowPain) {
            return ProgressionSuggestion(
                newPrescription = currentPrescription.copy(reps = currentPrescription.reps + 2),
                reason = "Consistent performance with perfect form and low pain.",
                priority = "INFO"
            )
        }

        // Regression criteria
        val last2 = history.takeLast(2)
        val lowAdherence = last2.any { it.results.valid_reps < (it.prescription.reps * 0.8) }
        val highPain = last2.any { it.pre_session.pain_score >= 5 }

        if (lowAdherence || highPain) {
            return ProgressionSuggestion(
                newPrescription = currentPrescription.copy(reps = (currentPrescription.reps - 2).coerceAtLeast(1)),
                reason = "Difficulty completing reps or elevated pain levels detected.",
                priority = "MEDIUM"
            )
        }

        return null
    }
}
