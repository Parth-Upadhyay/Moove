package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class ExternalRotationExercise : Exercise {
    private var repCount = 0
    private var state = State.START
    private var holdStartMs: Long = 0
    private var repMaxAngle = 0.0       // resets each rep
    private var peakMotion = 0.0        // all-time peak across all reps
    private var startTimeMs: Long? = null
    private var lastSecondsLeft = -1    // for voiceover consistency

    // Smoothing: rolling average over last N frames to suppress keypoint jitter
    private val angleBuffer = ArrayDeque<Double>()

    // Drop tolerance: forgive brief dips or form breaks during the hold
    private var dropStartMs: Long? = null

    private enum class State { START, ROTATING, PEAK, RETURNING }

    companion object {
        private const val STARTUP_DELAY_MS    = 2_000L   // Reduced to 2s to save time
        private const val TARGET_HOLD_MS      = 3_000L   // required peak hold duration
        private const val SMOOTHING_WINDOW    = 20       // High smoothing to handle glitchy skeleton
        
        // Thresholds for the horizontal displacement-based "angle" (mapped to 0-90)
        private const val ENTER_ROTATE_ANGLE  = 20.0    // start moving hand away from midline
        private const val EXIT_ROTATE_ANGLE   = 12.0    // drop back too close to start
        private const val ENTER_PEAK_ANGLE    = 80.0    // nearly at full 90 degree rotation
        private const val EXIT_PEAK_ANGLE     = 65.0    // buffer for instability during hold
        private const val RETURN_NEUTRAL_ANGLE = 15.0   // full return to starting position
        private const val MIN_REP_ROM         = 75.0    // minimum rotation required to count a rep
        
        private const val DROP_TOLERANCE_MS   = 1_200L  // 1.2s forgiveness for glitchy frames
        private const val MAX_ELBOW_FLARE_X   = 0.08    // Strict horizontal distance for tucked elbow
        private const val MAX_HORIZONTAL_ERROR = 0.07   // Max vertical dev from elbow for "parallel" forearm
    }

    override fun processFrame(input: SessionInput): ExerciseResult {
        // --- Initialise start time ---
        if (startTimeMs == null) startTimeMs = input.timestamp_ms
        val elapsed = input.timestamp_ms - startTimeMs!!
        val isGracePeriod = elapsed < STARTUP_DELAY_MS

        // --- Keypoint extraction ---
        val side     = input.prescription.side
        val shoulder = input.keypoints["${side}_shoulder"]
        val elbow    = input.keypoints["${side}_elbow"]
        val wrist    = input.keypoints["${side}_wrist"]
        val hip      = input.keypoints["${side}_hip"]

        if (shoulder == null || elbow == null || wrist == null || hip == null) {
            return ExerciseResult(repCount, "invalid", reason = "Keypoints missing")
        }

        // --- 2D Rotation Logic (Horizontal Displacement) ---
        val armRefLength = sqrt(
            (shoulder.x - elbow.x).toDouble().pow(2) +
            (shoulder.y - elbow.y).toDouble().pow(2)
        ).coerceAtLeast(0.01)
        
        val wristDispX = abs(wrist.x - elbow.x).toDouble()
        val rotationRatio = (wristDispX / armRefLength).coerceIn(0.0, 1.0)
        val currentRotationAngle = rotationRatio * 90.0

        // --- Rolling average smoothing ---
        if (angleBuffer.size >= SMOOTHING_WINDOW) angleBuffer.removeFirst()
        angleBuffer.addLast(currentRotationAngle)
        val smoothAngle = angleBuffer.average()

        // --- Form Validation ---
        val elbowFlareX = abs(elbow.x - shoulder.x).toDouble()
        val isFlaring = elbowFlareX > MAX_ELBOW_FLARE_X
        
        val verticalDev = abs(wrist.y - elbow.y).toDouble()
        val isNotHorizontal = verticalDev > MAX_HORIZONTAL_ERROR

        val incorrectJoints = mutableListOf<String>()
        val shouldWarn = (state == State.ROTATING || state == State.PEAK)
        if (shouldWarn && isFlaring) {
            incorrectJoints.add("${side}_elbow")
        }

        // --- Grace period ---
        if (isGracePeriod) {
            val secondsLeft = ((STARTUP_DELAY_MS - elapsed) / 1000).toInt() + 1
            val voice = if (secondsLeft != lastSecondsLeft) {
                lastSecondsLeft = secondsLeft
                secondsLeft.toString()
            } else null

            return ExerciseResult(
                repCount   = repCount,
                status     = "prepping",
                severity   = Severity.GUIDANCE,
                currentRom = smoothAngle,
                peakMotion = peakMotion,
                prepCountdown = secondsLeft,
                voiceover = voice,
                reason     = "Ready? Keep your elbow tucked against your side"
            )
        }

        // --- Peak tracking ---
        if (smoothAngle > repMaxAngle) repMaxAngle = smoothAngle
        if (smoothAngle > peakMotion)  peakMotion  = smoothAngle

        // --- State machine ---
        var holdCountdown: Int? = null
        var holdProgress: Float? = null
        var voiceover: String? = null
        var reason = ""

        when (state) {
            State.START -> {
                dropStartMs = null
                if (smoothAngle >= ENTER_ROTATE_ANGLE) {
                    state       = State.ROTATING
                    repMaxAngle = smoothAngle
                }
                reason = "Rotate your hand outward"
            }

            State.ROTATING -> {
                when {
                    smoothAngle < EXIT_ROTATE_ANGLE -> {
                        state       = State.START
                        repMaxAngle = 0.0
                        reason      = "Keep your hand rotated out"
                    }
                    smoothAngle >= ENTER_PEAK_ANGLE -> {
                        if (!isNotHorizontal) {
                            state       = State.PEAK
                            holdStartMs = input.timestamp_ms
                            reason      = "Hold that 90-degree rotation"
                        } else {
                            reason = "Keep your forearm level (parallel to floor)"
                        }
                    }
                    else -> {
                        reason = when {
                            isFlaring -> "Keep your elbow tucked"
                            isNotHorizontal -> "Keep your forearm level"
                            else -> "Rotate your hand further out"
                        }
                    }
                }
            }

            State.PEAK -> {
                val heldMs       = input.timestamp_ms - holdStartMs
                val remainingSec = ((TARGET_HOLD_MS - heldMs) / 1_000L).toInt().coerceAtLeast(0)
                holdProgress = (heldMs.toFloat() / TARGET_HOLD_MS).coerceIn(0f, 1f)

                if (remainingSec != lastSecondsLeft && remainingSec <= 3) {
                    lastSecondsLeft = remainingSec
                    voiceover = if (remainingSec > 0) remainingSec.toString() else null
                }

                val isFormBroken = smoothAngle < EXIT_PEAK_ANGLE || isFlaring || isNotHorizontal

                when {
                    isFormBroken -> {
                        val dropStart = dropStartMs ?: input.timestamp_ms.also { dropStartMs = it }
                        if (input.timestamp_ms - dropStart > DROP_TOLERANCE_MS) {
                            state         = State.START
                            repMaxAngle   = 0.0
                            dropStartMs   = null
                            reason = "Form lost — keep your elbow tucked and arm out"
                        } else {
                            holdCountdown = remainingSec
                            reason = when {
                                isFlaring -> "Keep your elbow tucked!"
                                isNotHorizontal -> "Keep your forearm level"
                                else -> "Hold that rotation"
                            }
                        }
                    }
                    heldMs >= TARGET_HOLD_MS -> {
                        state         = State.RETURNING
                        holdProgress  = 1f
                        reason = "Great job! Now slowly return"
                        voiceover = "Great job!"
                    }
                    else -> {
                        dropStartMs   = null
                        holdCountdown = remainingSec
                        reason = "Hold that 90-degree rotation"
                    }
                }
            }

            State.RETURNING -> {
                if (smoothAngle > RETURN_NEUTRAL_ANGLE) {
                    reason = "Return your hand to the front"
                } else {
                    if (repMaxAngle >= MIN_REP_ROM) {
                        repCount++
                        voiceover = repCount.toString()
                        reason = "Rep complete!"
                    }
                    repMaxAngle = 0.0
                    state = State.START
                    reason = "Ready"
                }
            }
        }

        return ExerciseResult(
            repCount         = repCount,
            status           = if (incorrectJoints.isNotEmpty()) "invalid" else "valid",
            severity         = if (incorrectJoints.isNotEmpty() && shouldWarn) Severity.WARNING else Severity.NONE,
            currentRom       = smoothAngle,
            peakMotion       = peakMotion,
            incorrect_joints = incorrectJoints,
            holdCountdown    = holdCountdown,
            holdProgress     = holdProgress,
            voiceover        = voiceover,
            reason           = reason
        )
    }
}
