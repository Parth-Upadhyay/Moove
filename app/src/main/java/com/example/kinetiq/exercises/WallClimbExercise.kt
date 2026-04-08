package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class WallClimbExercise : Exercise {
    private var repCount = 0
    private var state = State.START
    private var maxAngle = 0.0 // Session maximum ROM
    private var peakAngleInCurrentRep = 0.0
    private var isDescending = false

    private enum class State { START, ACTIVE }

    override fun processFrame(input: SessionInput): ExerciseResult {
        val side = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val wrist = input.keypoints["${side}_wrist"]
        val hip = input.keypoints["${side}_hip"]

        if (shoulder == null || wrist == null || hip == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        // ROM Angle Calculation: Angle at shoulder between torso line (shoulder-to-hip) and arm line (shoulder-to-wrist)
        val currentAngle = PoseMath.calculateAngle(hip, shoulder, wrist)
        
        // Update session max ROM
        if (currentAngle > maxAngle) {
            maxAngle = currentAngle
        }

        // Rep Counting Logic: Angle rises from below 30°, hits peak, returns below 30°
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
                    // Detect descent from peak
                    isDescending = true
                }

                if (currentAngle < 30.0) {
                    repCount++
                    state = State.START
                    peakAngleInCurrentRep = 0.0
                    isDescending = false
                }
            }
        }

        // Red Highlighting: Mark shoulder red if angle stops below 90° at peak
        val incorrectJoints = mutableListOf<String>()
        if (isDescending && peakAngleInCurrentRep < 90.0) {
            incorrectJoints.add("${side}_shoulder")
        }

        // Peak Motion = max angle achieved, capped at 180 degrees
        val peakMotionValue = maxAngle.coerceIn(0.0, 180.0)

        return ExerciseResult(
            repCount = repCount,
            status = if (incorrectJoints.isNotEmpty()) "invalid" else "valid",
            severity = if (incorrectJoints.isNotEmpty()) Severity.WARNING else Severity.NONE,
            currentRom = maxAngle,
            peakMotion = peakMotionValue,
            incorrect_joints = incorrectJoints,
            reason = if (incorrectJoints.isNotEmpty()) "Peak angle was below 90°" else null
        )
    }
}
