package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class HitchhikerExercise : Exercise {
    private var repCount = 0
    private var state = State.BELOW_LOW
    private var maxAngle = 0.0 // Session max
    private var peakAngleInCurrentRep = 0.0
    
    // Limits based on Shoulder-Elbow-Wrist angle
    // Forearm down = ~160° (LOW)
    // Forearm vertical up = ~80° (HIGH)
    private val LOWER_LIMIT = 150.0 // Slightly tighter than 160 to ensure return
    private val UPPER_LIMIT = 90.0  // Reaching towards 80

    private enum class State { BELOW_LOW, MOVING_UP, ABOVE_HIGH }

    override fun processFrame(input: SessionInput): ExerciseResult {
        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val elbow = input.keypoints["${side}_elbow"]
        val wrist = input.keypoints["${side}_wrist"]

        if (shoulder == null || elbow == null || wrist == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        // Angle at elbow (Shoulder-Elbow-Wrist)
        val currentAngle = PoseMath.calculateAngle(shoulder, elbow, wrist)
        
        // Track session peak (minimum angle is "higher" effort here, but we usually track ROM)
        // For Hitchhiker, we'll track the smallest angle achieved as the "peak" of the raise.
        // But for consistency with other exercises, let's track the excursion.
        if (maxAngle == 0.0 || currentAngle < maxAngle) {
            maxAngle = currentAngle
        }

        when (state) {
            State.BELOW_LOW -> {
                // Moving from 160 down towards 80
                if (currentAngle < LOWER_LIMIT) {
                    state = State.MOVING_UP
                    peakAngleInCurrentRep = currentAngle
                }
            }
            State.MOVING_UP -> {
                if (currentAngle < peakAngleInCurrentRep) {
                    peakAngleInCurrentRep = currentAngle
                }

                // Count rep when high limit (90 or less) is reached
                if (currentAngle <= UPPER_LIMIT) {
                    repCount++
                    state = State.ABOVE_HIGH
                }
                
                // If they drop back down without completing
                if (currentAngle > LOWER_LIMIT + 5.0) {
                    state = State.BELOW_LOW
                    peakAngleInCurrentRep = 180.0
                }
            }
            State.ABOVE_HIGH -> {
                if (currentAngle < peakAngleInCurrentRep) {
                    peakAngleInCurrentRep = currentAngle
                }
                
                // Must return above low limit (150) to reset
                if (currentAngle >= LOWER_LIMIT) {
                    state = State.BELOW_LOW
                    peakAngleInCurrentRep = 180.0
                }
            }
        }

        // We report ROM as (180 - minAngle) or similar to keep it positive/intuitive?
        // Let's just report the angle and the peak achieved.
        val displayRom = currentAngle
        val displayPeak = maxAngle

        return ExerciseResult(
            repCount = repCount,
            status = "valid",
            severity = Severity.NONE,
            currentRom = displayRom,
            peakMotion = displayPeak,
            incorrect_joints = emptyList()
        )
    }
}
