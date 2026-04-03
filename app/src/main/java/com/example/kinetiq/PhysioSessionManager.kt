package com.example.kinetiq

import com.example.kinetiq.exercises.*
import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.*

class PhysioSessionManager(private val listener: SessionUpdateListener) {

    interface SessionUpdateListener {
        fun onVoiceFeedback(message: String, severity: Severity)
        fun onRepCountUpdated(count: Int)
        fun onRomUpdated(rom: Double)
        fun onSessionEnded(reason: String, priority: String)
        fun onSecurityAlert(message: String)
        fun onPositioningTip(tip: String)
        fun onPrescriptionAdjusted(newPrescription: com.example.kinetiq.models.Prescription)
        fun onHoldCountdown(seconds: Int?)
        fun onIncorrectJointsUpdated(joints: List<String>)
    }

    private var currentExercise: Exercise? = null
    private var exerciseName: String = ""
    private var lowConfFrameCounter = 0
    private var isPaused = false
    
    private var lastInteractionTime: Long = 0
    private var recentFormErrors = 0
    private var lastRepCount = 0

    // Message Stability System
    private var activeMessage: String? = null
    private var messageExpiryTime: Long = 0
    private val MIN_MESSAGE_DURATION_MS = 2000L

    fun startSession(input: SessionInput) {
        exerciseName = input.prescription.exercise.lowercase()
        currentExercise = when (exerciseName) {
            "pendulum" -> PendulumExercise()
            "crossover" -> CrossoverStretchExercise()
            "external_rotation" -> ExternalRotationExercise()
            "wall_climb" -> WallClimbExercise()
            else -> null
        }
        
        lastInteractionTime = input.timestamp_ms
        postFeedback(ProtocolManager.getStageMessage(input.patient_context.protocol_stage), Severity.GUIDANCE, input.timestamp_ms)
    }

    fun processFrame(input: SessionInput) {
        val currentTime = input.timestamp_ms

        if (isPaused) {
            checkResume(input)
            return
        }

        // 1. Confidence Filtering
        if (!checkConfidence(input)) {
            lowConfFrameCounter++
            if (lowConfFrameCounter > 30) {
                isPaused = true
                postTip("Camera can't see your joints clearly — adjust your position", currentTime)
            }
            return
        } else {
            lowConfFrameCounter = 0
        }

        // 2. Mid-session Safety
        val safetyAction = SafetyManager.checkMidSession(input, recentFormErrors)
        safetyAction?.let {
            if (it.pauseSession) isPaused = true
            postFeedback(it.message, it.severity, currentTime)
        }

        // 3. Emergency
        if (currentTime - lastInteractionTime > 180000) {
            listener.onSecurityAlert("Are you okay?")
        }

        // 4. Exercise Processing
        currentExercise?.let { exercise ->
            val result = exercise.processFrame(input)
            
            // Update incorrect joints for visualization
            listener.onIncorrectJointsUpdated(result.incorrect_joints)

            // ROM Update
            result.currentRom?.let { rom ->
                listener.onRomUpdated(rom)
            }

            // Hold Countdown
            listener.onHoldCountdown(result.holdCountdown)
            
            // Map exercise output to UI feedback
            val msg = if (result.status == "invalid") {
                result.reason ?: "Incorrect form detected"
            } else {
                null
            }

            if (msg != null) {
                postFeedback(msg, result.severity, currentTime)
                if (result.severity == Severity.WARNING) {
                    recentFormErrors++
                }
            }
            
            if (result.repCount > lastRepCount) {
                lastRepCount = result.repCount
                lastInteractionTime = currentTime
                listener.onRepCountUpdated(result.repCount)
            }
        }
    }

    private fun postFeedback(message: String, severity: Severity, currentTime: Long) {
        // Priority check: CRITICAL/WARNING override current messages
        val isHighPriority = severity == Severity.WARNING || severity == Severity.CRITICAL || severity == Severity.POSITIVE
        
        if (currentTime >= messageExpiryTime || isHighPriority) {
            activeMessage = message
            messageExpiryTime = currentTime + MIN_MESSAGE_DURATION_MS
            listener.onVoiceFeedback(message, severity)
        }
    }

    private fun postTip(tip: String, currentTime: Long) {
        if (currentTime >= messageExpiryTime) {
            activeMessage = tip
            messageExpiryTime = currentTime + MIN_MESSAGE_DURATION_MS
            listener.onPositioningTip(tip)
        }
    }

    private fun checkConfidence(input: SessionInput): Boolean {
        val required = getRequiredLandmarks(input.prescription.side)
        if (required.isEmpty()) return true
        return required.all { name ->
            val kp = input.keypoints[name]
            kp != null && kp.conf >= 0.55
        }
    }

    private fun getRequiredLandmarks(side: String): List<String> {
        return when (exerciseName) {
            "pendulum", "wall_climb" -> listOf("${side}_shoulder", "${side}_elbow", "${side}_wrist")
            "crossover" -> listOf("left_shoulder", "right_shoulder", "${side}_wrist")
            "external_rotation" -> listOf("${side}_shoulder", "${side}_elbow", "${side}_wrist", "${side}_hip")
            else -> emptyList()
        }
    }

    private fun checkResume(input: SessionInput) {
        val required = getRequiredLandmarks(input.prescription.side)
        if (required.all { name -> (input.keypoints[name]?.conf ?: 0f) > 0.70 }) {
            isPaused = false
            postTip("Resuming session...", input.timestamp_ms)
        }
    }
}
