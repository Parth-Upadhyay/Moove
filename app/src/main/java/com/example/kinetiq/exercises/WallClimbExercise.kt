package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class WallClimbExercise : Exercise {
    private var repCount = 0
    private var state = State.START
    private var maxAngle = 0.0 
    private var peakAngleInCurrentRep = 0.0
    private var isDescending = false

    private enum class State { START, ACTIVE }

    private var startTimeMs: Long = -1
    private val PREP_TIME_MS = 5000L
    private var isPrepFinished = false

    override fun processFrame(input: SessionInput): ExerciseResult {
        if (startTimeMs == -1L) {
            startTimeMs = input.timestamp_ms
        }

        val elapsedTime = input.timestamp_ms - startTimeMs
        val remainingPrepTime = (PREP_TIME_MS - elapsedTime)
        
        if (remainingPrepTime > 0) {
            return ExerciseResult(
                repCount = 0,
                status = "prepping",
                reason = null,
                prepCountdown = (remainingPrepTime / 1000).toInt() + 1,
                severity = Severity.GUIDANCE
            )
        }

        if (!isPrepFinished) isPrepFinished = true

        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val wrist = input.keypoints["${side}_wrist"]
        val hip = input.keypoints["${side}_hip"]

        if (shoulder == null || wrist == null || hip == null) {
            return ExerciseResult(repCount, "invalid", reason = null)
        }

        val currentAngle = PoseMath.calculateAngle(hip, shoulder, wrist)
        
        if (currentAngle > maxAngle) maxAngle = currentAngle

        var voiceover: String? = null
        var reason: String? = null
        val incorrectJoints = mutableListOf<String>()

        when (state) {
            State.START -> {
                if (currentAngle > 30.0) {
                    state = State.ACTIVE
                    peakAngleInCurrentRep = currentAngle
                    isDescending = false
                }
            }
            State.ACTIVE -> {
                if (currentAngle > peakAngleInCurrentRep) {
                    peakAngleInCurrentRep = currentAngle
                    isDescending = false
                } else if (currentAngle < peakAngleInCurrentRep - 5.0) {
                    isDescending = true
                }

                // Feedback only when active
                if (isDescending && peakAngleInCurrentRep < 90.0 && currentAngle > 45.0) {
                    reason = "Reach higher"
                    incorrectJoints.add("${side}_shoulder")
                }

                if (currentAngle < 30.0) {
                    repCount++
                    state = State.START
                    peakAngleInCurrentRep = 0.0
                    isDescending = false
                }
            }
        }

        return ExerciseResult(
            repCount = repCount,
            status = if (incorrectJoints.isNotEmpty()) "invalid" else "valid",
            severity = if (incorrectJoints.isNotEmpty()) Severity.WARNING else Severity.NONE,
            currentRom = maxAngle,
            peakMotion = maxAngle.coerceIn(0.0, 180.0),
            incorrect_joints = incorrectJoints,
            reason = reason,
            voiceover = voiceover,
            prepCountdown = 0
        )
    }
}
