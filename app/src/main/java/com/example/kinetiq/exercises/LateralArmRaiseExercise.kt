package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class LateralArmRaiseExercise : Exercise {
    private var repCount = 0
    private var state = State.BELOW_LOW
    private var maxAngle = 0.0
    private var peakAngleInCurrentRep = 0.0
    
    // Countdown variables
    private var startTimeMs: Long = -1
    private val PREP_TIME_MS = 5000L
    private var isPrepFinished = false
    
    private val LOWER_LIMIT = 60.0
    private val UPPER_LIMIT = 100.0
    private val ELBOW_STRAIGHT_THRESHOLD = 135.0 
    private val LATERAL_X_THRESHOLD = 0.50

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
        val hip = input.keypoints["${side}_hip"]

        if (shoulder == null || elbow == null || wrist == null || hip == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        val currentShoulderAngle = PoseMath.calculateAngle(hip, shoulder, wrist)
        val elbowAngle = PoseMath.calculateAngle(shoulder, elbow, wrist)
        val isArmStraightEnough = elbowAngle >= ELBOW_STRAIGHT_THRESHOLD

        // Horizontal Plane Validation
        val bodyScale = abs(shoulder.y - hip.y)
        val horizontalDistRatio = abs(wrist.x - shoulder.x) / bodyScale
        val isMovingLateral = horizontalDistRatio > LATERAL_X_THRESHOLD

        var voiceover: String? = null

        // Logic gating
        if (isMovingLateral) {
            if (currentShoulderAngle > maxAngle) maxAngle = currentShoulderAngle

            when (state) {
                State.BELOW_LOW -> {
                    if (currentShoulderAngle > LOWER_LIMIT) {
                        state = State.MOVING_UP
                        peakAngleInCurrentRep = currentShoulderAngle
                    }
                }
                State.MOVING_UP -> {
                    if (currentShoulderAngle > peakAngleInCurrentRep) peakAngleInCurrentRep = currentShoulderAngle
                    if (currentShoulderAngle >= UPPER_LIMIT) {
                        if (isArmStraightEnough) {
                            repCount++
                            voiceover = repCount.toString()
                            state = State.ABOVE_HIGH
                        }
                    }
                    if (currentShoulderAngle < LOWER_LIMIT - 5.0) {
                        state = State.BELOW_LOW
                        peakAngleInCurrentRep = 0.0
                    }
                }
                State.ABOVE_HIGH -> {
                    if (currentShoulderAngle > peakAngleInCurrentRep) peakAngleInCurrentRep = currentShoulderAngle
                    if (currentShoulderAngle <= LOWER_LIMIT) {
                        state = State.BELOW_LOW
                        peakAngleInCurrentRep = 0.0
                    }
                }
            }
        }

        var status = "valid"
        var reason: String? = null
        val incorrectJoints = mutableListOf<String>()

        // Error Handling
        if (currentShoulderAngle > 45.0 && !isMovingLateral) {
            reason = "Move your arms out to the side, not in front"
            status = "invalid"
        } else if (!isArmStraightEnough && currentShoulderAngle > LOWER_LIMIT) {
            reason = "Try to keep your arm straighter"
        }

        if (!isArmStraightEnough && currentShoulderAngle > 45.0) {
            incorrectJoints.add("${side}_elbow")
        }

        return ExerciseResult(
            repCount = repCount,
            status = status,
            incorrect_joints = incorrectJoints,
            reason = reason,
            voiceover = voiceover,
            currentRom = currentShoulderAngle,
            peakMotion = maxAngle,
            severity = if (status == "invalid") Severity.WARNING else Severity.NONE,
            prepCountdown = 0
        )
    }
}
