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

    // Countdown variables
    private var startTimeMs: Long = -1
    private val PREP_TIME_MS = 5000L
    private var isPrepFinished = false

    // Rules
    private val ELBOW_STRAIGHT_THRESHOLD = 135.0 
    private val MIDLINE_THRESHOLD = 0.005 // Slightly less twitchy

    override fun processFrame(input: SessionInput): ExerciseResult {
        // Initialize start time on first frame
        if (startTimeMs == -1L) {
            startTimeMs = input.timestamp_ms
        }

        val elapsedTime = input.timestamp_ms - startTimeMs
        val remainingPrepTime = (PREP_TIME_MS - elapsedTime)
        
        if (remainingPrepTime > 0) {
            val secondsLeft = (remainingPrepTime / 1000).toInt() + 1
            return ExerciseResult(
                repCount = 0,
                status = "prepping",
                reason = "Get ready!",
                prepCountdown = secondsLeft,
                voiceover = secondsLeft.toString(),
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

        // 1. Calculate metrics
        val currentAngle = PoseMath.calculateAngle(hip, shoulder, wrist)
        val elbowAngle = PoseMath.calculateAngle(shoulder, elbow, wrist)
        val isArmStraight = elbowAngle >= ELBOW_STRAIGHT_THRESHOLD
        
        // Height check: Wrist or Elbow should not be above shoulder height (y is smaller when higher)
        val isAboveShoulder = wrist.y < shoulder.y || elbow.y < shoulder.y

        // 2. Midline Detection using 2D cross product
        val shX = hip.x - shoulder.x
        val shY = hip.y - shoulder.y
        
        // Wrist position relative to midline
        val swX = wrist.x - shoulder.x
        val swY = wrist.y - shoulder.y
        val wristCrossProduct = shX * swY - shY * swX
        
        // Elbow position relative to midline
        val seX = elbow.x - shoulder.x
        val seY = elbow.y - shoulder.y
        val elbowCrossProduct = shX * seY - shY * seX
        
        // CRITICAL: If arm is raised, treat it as "on the line" (0) so it doesn't trigger crossings or returns
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

        // 3. Rep Counting (ONLY if BOTH wrist and elbow have crossed AND not above shoulder)
        if (initialSide == 0 && currentWristSide != 0 && currentElbowSide != 0) {
            initialSide = currentWristSide
            currentState = State.START
        }

        if (initialSide != 0) {
            when (currentState) {
                State.START -> {
                    // To count as a cross, BOTH joint segments must pass the line while the arm is low
                    if (currentWristSide == -initialSide && currentElbowSide == -initialSide) {
                        currentState = State.CROSSED
                    }
                }
                State.CROSSED -> {
                    // To return, BOTH must return to the initial side while the arm is low
                    if (currentWristSide == initialSide && currentElbowSide == initialSide) {
                        repCount++
                        voiceover = repCount.toString()
                        currentState = State.START
                    }
                }
            }
        }

        // 4. Form Check and Feedback
        if (isAboveShoulder) {
            status = "invalid"
            reason = "Keep your arm hanging low"
            incorrectJoints.add("${side}_shoulder")
        } else {
            if (!isArmStraight && currentAngle > 5.0) {
                status = "invalid"
                reason = "Keep your arm straight"
                incorrectJoints.add("${side}_elbow")
            }
            
            // Feedback if the user is just moving their wrist/hand and not the whole arm
            if (currentWristSide != 0 && currentElbowSide != currentWristSide && currentAngle > 10.0) {
                status = "invalid"
                reason = "Swing from the shoulder"
                incorrectJoints.add("${side}_elbow")
            }
        }

        return ExerciseResult(
            repCount = repCount,
            status = status,
            severity = if (status == "invalid") Severity.WARNING else Severity.NONE,
            currentRom = currentAngle,
            peakMotion = maxAngle,
            incorrect_joints = incorrectJoints,
            reason = reason,
            voiceover = voiceover,
            prepCountdown = 0
        )
    }
}
