package com.example.kinetiq.exercises

import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.PoseMath
import kotlin.math.*

class ExternalRotationExercise : Exercise {
    private var repCount = 0
    private var state = State.START
    private var holdStartMs: Long = 0
    private var repMaxAngle = 0.0       // resets each rep
    private var peakMotion = 0.0        // all-time peak — never resets
    private var startTimeMs: Long? = null
    private var lastSecondsLeft = -1    // for voiceover consistency

    // Smoothing: rolling average over last N frames to suppress keypoint jitter
    private val angleBuffer = ArrayDeque<Double>()

    // Drop tolerance: forgive brief dips below EXIT_PEAK_ANGLE during the hold
    private var dropStartMs: Long? = null

    private enum class State { START, ROTATING, PEAK, RETURNING }

    companion object {
        private const val STARTUP_DELAY_MS    = 5_000L   // grace period on launch
        private const val TARGET_HOLD_MS      = 3_000L   // required peak hold
        private const val SMOOTHING_WINDOW    = 10       // increased for stability
        private const val ENTER_ROTATE_ANGLE  = 45.0    // ° arm starts rotating
        private const val EXIT_ROTATE_ANGLE   = 35.0    // ° hysteresis — drop resets ROTATING
        private const val ENTER_PEAK_ANGLE    = 85.0    // ° qualifies as peak (UPDATED)
        private const val EXIT_PEAK_ANGLE     = 75.0    // ° hysteresis — drop breaks hold (UPDATED)
        private const val RETURN_NEUTRAL_ANGLE = 20.0   // ° arm considered back at neutral (UPDATED)
        private const val MIN_REP_ROM         = 80.0    // ° minimum peak ROM to count a rep (UPDATED)
        private const val DROP_TOLERANCE_MS   = 1_000L  // forgive dips shorter than 1.0 s
        private const val MAX_ELBOW_FLARE     = 30.0    // max allowed angle between torso and arm
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

        // --- Angle: shoulder → elbow → wrist (increases with external rotation) ---
        val rawAngle = PoseMath.calculateAngle(shoulder, elbow, wrist)

        // --- Rolling average smoothing ---
        if (angleBuffer.size >= SMOOTHING_WINDOW) angleBuffer.removeFirst()
        angleBuffer.addLast(rawAngle)
        val smoothAngle = angleBuffer.average()

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
                reason     = "Get ready — keep your elbow at your side, forearm forward"
            )
        }

        // --- Peak tracking ---
        if (smoothAngle > repMaxAngle) repMaxAngle = smoothAngle
        if (smoothAngle > peakMotion)  peakMotion  = smoothAngle

        // --- Form Validation: Elbow Flare ---
        val flareAngle = PoseMath.calculateAngle(hip, shoulder, elbow)
        val isFlaring = flareAngle > MAX_ELBOW_FLARE
        val incorrectJoints = mutableListOf<String>()
        
        // Only flag form errors during active movement or hold states
        val shouldFlagForm = (state == State.ROTATING || state == State.PEAK)
        if (isFlaring && shouldFlagForm) {
            incorrectJoints.add("${side}_elbow")
        }

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
                    repMaxAngle = smoothAngle   // start fresh for this rep
                    reason      = "Keep rotating outward!"
                } else {
                    reason = "Rotate your forearm outward, keeping your elbow at your side"
                }
            }

            State.ROTATING -> {
                when {
                    smoothAngle < EXIT_ROTATE_ANGLE -> {
                        state       = State.START
                        repMaxAngle = 0.0
                        reason      = "Arm dropped — start the rotation again"
                    }
                    smoothAngle >= ENTER_PEAK_ANGLE -> {
                        state       = State.PEAK
                        holdStartMs = input.timestamp_ms
                        reason      = "Excellent! Hold this 90-degree position"
                    }
                    else -> {
                        reason = if (isFlaring) "Keep your elbow tucked against your side" else "Keep rotating — almost at 90 degrees!"
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

                // Logic Check: Break hold if either ROM drops or elbow flares
                val isFormBroken = smoothAngle < EXIT_PEAK_ANGLE || isFlaring

                when {
                    isFormBroken -> {
                        val dropStart = dropStartMs ?: input.timestamp_ms.also { dropStartMs = it }
                        if (input.timestamp_ms - dropStart > DROP_TOLERANCE_MS) {
                            state         = State.START
                            repMaxAngle   = 0.0
                            dropStartMs   = null
                            reason = "Keep your form steady at 90 degrees."
                        } else {
                            holdCountdown = remainingSec
                            reason = "Form slipping! Stay steady at 90 degrees"
                        }
                    }

                    heldMs >= TARGET_HOLD_MS -> {
                        dropStartMs   = null
                        state         = State.RETURNING
                        holdCountdown = null
                        holdProgress  = 1f
                        reason = "Hold complete! Slowly rotate back to start"
                        voiceover = "Great job!"
                    }

                    else -> {
                        dropStartMs   = null
                        holdCountdown = remainingSec
                        reason = if (isFlaring) "Keep your elbow tucked!" else "Maintain this 90-degree stretch — ${remainingSec}s"
                    }
                }
            }

            State.RETURNING -> {
                if (smoothAngle > RETURN_NEUTRAL_ANGLE) {
                    reason = "Slowly rotate your arm back to the starting position"
                } else {
                    if (repMaxAngle >= MIN_REP_ROM) {
                        repCount++
                        voiceover = repCount.toString()
                        reason = "Rep complete! Great work 💪"
                    } else {
                        reason = "Rep not counted — rotate closer to 90 degrees next time"
                    }
                    repMaxAngle = 0.0
                    state = State.START
                }
            }
        }

        return ExerciseResult(
            repCount         = repCount,
            status           = if (incorrectJoints.isNotEmpty()) "invalid" else "valid",
            severity         = if (incorrectJoints.isNotEmpty()) Severity.WARNING else Severity.NONE,
            currentRom       = smoothAngle, // Use smoothAngle for real-time visualization
            peakMotion       = peakMotion,
            incorrect_joints = incorrectJoints,
            holdCountdown    = holdCountdown,
            holdProgress     = holdProgress,
            voiceover        = voiceover,
            reason           = reason
        )
    }
}
