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
    
    // Countdown variables
    private var startTimeMs: Long = -1
    private val PREP_TIME_MS = 5000L
    private var isPrepFinished = false

    private enum class State { START, FLARING, PEAK, RETURNING }

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

        val leftShoulder = input.keypoints["left_shoulder"]
        val rightShoulder = input.keypoints["right_shoulder"]
        val leftElbow = input.keypoints["left_elbow"]
        val rightElbow = input.keypoints["right_elbow"]

        if (leftShoulder == null || rightShoulder == null || leftElbow == null || rightElbow == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        val shoulderWidth = abs(rightShoulder.x - leftShoulder.x).toDouble()
        val elbowWidth = abs(rightElbow.x - leftElbow.x).toDouble()
        val currentRatio = if (shoulderWidth > 0) elbowWidth / shoulderWidth else 0.0
        
        if (currentRatio > maxRatio) {
            maxRatio = currentRatio
        }

        val targetHold = 3000L // 3 seconds
        var voiceover: String? = null

        // State Machine
        when (state) {
            State.START -> {
                if (currentRatio > 0.8) state = State.FLARING
            }
            State.FLARING -> {
                if (currentRatio > 1.1) {
                    state = State.PEAK
                    holdStartMs = input.timestamp_ms
                }
            }
            State.PEAK -> {
                val currentHoldDuration = input.timestamp_ms - holdStartMs
                if (currentHoldDuration >= targetHold) {
                    currentHoldMet = true
                    state = State.RETURNING
                }
                if (currentRatio < 1.0) {
                    state = State.START
                    currentHoldMet = false
                }
            }
            State.RETURNING -> {
                if (currentRatio < 0.8) {
                    repCount++
                    voiceover = repCount.toString()
                    state = State.START
                    currentHoldMet = false
                }
            }
        }

        val incorrectJoints = mutableListOf<String>()
        if (currentRatio > 0.3 && maxRatio < 0.6) {
            incorrectJoints.add("left_elbow")
            incorrectJoints.add("right_elbow")
        }

        return ExerciseResult(
            repCount = repCount,
            status = if (incorrectJoints.isNotEmpty()) "invalid" else "valid",
            severity = if (incorrectJoints.isNotEmpty()) Severity.WARNING else Severity.NONE,
            currentRom = currentRatio,
            peakMotion = maxRatio,
            incorrect_joints = incorrectJoints,
            holdCountdown = if (state == State.PEAK) (3 - ((input.timestamp_ms - holdStartMs) / 1000).toInt()) else null,
            voiceover = voiceover,
            prepCountdown = 0
        )
    }
}
