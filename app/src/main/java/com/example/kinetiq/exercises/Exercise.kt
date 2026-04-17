package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput

interface Exercise {
    fun processFrame(input: SessionInput): ExerciseResult
}

data class ExerciseResult(
    val repCount: Int,
    val status: String, // "valid" or "invalid"
    val incorrect_joints: List<String> = emptyList(),
    val reason: String? = null,
    val voiceover: String? = null,
    val currentRom: Double? = null,
    val holdCountdown: Int? = null,
    val prepCountdown: Int? = null, // Added for the 5-second start timer
    val severity: Severity = Severity.NONE,
    val peakMotion: Double = 0.0
)

enum class Severity {
    NONE, GUIDANCE, WARNING, POSITIVE, CRITICAL
}
