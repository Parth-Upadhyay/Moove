package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import com.example.kinetiq.models.Keypoint
import kotlin.math.*

class PendulumExercise : Exercise {
    private var repCount = 0
    private var maxAngle = 0.0
    
    private enum class State { START, CROSSED }
    private var currentState = State.START
    private var initialSide = 0 // 1 or -1

    // Shoulder fixation tracking
    private var lastShoulderPos: Keypoint? = null
    private var totalShoulderMovement = 0.0

    // Countdown variables
    private var startTimeMs: Long = -1
    private val PREP_TIME_MS = 5000L
    private var isPrepFinished = false

    // Rules
    private val ELBOW_STRAIGHT_THRESHOLD = 150.0 
    private val MIDLINE_THRESHOLD = 0.005 
    private val SHOULDER_STABILITY_THRESHOLD = 0.05 // Adjusted threshold for movement

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
        val elbow = input.keypoints["${side}_elbow"]
        val wrist = input.keypoints["${side}_wrist"]
        val hip = input.keypoints["${side}_hip"]

        if (shoulder == null || elbow == null || wrist == null || hip == null) {
            return ExerciseResult(repCount, "invalid", reason = null)
        }

        // Shoulder stability check
        if (lastShoulderPos != null) {
            val move = sqrt((shoulder.x - lastShoulderPos!!.x).pow(2) + (shoulder.y - lastShoulderPos!!.y).pow(2))
            totalShoulderMovement = totalShoulderMovement * 0.9 + move * 0.1
        }
        lastShoulderPos = shoulder

        val currentAngle = PoseMath.calculateAngle(hip, shoulder, wrist)
        val elbowAngle = PoseMath.calculateAngle(shoulder, elbow, wrist)
        val isArmStraight = elbowAngle >= ELBOW_STRAIGHT_THRESHOLD
        val isAboveShoulder = wrist.y < shoulder.y || elbow.y < shoulder.y

        val shX = hip.x - shoulder.x
        val shY = hip.y - shoulder.y
        val swX = wrist.x - shoulder.x
        val swY = wrist.y - shoulder.y
        val wristCrossProduct = shX * swY - shY * swX
        
        val seX = elbow.x - shoulder.x
        val seY = elbow.y - shoulder.y
        val elbowCrossProduct = shX * seY - shY * seX
        
        val currentWristSide = if (isAboveShoulder) 0 else {
            if (wristCrossProduct > MIDLINE_THRESHOLD) 1 else if (wristCrossProduct < -MIDLINE_THRESHOLD) -1 else 0
        }
        val currentElbowSide = if (isAboveShoulder) 0 else {
            if (elbowCrossProduct > MIDLINE_THRESHOLD) 1 else if (elbowCrossProduct < -MIDLINE_THRESHOLD) -1 else 0
        }

        var voiceover: String? = null
        var status = "valid"
        var reason: String? = null
        val incorrectJoints = mutableListOf<String>()

        if (currentAngle > maxAngle) maxAngle = currentAngle

        if (initialSide == 0 && currentWristSide != 0 && currentElbowSide != 0) {
            initialSide = currentWristSide
            currentState = State.START
        }

        if (initialSide != 0) {
            // FEEDBACK ONLY WHEN IN ACTIVE REP RANGE
            if (!isArmStraight && currentAngle > 5.0) {
                reason = "Straighten elbow"
                incorrectJoints.add("${side}_elbow")
            } else if (totalShoulderMovement > SHOULDER_STABILITY_THRESHOLD) {
                reason = "Keep shoulder still"
                incorrectJoints.add("${side}_shoulder")
            }

            when (currentState) {
                State.START -> {
                    if (currentWristSide == -initialSide && currentElbowSide == -initialSide) {
                        currentState = State.CROSSED
                    }
                }
                State.CROSSED -> {
                    if (currentWristSide == initialSide && currentElbowSide == initialSide) {
                        repCount++
                        currentState = State.START
                    }
                }
            }
        }

        if (isAboveShoulder) {
            status = "invalid"
            reason = "Keep arm low"
            incorrectJoints.add("${side}_shoulder")
        }

        return ExerciseResult(
            repCount = repCount,
            status = status,
            severity = if (reason != null) Severity.WARNING else Severity.NONE,
            currentRom = currentAngle,
            peakMotion = maxAngle,
            incorrect_joints = incorrectJoints,
            reason = reason,
            voiceover = voiceover,
            prepCountdown = 0
        )
    }
}
