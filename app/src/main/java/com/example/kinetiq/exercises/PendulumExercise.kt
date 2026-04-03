package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class PendulumExercise : Exercise {
    private var repCount = 0
    private var state = State.NEUTRAL
    
    // EMA Smoothing
    private val alpha = 0.2
    private var smoothedTorsoAngle = 0.0
    private var smoothedShoulderAngle = 0.0
    
    // Circle tracking
    private val historyX = mutableListOf<Float>()
    private val historyY = mutableListOf<Float>()
    private val HISTORY_SIZE = 30

    private enum class State { NEUTRAL, SWINGING }

    override fun processFrame(input: SessionInput): ExerciseResult {
        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val hip = input.keypoints["${side}_hip"]
        val elbow = input.keypoints["${side}_elbow"]
        val wrist = input.keypoints["${side}_wrist"]

        if (shoulder == null || hip == null || elbow == null || wrist == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        // 1. Calculate Angles
        val torsoAngle = abs(Math.toDegrees(atan2((shoulder.x - hip.x).toDouble(), (hip.y - shoulder.y).toDouble())))
        val shoulderAngle = PoseMath.calculateAngle(hip, shoulder, elbow)

        // 2. EMA Smoothing
        smoothedTorsoAngle = alpha * torsoAngle + (1 - alpha) * smoothedTorsoAngle
        smoothedShoulderAngle = alpha * shoulderAngle + (1 - alpha) * smoothedShoulderAngle

        // 3. Strict Validation
        if (smoothedTorsoAngle < 30.0) {
            return ExerciseResult(repCount, "invalid", incorrect_joints = listOf("torso"), reason = "Stand with your back more parallel to the floor")
        }
        if (smoothedShoulderAngle > 25.0) {
            return ExerciseResult(repCount, "invalid", incorrect_joints = listOf("shoulder"), reason = "Keep your shoulder relaxed — let the arm hang")
        }

        // 4. Circle Detection
        historyX.add(wrist.x)
        historyY.add(wrist.y)
        if (historyX.size > HISTORY_SIZE) {
            historyX.removeAt(0)
            historyY.removeAt(0)
        }

        val rangeX = (historyX.maxOrNull() ?: 0f) - (historyX.minOrNull() ?: 0f)
        val rangeY = (historyY.maxOrNull() ?: 0f) - (historyY.minOrNull() ?: 0f)
        
        if (rangeX > 0.4f || rangeY > 0.4f) {
             return ExerciseResult(repCount, "invalid", incorrect_joints = listOf("wrist"), reason = "Small controlled circles only")
        }

        val isMoving = rangeX > 0.05f && rangeY > 0.05f
        when (state) {
            State.NEUTRAL -> if (isMoving) state = State.SWINGING
            State.SWINGING -> {
                if (historyX.size == HISTORY_SIZE && isMoving) {
                    repCount++
                    historyX.clear()
                    historyY.clear()
                    state = State.NEUTRAL
                }
            }
        }

        return ExerciseResult(repCount, "valid")
    }
}
