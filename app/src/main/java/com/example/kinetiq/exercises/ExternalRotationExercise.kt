package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class ExternalRotationExercise : Exercise {
    private var repCount = 0
    private var state = State.START
    
    // EMA Smoothing
    private val alpha = 0.3
    private var smoothedRotation = 0.0
    private var smoothedElbowDrift = 0.0

    private enum class State { START, ROTATING, PEAK, RETURNING }

    override fun processFrame(input: SessionInput): ExerciseResult {
        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val elbow = input.keypoints["${side}_elbow"]
        val wrist = input.keypoints["${side}_wrist"]
        val hip = input.keypoints["${side}_hip"]

        if (shoulder == null || elbow == null || wrist == null || hip == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        // 1. Calculate Angles & Metrics
        val rawRotation = calculateExternalRotation(elbow, wrist)
        val rawElbowDrift = abs(elbow.x - hip.x) 
        
        // 2. EMA Smoothing
        smoothedRotation = alpha * rawRotation + (1 - alpha) * smoothedRotation
        smoothedElbowDrift = alpha * rawElbowDrift + (1 - alpha) * smoothedElbowDrift

        val incorrectJoints = mutableListOf<String>()
        var reason: String? = null

        // 3. Strict Validation
        if (smoothedElbowDrift > 0.08f) {
            incorrectJoints.add("elbow")
            reason = "Elbow drifting away from torso"
        }

        // 4. State Machine
        when (state) {
            State.START -> {
                if (smoothedRotation < 10.0) state = State.ROTATING
            }
            State.ROTATING -> {
                if (smoothedRotation >= 30.0) state = State.PEAK
            }
            State.PEAK -> {
                state = State.RETURNING
            }
            State.RETURNING -> {
                if (smoothedRotation < 10.0) {
                    repCount++
                    state = State.START
                }
            }
        }

        return ExerciseResult(
            repCount = repCount,
            status = if (incorrectJoints.isEmpty()) "valid" else "invalid",
            incorrect_joints = incorrectJoints,
            reason = reason,
            currentRom = smoothedRotation
        )
    }

    private fun calculateExternalRotation(elbow: com.example.kinetiq.models.Keypoint, wrist: com.example.kinetiq.models.Keypoint): Double {
        val dx = (wrist.x - elbow.x).toDouble()
        val dz = (wrist.z - elbow.z).toDouble()
        return abs(Math.toDegrees(atan2(dz, dx)))
    }
}
