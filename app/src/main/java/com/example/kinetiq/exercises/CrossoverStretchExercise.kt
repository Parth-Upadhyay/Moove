package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class CrossoverStretchExercise : Exercise {
    private var repCount = 0
    private var holdStartMs: Long = 0
    private var state = State.START
    private var maxAngle = 0.0
    private var currentHoldMet = false
    private var startTimeMs: Long? = null
    
    private val STARTUP_DELAY_MS = 5000L

    private enum class State { START, HOLDING, RETURNING }

    override fun processFrame(input: SessionInput): ExerciseResult {
        if (startTimeMs == null) {
            startTimeMs = input.timestamp_ms
        }
        val elapsedTime = input.timestamp_ms - (startTimeMs ?: input.timestamp_ms)
        val isGracePeriod = elapsedTime < STARTUP_DELAY_MS

        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val elbow = input.keypoints["${side}_elbow"]
        val otherShoulder = input.keypoints[if (side == "left") "right_shoulder" else "left_shoulder"]

        if (shoulder == null || elbow == null || otherShoulder == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        val currentAngle = PoseMath.calculateAngle(otherShoulder, shoulder, elbow)
        val shiftedAngle = (180.0 - currentAngle) - 90.0
        val finalAngle = shiftedAngle.coerceAtLeast(0.0)

        if (!isGracePeriod) {
            if (finalAngle > maxAngle) {
                maxAngle = finalAngle
            }

            val targetHold = 20000L // 20 seconds
            var currentHoldDuration = 0L

            when (state) {
                State.START -> {
                    if (finalAngle > 20.0) {
                        state = State.HOLDING
                        holdStartMs = input.timestamp_ms
                    }
                }
                State.HOLDING -> {
                    currentHoldDuration = input.timestamp_ms - holdStartMs
                    if (finalAngle < 15.0) {
                        state = State.START
                        currentHoldMet = false
                    } else if (currentHoldDuration >= targetHold) {
                        currentHoldMet = true
                        state = State.RETURNING
                    }
                }
                State.RETURNING -> {
                    if (finalAngle < 5.0) {
                        repCount++
                        state = State.START
                        currentHoldMet = false
                    }
                }
            }
        }

        val incorrectJoints = mutableListOf<String>()
        if (finalAngle > 5.0 && maxAngle < 20.0 && !isGracePeriod) {
            incorrectJoints.add("${side}_elbow")
        }

        return ExerciseResult(
            repCount = repCount,
            status = if (incorrectJoints.isNotEmpty()) "invalid" else "valid",
            severity = if (incorrectJoints.isNotEmpty()) Severity.WARNING else Severity.NONE,
            currentRom = if (isGracePeriod) 0.0 else maxAngle,
            peakMotion = maxAngle,
            incorrect_joints = incorrectJoints,
            holdCountdown = if (state == State.HOLDING) (20 - ((input.timestamp_ms - holdStartMs) / 1000).toInt()) else null
        )
    }
}
