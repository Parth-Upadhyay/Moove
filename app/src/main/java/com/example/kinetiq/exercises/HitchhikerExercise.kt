package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class HitchhikerExercise : Exercise {
    private var repCount = 0
    private var state = State.BELOW_LOW
    private var maxAngle = 0.0 // Session max
    private var peakAngleInCurrentRep = 0.0
    
    // Countdown variables
    private var startTimeMs: Long = -1
    private val PREP_TIME_MS = 5000L
    private var isPrepFinished = false
    
    // Limits based on Shoulder-Elbow-Wrist angle
    private val LOWER_LIMIT = 130.0 
    private val UPPER_LIMIT = 70.0  

    private enum class State { BELOW_LOW, MOVING_UP, ABOVE_HIGH }

    override fun processFrame(input: SessionInput): ExerciseResult {
        // 1. Countdown Logic
        if (startTimeMs == -1L) {
            startTimeMs = input.timestamp_ms
        }

        val elapsedTime = input.timestamp_ms - startTimeMs
        val remainingPrepTime = (PREP_TIME_MS - elapsedTime)
        
        if (remainingPrepTime > 0) {
            return ExerciseResult(
                repCount = 0,
                status = "prepping",
                reason = "Get ready!",
                prepCountdown = (remainingPrepTime / 1000).toInt() + 1,
                severity = Severity.GUIDANCE
            )
        }

        if (!isPrepFinished) {
            isPrepFinished = true
        }

        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val elbow = input.keypoints["${side}_elbow"]
        val wrist = input.keypoints["${side}_wrist"]

        if (shoulder == null || elbow == null || wrist == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        val currentAngle = PoseMath.calculateAngle(shoulder, elbow, wrist)
        var voiceover: String? = null
        
        if (maxAngle == 0.0 || currentAngle < maxAngle) {
            maxAngle = currentAngle
        }

        when (state) {
            State.BELOW_LOW -> {
                if (currentAngle < LOWER_LIMIT) {
                    state = State.MOVING_UP
                    peakAngleInCurrentRep = currentAngle
                }
            }
            State.MOVING_UP -> {
                if (currentAngle < peakAngleInCurrentRep) {
                    peakAngleInCurrentRep = currentAngle
                }
                if (currentAngle <= UPPER_LIMIT) {
                    repCount++
                    voiceover = repCount.toString()
                    state = State.ABOVE_HIGH
                }
                if (currentAngle > LOWER_LIMIT + 5.0) {
                    state = State.BELOW_LOW
                    peakAngleInCurrentRep = 180.0
                }
            }
            State.ABOVE_HIGH -> {
                if (currentAngle < peakAngleInCurrentRep) {
                    peakAngleInCurrentRep = currentAngle
                }
                if (currentAngle >= LOWER_LIMIT) {
                    state = State.BELOW_LOW
                    peakAngleInCurrentRep = 180.0
                }
            }
        }

        return ExerciseResult(
            repCount = repCount,
            status = "valid",
            severity = Severity.NONE,
            currentRom = currentAngle,
            peakMotion = maxAngle,
            incorrect_joints = emptyList(),
            voiceover = voiceover,
            prepCountdown = 0
        )
    }
}
