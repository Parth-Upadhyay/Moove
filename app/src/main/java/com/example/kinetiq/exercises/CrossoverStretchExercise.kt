package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class CrossoverStretchExercise : Exercise {
    private var repCount = 0
    private var holdStartMs: Long = 0
    private var state = State.START
    private var repMaxAngle = 0.0       // resets each rep
    private var peakMotion = 0.0        // all-time peak
    private var startTimeMs: Long? = null
    private var lastSecondsLeft = -1    // to prevent voiceover spam

    // Smoothing: rolling average over last N frames
    private val angleBuffer = ArrayDeque<Double>()

    // Drop tolerance: how long a brief dip below EXIT_HOLD_ANGLE is forgiven
    private var dropStartMs: Long? = null

    private enum class State { START, HOLDING, RETURNING }

    companion object {
        private const val STARTUP_DELAY_MS     = 5_000L   
        private const val TARGET_HOLD_MS       = 10_000L
        private const val SMOOTHING_WINDOW     = 10       // Increased for stability
        private const val ENTER_HOLD_ANGLE     = 18.0     // Slightly more accessible
        private const val EXIT_HOLD_ANGLE      = 10.0     // More forgiving exit
        private const val RETURN_NEUTRAL_ANGLE = 8.0      
        private const val MIN_REP_ROM          = 15.0     
        private const val DROP_TOLERANCE_MS    = 1_500L   // Increased to 1.5s
        private const val ELBOW_STRAIGHT_THRESHOLD = 140.0 
    }

    /**
     * Calculates the horizontal adduction angle in 2D (X-Y plane).
     * This is much more stable than 3D for crossover movements.
     */
    private fun calculateHorizontalAngle(shoulder: com.example.kinetiq.models.Keypoint, otherShoulder: com.example.kinetiq.models.Keypoint, elbow: com.example.kinetiq.models.Keypoint): Double {
        // Vector from shoulder to other shoulder (the "baseline" across the chest)
        val baselineX = otherShoulder.x - shoulder.x
        val baselineY = otherShoulder.y - shoulder.y
        
        // Vector from shoulder to elbow
        val armX = elbow.x - shoulder.x
        val armY = elbow.y - shoulder.y
        
        // Use 2D dot product to find angle between baseline and arm
        val dot = baselineX * armX + baselineY * armY
        val magBaseline = sqrt(baselineX.toDouble().pow(2) + baselineY.toDouble().pow(2))
        val magArm = sqrt(armX.toDouble().pow(2) + armY.toDouble().pow(2))
        
        val angleRad = acos((dot / (magBaseline * magArm)).coerceIn(-1.0, 1.0))
        val angleDeg = Math.toDegrees(angleRad)
        
        // We want 0 at "front" and positive as it moves toward the other shoulder.
        // If baseline to front is 90 degrees, then adduction is 90 - angleDeg.
        return (90.0 - angleDeg).coerceAtLeast(0.0)
    }

    override fun processFrame(input: SessionInput): ExerciseResult {
        if (startTimeMs == null) startTimeMs = input.timestamp_ms
        val elapsed = input.timestamp_ms - startTimeMs!!
        val isGracePeriod = elapsed < STARTUP_DELAY_MS

        val side = input.prescription.side
        val shoulder      = input.keypoints["${side}_shoulder"]
        val elbow         = input.keypoints["${side}_elbow"]
        val wrist         = input.keypoints["${side}_wrist"]
        val otherShoulder = input.keypoints[if (side == "left") "right_shoulder" else "left_shoulder"]

        if (shoulder == null || elbow == null || otherShoulder == null || wrist == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        // --- Use 2D Horizontal Angle for Stability ---
        val finalAngle = calculateHorizontalAngle(shoulder, otherShoulder, elbow)

        if (angleBuffer.size >= SMOOTHING_WINDOW) angleBuffer.removeFirst()
        angleBuffer.addLast(finalAngle)
        val smoothAngle = angleBuffer.average()

        if (isGracePeriod) {
            val secondsLeft = ((STARTUP_DELAY_MS - elapsed) / 1000).toInt() + 1
            val voice = if (secondsLeft != lastSecondsLeft) {
                lastSecondsLeft = secondsLeft
                secondsLeft.toString()
            } else null
            
            return ExerciseResult(
                repCount = repCount,
                status = "prepping",
                severity = Severity.GUIDANCE,
                currentRom = smoothAngle,
                peakMotion = peakMotion,
                prepCountdown = secondsLeft,
                voiceover = voice,
                reason = "Get ready to stretch"
            )
        }

        if (smoothAngle > repMaxAngle) repMaxAngle = smoothAngle
        if (smoothAngle > peakMotion)  peakMotion  = smoothAngle

        val elbowAngle = PoseMath.calculateAngle(shoulder, elbow, wrist)
        val isElbowBent = elbowAngle < ELBOW_STRAIGHT_THRESHOLD
        val incorrectJoints = mutableListOf<String>()
        if (isElbowBent && smoothAngle > RETURN_NEUTRAL_ANGLE) {
            incorrectJoints.add("${side}_elbow")
        }

        var holdCountdown: Int? = null
        var holdProgress: Float? = null
        var voiceover: String? = null
        val reason: String

        when (state) {
            State.START -> {
                dropStartMs = null
                if (smoothAngle >= ENTER_HOLD_ANGLE) {
                    state = State.HOLDING
                    holdStartMs = input.timestamp_ms
                    repMaxAngle = smoothAngle
                    reason = "Hold it!"
                } else {
                    reason = if (isElbowBent) "Keep arm straight" else "Bring arm across"
                }
            }

            State.HOLDING -> {
                val heldMs = input.timestamp_ms - holdStartMs
                val remainingSec = ((TARGET_HOLD_MS - heldMs) / 1_000L).toInt().coerceAtLeast(0)
                holdProgress = (heldMs.toFloat() / TARGET_HOLD_MS).coerceIn(0f, 1f)

                if (remainingSec != lastSecondsLeft && remainingSec <= 5) {
                    lastSecondsLeft = remainingSec
                    voiceover = if (remainingSec > 0) remainingSec.toString() else null
                }

                when {
                    smoothAngle < EXIT_HOLD_ANGLE -> {
                        val dropStart = dropStartMs ?: input.timestamp_ms.also { dropStartMs = it }
                        if (input.timestamp_ms - dropStart > DROP_TOLERANCE_MS) {
                            state = State.START
                            repMaxAngle = 0.0
                            dropStartMs = null
                            reason = "Stretch dropped"
                        } else {
                            holdCountdown = remainingSec
                            reason = "Stay crossed!"
                        }
                    }
                    heldMs >= TARGET_HOLD_MS -> {
                        state = State.RETURNING
                        holdProgress = 1f
                        reason = "Release slowly"
                        voiceover = "Great job!"
                    }
                    else -> {
                        dropStartMs = null
                        holdCountdown = remainingSec
                        reason = if (isElbowBent) "Straighten elbow" else "Holding... ${remainingSec}s"
                    }
                }
            }

            State.RETURNING -> {
                if (smoothAngle > RETURN_NEUTRAL_ANGLE) {
                    reason = "Return to side"
                } else {
                    if (repMaxAngle >= MIN_REP_ROM) {
                        repCount++
                        voiceover = repCount.toString()
                    }
                    repMaxAngle = 0.0
                    state = State.START
                    reason = "Ready"
                }
            }
        }

        return ExerciseResult(
            repCount = repCount,
            status = if (incorrectJoints.isNotEmpty()) "invalid" else "valid",
            severity = if (incorrectJoints.isNotEmpty()) Severity.WARNING else Severity.NONE,
            currentRom = smoothAngle,
            peakMotion = peakMotion,
            incorrect_joints = incorrectJoints,
            holdCountdown = holdCountdown,
            holdProgress = holdProgress,
            voiceover = voiceover,
            reason = reason
        )
    }
}
