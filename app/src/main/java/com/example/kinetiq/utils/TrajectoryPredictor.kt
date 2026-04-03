package com.example.kinetiq.utils

import com.example.kinetiq.models.SessionResult
import java.util.*

object TrajectoryPredictor {

    data class Prediction(
        val estimatedDate: Date?,
        val confidence: String,
        val trajectory: String
    )

    fun predict(history: List<SessionResult>, targetRom: Double): Prediction? {
        if (history.size < 4) return null
        
        // Simple linear regression placeholder logic
        val romValues = history.map { it.results.peak_rom_degrees }
        val n = romValues.size
        val xSum = (0 until n).sum().toDouble()
        val ySum = romValues.sum()
        val xxSum = (0 until n).sumOf { it.toDouble() * it }.toDouble()
        val xySum = romValues.indices.sumOf { it.toDouble() * romValues[it] }

        val slope = (n * xySum - xSum * ySum) / (n * xxSum - xSum * xSum)

        if (slope <= 0) {
            return Prediction(null, "LOW", "PLATEAU_OR_REGRESSION")
        }

        val lastRom = romValues.last()
        val remainingRom = targetRom - lastRom
        val sessionsNeeded = (remainingRom / slope).toInt()
        
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, sessionsNeeded) // Assuming 1 session/day for simplicity

        val adherence = history.last().adherence.adherence_rate_30d
        val confidence = when {
            adherence > 0.80 -> "HIGH"
            adherence > 0.60 -> "MEDIUM"
            else -> "LOW"
        }

        return Prediction(calendar.time, confidence, "PROGRESSING")
    }
}
