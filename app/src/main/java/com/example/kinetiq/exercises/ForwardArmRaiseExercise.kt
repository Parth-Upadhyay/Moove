package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class ForwardArmRaiseExercise : Exercise {
    private var repCount = 0
    private var state = State.BELOW_LOW
    private var maxAngle = 0.0
    private var peakAngleInCurrentRep = 0.0
    private var niceFormCounter = 0
    
    // Countdown variables
    private var startTimeMs: Long = -1
    private val PREP_TIME_MS = 5000L
    private var isPrepFinished = false
    
    private val LOWER_LIMIT = 60.0
    private val UPPER_LIMIT = 100.0
    private val ELBOW_STRAIGHT_THRESHOLD = 160.0 
    private val ELBOW_ACCEPTABLE_THRESHOLD = 145.0
    private val FORWARD_X_THRESHOLD = 0.20

    private enum class State { BELOW_LOW, MOVING_UP, ABOVE_HIGH }

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

        val currentShoulderAngle = PoseMath.calculateAngle(hip, shoulder, wrist)
        val elbowAngle = PoseMath.calculateAngle(shoulder, elbow, wrist)
        val isArmStraightEnough = elbowAngle >= ELBOW_ACCEPTABLE_THRESHOLD

        val bodyScale = abs(shoulder.y - hip.y)
        val isMovingForward = (abs(wrist.x - shoulder.x) / bodyScale) < FORWARD_X_THRESHOLD

        var voiceover: String? = null
        var status = "valid"
        var reason: String? = null
        val incorrectJoints = mutableListOf<String>()

        if (isMovingForward) {
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
                    
                    // Feedback ONLY in counting range
                    if (!isArmStraightEnough) {
                        reason = "Straighten your elbow"
                        incorrectJoints.add("${side}_elbow")
                    } else if (elbowAngle >= ELBOW_STRAIGHT_THRESHOLD && currentShoulderAngle > (LOWER_LIMIT + UPPER_LIMIT) / 2) {
                        niceFormCounter++
                        if (niceFormCounter % 50 == 0) voiceover = "Nice form"
                    }

                    if (currentShoulderAngle >= UPPER_LIMIT) {
                        if (isArmStraightEnough) {
                            repCount++
                            // No rep count voiceover as per request, but we keep logic
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

        if (currentShoulderAngle > LOWER_LIMIT && !isMovingForward) {
            reason = "Keep arms in front"
            status = "invalid"
        }

        return ExerciseResult(
            repCount = repCount,
            status = status,
            incorrect_joints = incorrectJoints,
            reason = reason,
            voiceover = voiceover,
            currentRom = currentShoulderAngle,
            peakMotion = maxAngle,
            severity = if (reason != null) Severity.WARNING else Severity.NONE,
            prepCountdown = 0
        )
    }
}
