package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class HandsBehindHeadExercise : Exercise {
    private var repCount = 0
    private var state = State.START
    private var holdStartMs: Long = 0
    private var maxRatio = 0.0
    private var currentHoldMet = false

    private enum class State { START, FLARING, PEAK, RETURNING }

    override fun processFrame(input: SessionInput): ExerciseResult {
        val leftShoulder = input.keypoints["left_shoulder"]
        val rightShoulder = input.keypoints["right_shoulder"]
        val leftElbow = input.keypoints["left_elbow"]
        val rightElbow = input.keypoints["right_elbow"]

        if (leftShoulder == null || rightShoulder == null || leftElbow == null || rightElbow == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        // Measure the angle (ratio) between left elbow X and right elbow X spreading apart relative to shoulder width
        val shoulderWidth = abs(rightShoulder.x - leftShoulder.x).toDouble()
        val elbowWidth = abs(rightElbow.x - leftElbow.x).toDouble()
        val currentRatio = if (shoulderWidth > 0) elbowWidth / shoulderWidth else 0.0
        
        if (currentRatio > maxRatio) {
            maxRatio = currentRatio
        }

        val targetHold = 3000L // 3 seconds
        var currentHoldDuration = 0L

        when (state) {
            State.START -> {
                if (currentRatio > 1.0) state = State.FLARING
            }
            State.FLARING -> {
                if (currentRatio > 1.3) {
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
                if (currentRatio < 1.2) {
                    state = State.START
                    currentHoldMet = false
                }
            }
            State.RETURNING -> {
                if (currentRatio < 1.0) {
                    repCount++
                    state = State.START
                    currentHoldMet = false
                }
            }
        }

        // Mark both elbows red if flare stays below 0.8x shoulder width
        val incorrectJoints = mutableListOf<String>()
        if (currentRatio > 0.5 && maxRatio < 0.8) {
            incorrectJoints.add("left_elbow")
            incorrectJoints.add("right_elbow")
        }

        // Peak Motion is represented by the max ratio achieved
        val peakMotionValue = maxRatio

        return ExerciseResult(
            repCount = repCount,
            status = if (incorrectJoints.isNotEmpty()) "invalid" else "valid",
            severity = if (incorrectJoints.isNotEmpty()) Severity.WARNING else Severity.NONE,
            currentRom = currentRatio,
            peakMotion = peakMotionValue,
            incorrect_joints = incorrectJoints,
            holdCountdown = if (state == State.PEAK) (3 - (currentHoldDuration / 1000).toInt()) else null
        )
    }
}
