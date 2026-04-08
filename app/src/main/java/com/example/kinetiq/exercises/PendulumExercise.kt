package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class PendulumExercise : Exercise {
    private var repCount = 0
    private var maxAngle = 0.0
    private var crossedLeft = false
    private var crossedRight = false
    private var peakAngleThisRep = 0.0

    override fun processFrame(input: SessionInput): ExerciseResult {
        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val wrist = input.keypoints["${side}_wrist"]

        if (shoulder == null || wrist == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        val verticalPoint = com.example.kinetiq.models.Keypoint(shoulder.x, shoulder.y + 1.0f, shoulder.z)
        val currentAngle = PoseMath.calculateAngle(verticalPoint, shoulder, wrist)
        
        if (currentAngle > maxAngle) {
            maxAngle = currentAngle
        }
        
        if (currentAngle > peakAngleThisRep) {
            peakAngleThisRep = currentAngle
        }

        val isLeft = wrist.x < shoulder.x
        val isRight = wrist.x > shoulder.x

        if (isLeft) crossedLeft = true
        if (isRight) crossedRight = true

        if (crossedLeft && crossedRight) {
            repCount++
            crossedLeft = false
            crossedRight = false
            peakAngleThisRep = 0.0
        }

        val incorrectJoints = mutableListOf<String>()
        if (currentAngle > 5.0 && peakAngleThisRep < 15.0) {
            incorrectJoints.add("${side}_wrist")
        }

        // Peak Motion = max angle achieved
        val peakMotionValue = maxAngle

        return ExerciseResult(
            repCount = repCount,
            status = if (incorrectJoints.isNotEmpty()) "invalid" else "valid",
            severity = if (incorrectJoints.isNotEmpty()) Severity.WARNING else Severity.NONE,
            currentRom = currentAngle,
            peakMotion = peakMotionValue,
            incorrect_joints = incorrectJoints
        )
    }
}
