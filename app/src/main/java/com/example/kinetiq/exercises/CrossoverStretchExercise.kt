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

    private enum class State { START, HOLDING, RETURNING }

    override fun processFrame(input: SessionInput): ExerciseResult {
        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val elbow = input.keypoints["${side}_elbow"]
        val otherShoulder = input.keypoints[if (side == "left") "right_shoulder" else "left_shoulder"]

        if (shoulder == null || elbow == null || otherShoulder == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        // Angle of shoulder-to-elbow line from starting position (arm out) to crossed position (arm across body)
        // Midline is roughly the line between shoulders. 
        // We'll calculate the angle of the shoulder-elbow vector relative to the shoulder-shoulder vector.
        val currentAngle = PoseMath.calculateAngle(otherShoulder, shoulder, elbow)
        
        // Adjust angle so 0 is "arm out" (perpendicular to midline) and 90 is "across body" (along midline)
        val shiftedAngle = (180.0 - currentAngle) - 90.0
        val finalAngle = shiftedAngle.coerceAtLeast(0.0)

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

        // Mark elbow red if crossing angle stays below 20°
        val incorrectJoints = mutableListOf<String>()
        if (finalAngle > 5.0 && maxAngle < 20.0) {
            incorrectJoints.add("${side}_elbow")
        }

        // Peak Motion = max angle achieved
        val peakMotionValue = maxAngle

        return ExerciseResult(
            repCount = repCount,
            status = if (incorrectJoints.isNotEmpty()) "invalid" else "valid",
            severity = if (incorrectJoints.isNotEmpty()) Severity.WARNING else Severity.NONE,
            currentRom = maxAngle,
            peakMotion = peakMotionValue,
            incorrect_joints = incorrectJoints,
            holdCountdown = if (state == State.HOLDING) (20 - (currentHoldDuration / 1000).toInt()) else null
        )
    }
}
