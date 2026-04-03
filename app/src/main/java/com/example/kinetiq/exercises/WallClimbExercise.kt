package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class WallClimbExercise : Exercise {
    private var state = State.START
    private var repCount = 0
    private var holdStartMs: Long = 0
    
    // EMA Smoothing factor
    private val alpha = 0.3
    private var smoothedFlexion = 0.0
    private var smoothedTorsoTilt = 0.0

    private enum class State { START, CLIMBING, PEAK, HOLDING, RETURNING }

    override fun processFrame(input: SessionInput): ExerciseResult {
        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val elbow = input.keypoints["${side}_elbow"]
        val wrist = input.keypoints["${side}_wrist"]
        val hip = input.keypoints["${side}_hip"]

        if (shoulder == null || elbow == null || wrist == null || hip == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        // 1. Calculate Raw Angles
        val rawFlexion = calculateShoulderFlexion(shoulder, hip, elbow)
        val rawTorsoTilt = calculateTorsoTilt(shoulder, hip)
        val elbowAngle = PoseMath.calculateAngle(shoulder, elbow, wrist)

        // 2. Apply EMA Smoothing
        smoothedFlexion = alpha * rawFlexion + (1 - alpha) * smoothedFlexion
        smoothedTorsoTilt = alpha * rawTorsoTilt + (1 - alpha) * smoothedTorsoTilt

        val incorrectJoints = mutableListOf<String>()
        var reason: String? = null

        // 3. Validation Rules (Strict)
        if (smoothedTorsoTilt > 10.0) {
            incorrectJoints.add("torso")
            reason = "Leaning body forward"
        }
        if (elbowAngle < 150.0) {
            incorrectJoints.add("elbow")
            reason = "Keep your arm straight"
        }

        var holdCountdown: Int? = null

        // 4. State Machine
        when (state) {
            State.START -> {
                if (smoothedFlexion < 35.0) { 
                    state = State.CLIMBING
                }
            }
            State.CLIMBING -> {
                if (smoothedFlexion >= 145.0) {
                    state = State.PEAK
                }
            }
            State.PEAK -> {
                holdStartMs = input.timestamp_ms
                state = State.HOLDING
            }
            State.HOLDING -> {
                val elapsed = (input.timestamp_ms - holdStartMs) / 1000
                holdCountdown = (3 - elapsed).toInt().coerceAtLeast(0)
                
                if (smoothedFlexion < 140.0) {
                    state = State.CLIMBING 
                    incorrectJoints.add("shoulder")
                    reason = "Maintain full height"
                } else if (elapsed >= 3) {
                    state = State.RETURNING
                }
            }
            State.RETURNING -> {
                if (smoothedFlexion < 40.0) {
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
            currentRom = smoothedFlexion,
            holdCountdown = holdCountdown
        )
    }

    private fun calculateShoulderFlexion(shoulder: com.example.kinetiq.models.Keypoint, hip: com.example.kinetiq.models.Keypoint, elbow: com.example.kinetiq.models.Keypoint): Double {
        val torsoVecX = (shoulder.x - hip.x).toDouble()
        val torsoVecY = (shoulder.y - hip.y).toDouble()
        val armVecX = (elbow.x - shoulder.x).toDouble()
        val armVecY = (elbow.y - shoulder.y).toDouble()
        
        val dot = torsoVecX * armVecX + torsoVecY * armVecY
        val magTorso = sqrt(torsoVecX.pow(2) + torsoVecY.pow(2))
        val magArm = sqrt(armVecX.pow(2) + armVecY.pow(2))
        return Math.toDegrees(acos((dot / (magTorso * magArm)).coerceIn(-1.0, 1.0)))
    }

    private fun calculateTorsoTilt(shoulder: com.example.kinetiq.models.Keypoint, hip: com.example.kinetiq.models.Keypoint): Double {
        return abs(Math.toDegrees(atan2((shoulder.x - hip.x).toDouble(), (hip.y - shoulder.y).toDouble())))
    }
}
