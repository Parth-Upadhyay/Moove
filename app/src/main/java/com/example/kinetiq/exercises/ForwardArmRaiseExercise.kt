package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class ForwardArmRaiseExercise : Exercise {
    private var repCount = 0
    private var state = State.BELOW_LOW
    private var maxAngle = 0.0
    private var peakAngleInCurrentRep = 0.0
    
    // Limits
    private val LOWER_LIMIT = 40.0
    private val UPPER_LIMIT = 90.0
    private val ELBOW_STRAIGHT_THRESHOLD = 135.0 

    private enum class State { BELOW_LOW, MOVING_UP, ABOVE_HIGH }

    override fun processFrame(input: SessionInput): ExerciseResult {
        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val elbow = input.keypoints["${side}_elbow"]
        val wrist = input.keypoints["${side}_wrist"]
        val hip = input.keypoints["${side}_hip"]

        if (shoulder == null || elbow == null || wrist == null || hip == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        // Angle at shoulder between torso line (shoulder-to-hip) and arm line (shoulder-to-wrist)
        val currentShoulderAngle = PoseMath.calculateAngle(hip, shoulder, wrist)
        
        // Straight arm check: Angle at elbow (shoulder-elbow-wrist)
        val elbowAngle = PoseMath.calculateAngle(shoulder, elbow, wrist)
        val isArmStraightEnough = elbowAngle >= ELBOW_STRAIGHT_THRESHOLD

        if (currentShoulderAngle > maxAngle) {
            maxAngle = currentShoulderAngle
        }

        val incorrectJoints = mutableListOf<String>()
        // Only flag if the bend is significant and they are in the active range
        if (!isArmStraightEnough && currentShoulderAngle > 30.0) {
            incorrectJoints.add("${side}_elbow")
        }

        when (state) {
            State.BELOW_LOW -> {
                if (currentShoulderAngle > LOWER_LIMIT) {
                    state = State.MOVING_UP
                    peakAngleInCurrentRep = currentShoulderAngle
                }
            }
            State.MOVING_UP -> {
                if (currentShoulderAngle > peakAngleInCurrentRep) {
                    peakAngleInCurrentRep = currentShoulderAngle
                }

                // Count rep when high limit (90) is reached
                if (currentShoulderAngle >= UPPER_LIMIT) {
                    if (isArmStraightEnough) {
                        repCount++
                        state = State.ABOVE_HIGH
                    }
                }
                
                // If they drop back down without completing, reset to low
                if (currentShoulderAngle < LOWER_LIMIT - 5.0) {
                    state = State.BELOW_LOW
                    peakAngleInCurrentRep = 0.0
                }
            }
            State.ABOVE_HIGH -> {
                if (currentShoulderAngle > peakAngleInCurrentRep) {
                    peakAngleInCurrentRep = currentShoulderAngle
                }
                
                // Must return below low limit (40) to reset state for the next rep
                if (currentShoulderAngle <= LOWER_LIMIT) {
                    state = State.BELOW_LOW
                    peakAngleInCurrentRep = 0.0
                }
            }
        }

        val status = if (incorrectJoints.isNotEmpty()) "invalid" else "valid"
        val severity = if (incorrectJoints.isNotEmpty()) Severity.WARNING else Severity.NONE
        val reason = if (!isArmStraightEnough && currentShoulderAngle > LOWER_LIMIT) "Try to keep your arm straighter" else null

        return ExerciseResult(
            repCount = repCount,
            status = status,
            severity = severity,
            currentRom = currentShoulderAngle,
            peakMotion = maxAngle.coerceIn(0.0, 180.0),
            incorrect_joints = incorrectJoints,
            reason = reason
        )
    }
}
