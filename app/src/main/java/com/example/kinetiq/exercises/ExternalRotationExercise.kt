package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class ExternalRotationExercise : Exercise {
    private var repCount = 0
    private var state = State.START
    private var holdStartMs: Long = 0
    private var maxAngle = 0.0
    private var currentHoldMet = false
    private var startTimeMs: Long? = null
    
    private val STARTUP_DELAY_MS = 5000L

    private enum class State { START, ROTATING, PEAK, RETURNING }

    override fun processFrame(input: SessionInput): ExerciseResult {
        if (startTimeMs == null) {
            startTimeMs = input.timestamp_ms
        }
        val elapsedTime = input.timestamp_ms - (startTimeMs ?: input.timestamp_ms)
        val isGracePeriod = elapsedTime < STARTUP_DELAY_MS

        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val elbow = input.keypoints["${side}_elbow"]
        val wrist = input.keypoints["${side}_wrist"]

        if (shoulder == null || elbow == null || wrist == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        val currentAngle = PoseMath.calculateAngle(shoulder, elbow, wrist)
        
        if (!isGracePeriod) {
            if (currentAngle > maxAngle) {
                maxAngle = currentAngle
            }

            val targetHold = 3000L // 3 seconds
            var currentHoldDuration = 0L

            when (state) {
                State.START -> {
                    if (currentAngle > 45.0) state = State.ROTATING
                }
                State.ROTATING -> {
                    if (currentAngle > 70.0) { // arbitrary peak threshold for hold start
                        state = State.PEAK
                        holdStartMs = input.timestamp_ms
                    }
                }
                State.PEAK -> {
                    currentHoldDuration = input.timestamp_ms - holdStartMs
                    if (currentHoldDuration >= targetHold) {
                        currentHoldMet = true
                        state = State.RETURNING
                    }
                    if (currentAngle < 60.0) {
                        state = State.START
                        currentHoldMet = false
                    }
                }
                State.RETURNING -> {
                    if (currentAngle < 45.0) {
                        repCount++
                        state = State.START
                        currentHoldMet = false
                    }
                }
            }
        }

        val incorrectJoints = mutableListOf<String>()
        if (currentAngle > 10.0 && maxAngle < 30.0 && !isGracePeriod) {
            incorrectJoints.add("${side}_elbow")
        }

        return ExerciseResult(
            repCount = repCount,
            status = if (incorrectJoints.isNotEmpty()) "invalid" else "valid",
            severity = if (incorrectJoints.isNotEmpty()) Severity.WARNING else Severity.NONE,
            currentRom = if (isGracePeriod) 0.0 else maxAngle,
            peakMotion = maxAngle,
            incorrect_joints = incorrectJoints,
            holdCountdown = if (state == State.PEAK) (3 - ((input.timestamp_ms - holdStartMs) / 1000).toInt()) else null
        )
    }
}
