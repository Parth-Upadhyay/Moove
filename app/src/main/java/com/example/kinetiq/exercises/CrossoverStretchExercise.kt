package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import kotlin.math.*

class CrossoverStretchExercise : Exercise {
    private var repCount = 0
    private var state = State.RESTING
    private var holdStartMs: Long = 0
    private var lastRepTimeMs: Long = 0
    
    // EMA Smoothing
    private val alpha = 0.3
    private var smoothedNormDist = 1.0
    private var previousNormDist = 1.0
    private var movementFrames = 0
    
    // Thresholds
    private val REST_THRESHOLD = 0.7
    private val STRETCH_THRESHOLD = 0.45
    private val STABILITY_THRESHOLD = 0.003
    private val MOVEMENT_DELTA_MIN = 0.005
    private val COOLDOWN_MS = 1000L

    private enum class State { RESTING, CROSSING, HOLDING, RELEASING }

    override fun processFrame(input: SessionInput): ExerciseResult {
        val side = input.prescription.side
        val lShoulder = input.keypoints["left_shoulder"]
        val rShoulder = input.keypoints["right_shoulder"]
        val elbow = input.keypoints["${side}_elbow"]
        val shoulder = if (side == "right") rShoulder else lShoulder
        val oppositeShoulder = if (side == "right") lShoulder else rShoulder
        
        // Confidence and visibility check
        if (lShoulder == null || rShoulder == null || elbow == null || shoulder == null || oppositeShoulder == null) {
            return ExerciseResult(repCount, "invalid", reason = "Ensure your upper body is visible")
        }
        
        if (elbow.conf < 0.5 || lShoulder.conf < 0.5 || rShoulder.conf < 0.5) {
            return ExerciseResult(repCount, "invalid", reason = "Low camera confidence")
        }

        // 1. Primary Metric: Normalized Elbow-to-Opposite-Shoulder Distance
        val shoulderWidth = sqrt((rShoulder.x - lShoulder.x).toDouble().pow(2.0) + (rShoulder.y - lShoulder.y).toDouble().pow(2.0)).coerceAtLeast(0.1)
        val distToOpposite = sqrt((elbow.x - oppositeShoulder.x).toDouble().pow(2.0) + (elbow.y - oppositeShoulder.y).toDouble().pow(2.0))
        val normDist = distToOpposite / shoulderWidth

        // Smoothing
        smoothedNormDist = alpha * normDist + (1 - alpha) * smoothedNormDist
        val deltaDist = previousNormDist - smoothedNormDist // Positive when moving towards opposite shoulder
        previousNormDist = smoothedNormDist

        // 2. Error Detection
        val incorrectJoints = mutableListOf<String>()
        var reason: String? = null

        // Shoulder Elevation
        val shoulderBaseline = (lShoulder.y + rShoulder.y) / 2
        if (shoulder.y < shoulderBaseline - 0.05) {
            incorrectJoints.add("shoulder")
            reason = "Shoulder shrugging detected"
        }

        // Elbow Dropping
        if (elbow.y > shoulder.y + 0.1) {
            incorrectJoints.add("elbow")
            reason = "Keep your elbow aligned with your shoulder"
        }

        // Torso Compensation (Trunk Tilt)
        val trunkTilt = Math.toDegrees(atan2(abs(rShoulder.y - lShoulder.y).toDouble(), abs(rShoulder.x - lShoulder.x).toDouble()))
        if (trunkTilt > 15.0) {
            incorrectJoints.add("torso")
            incorrectJoints.add("hips")
            reason = "Avoid twisting or leaning your body"
        }

        // Lack of intentional movement
        val isMovingTowards = deltaDist > MOVEMENT_DELTA_MIN
        if (isMovingTowards) {
            movementFrames++
        } else if (deltaDist < -MOVEMENT_DELTA_MIN) {
            movementFrames = 0
        }

        val currentTime = input.timestamp_ms
        val prescribedHold = (input.prescription.hold_seconds ?: 3).toLong()
        var holdCountdown: Int? = null

        // 3. State Machine
        when (state) {
            State.RESTING -> {
                if (smoothedNormDist < REST_THRESHOLD) {
                    if (movementFrames >= 5) {
                        state = State.CROSSING
                    } else if (smoothedNormDist < STRETCH_THRESHOLD + 0.1) {
                        // Triggered too fast or starting from crossed position
                        incorrectJoints.add("timing")
                        reason = "Start from a relaxed position with controlled movement"
                    }
                }
            }
            State.CROSSING -> {
                if (smoothedNormDist < STRETCH_THRESHOLD) {
                    if (abs(deltaDist) < STABILITY_THRESHOLD) {
                        state = State.HOLDING
                        holdStartMs = currentTime
                    }
                } else if (smoothedNormDist > REST_THRESHOLD + 0.05) {
                    state = State.RESTING
                    movementFrames = 0
                }
            }
            State.HOLDING -> {
                val elapsedSec = (currentTime - holdStartMs) / 1000
                holdCountdown = (prescribedHold - elapsedSec).toInt().coerceAtLeast(0)

                // Incomplete Stretch Check
                if (smoothedNormDist > STRETCH_THRESHOLD + 0.05) {
                    incorrectJoints.add("elbow")
                    incorrectJoints.add("shoulder")
                    reason = "Maintain the stretch position"
                    state = State.CROSSING
                } else if (elapsedSec >= prescribedHold) {
                    state = State.RELEASING
                }
            }
            State.RELEASING -> {
                if (smoothedNormDist > REST_THRESHOLD) {
                    if (currentTime - lastRepTimeMs > COOLDOWN_MS) {
                        repCount++
                        lastRepTimeMs = currentTime
                    }
                    state = State.RESTING
                    movementFrames = 0
                }
            }
        }

        // Optimality for UI feedback
        val optimality = (((REST_THRESHOLD - smoothedNormDist) / (REST_THRESHOLD - STRETCH_THRESHOLD)) * 100).coerceIn(0.0, 100.0)

        return ExerciseResult(
            repCount = repCount,
            status = if (incorrectJoints.isEmpty()) "valid" else "invalid",
            incorrect_joints = incorrectJoints,
            reason = reason,
            currentRom = optimality,
            holdCountdown = holdCountdown
        )
    }
}
