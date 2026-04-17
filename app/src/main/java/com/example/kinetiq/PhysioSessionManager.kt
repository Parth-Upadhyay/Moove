package com.example.kinetiq

import com.example.kinetiq.exercises.*
import com.example.kinetiq.models.SessionInput
import com.example.kinetiq.utils.*

class PhysioSessionManager(private val listener: SessionUpdateListener) {

    interface SessionUpdateListener {
        fun onVoiceFeedback(message: String, severity: Severity)
        fun onRepCountUpdated(count: Int, target: Int)
        fun onRepCompleted(delta: Int)
        fun onSetCountUpdated(currentSet: Int, totalSets: Int)
        fun onTimerUpdated(secondsRemaining: Int?)
        fun onPeakMotionUpdated(peakMotion: Double)
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
    
    // Set and Rep tracking
    private var currentSet = 1
    private var totalSets = 3
    private var repsInCurrentSet = 0
    private var targetRepsPerSet = 10
    private var isResting = false
    private var restStartTime: Long = 0
    private val REST_DURATION_MS = 20000L // 20 seconds
    private var lastExerciseTotalReps = 0
    private var lastAnnouncedRestSecond = -1
    private var isFirstFrameOfExercise = true

    // Message Stability System
    private var activeMessage: String? = null
    private var lastPostedMessage: String? = null
    private var messageExpiryTime: Long = 0
    private val MIN_MESSAGE_DURATION_MS = 5000L // Instructions stay for 5 seconds

    private fun createExerciseInstance(name: String): Exercise? {
        return when (name.lowercase()) {
            "pendulum" -> PendulumExercise()
            "crossover" -> CrossoverStretchExercise()
            "external_rotation" -> ExternalRotationExercise()
            "wall_climb" -> WallClimbExercise()
            "lateral_arm_raise" -> LateralArmRaiseExercise()
            "forward_arm_raise" -> ForwardArmRaiseExercise()
            "hitchhiker" -> HitchhikerExercise()
            else -> null
        }
    }

    fun startSession(input: SessionInput) {
        exerciseName = input.prescription.exercise.lowercase()
        currentExercise = createExerciseInstance(exerciseName)
        
        totalSets = if (input.prescription.sets > 0) input.prescription.sets else 3
        targetRepsPerSet = if (input.prescription.reps > 0) input.prescription.reps else 10
        currentSet = 1
        repsInCurrentSet = 0
        isResting = false
        lastExerciseTotalReps = 0
        lastAnnouncedRestSecond = -1
        isFirstFrameOfExercise = true
        
        lastInteractionTime = input.timestamp_ms
        postFeedback(ProtocolManager.getStageMessage(input.patient_context.protocol_stage), Severity.GUIDANCE, input.timestamp_ms)

        if (exerciseName == "forward_arm_raise") {
            val side = input.prescription.side.uppercase()
            postTip("Stand at a slight angle so your $side arm is closer to the camera", input.timestamp_ms)
        }
        
        listener.onSetCountUpdated(currentSet, totalSets)
        listener.onRepCountUpdated(repsInCurrentSet, targetRepsPerSet)
    }

    fun processFrame(input: SessionInput) {
        val currentTime = input.timestamp_ms

        if (isResting) {
            val elapsed = currentTime - restStartTime
            val remaining = ((REST_DURATION_MS - elapsed) / 1000).toInt()
            if (remaining <= 0) {
                isResting = false
                currentSet++
                repsInCurrentSet = 0
                lastExerciseTotalReps = 0
                lastAnnouncedRestSecond = -1
                isFirstFrameOfExercise = true
                currentExercise = createExerciseInstance(exerciseName) 
                
                listener.onTimerUpdated(null)
                listener.onSetCountUpdated(currentSet, totalSets)
                listener.onRepCountUpdated(repsInCurrentSet, targetRepsPerSet)
                postFeedback("Get ready for Set $currentSet", Severity.GUIDANCE, currentTime, force = true)
            } else {
                listener.onTimerUpdated(remaining)
                if (remaining <= 5 && remaining != lastAnnouncedRestSecond) {
                    lastAnnouncedRestSecond = remaining
                    postFeedback(remaining.toString(), Severity.GUIDANCE, currentTime, force = true)
                }
            }
            return
        }

        if (isPaused) {
            checkResume(input)
            return
        }

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

        val safetyAction = SafetyManager.checkMidSession(input, recentFormErrors)
        safetyAction?.let {
            if (it.pauseSession) isPaused = true
            postFeedback(it.message, it.severity, currentTime)
        }

        if (currentTime - lastInteractionTime > 180000) {
            listener.onSecurityAlert("Are you okay?")
        }

        currentExercise?.let { exercise ->
            val result = exercise.processFrame(input)
            
            listener.onIncorrectJointsUpdated(result.incorrect_joints)
            listener.onPeakMotionUpdated(result.peakMotion)
            listener.onHoldCountdown(result.holdCountdown)
            
            // Handle prep countdown timer (start of exercise/set)
            if (result.prepCountdown != null && result.prepCountdown > 0) {
                listener.onTimerUpdated(result.prepCountdown)
                return@let // Skip rep counting while prepping
            } else if (result.prepCountdown == 0 && !isResting) {
                listener.onTimerUpdated(null)
            }

            // Sync initial rep count to avoid counting starting position as a rep
            if (isFirstFrameOfExercise) {
                lastExerciseTotalReps = result.repCount
                isFirstFrameOfExercise = false
                return@let // Skip counting on the exact sync frame
            }

            // Handle voiceover from result (countdowns or rep numbers)
            if (result.voiceover != null) {
                postFeedback(result.voiceover, Severity.POSITIVE, currentTime, force = true)
            }
            
            val msg = if (result.status == "invalid") {
                result.reason ?: "Incorrect form detected"
            } else if (result.status == "prepping") {
                result.reason ?: "Get ready!"
            } else {
                null
            }

            // Don't post descriptive message if we just voiced a countdown/number
            if (msg != null && result.voiceover == null) {
                postFeedback(msg, result.severity, currentTime)
                if (result.severity == Severity.WARNING) {
                    recentFormErrors++
                }
            }
            
            if (result.repCount > lastExerciseTotalReps) {
                val delta = result.repCount - lastExerciseTotalReps
                lastExerciseTotalReps = result.repCount
                repsInCurrentSet += delta
                lastInteractionTime = currentTime
                
                // Voice count the rep if the exercise didn't already provide a voiceover
                if (result.voiceover == null) {
                    postFeedback(repsInCurrentSet.toString(), Severity.POSITIVE, currentTime, force = true)
                }
                
                listener.onRepCompleted(delta)
                
                if (repsInCurrentSet >= targetRepsPerSet) {
                    if (currentSet >= totalSets) {
                        listener.onRepCountUpdated(repsInCurrentSet, targetRepsPerSet)
                        listener.onSessionEnded("Exercise complete!", "high")
                    } else {
                        isResting = true
                        restStartTime = currentTime
                        postFeedback("Set complete! Take a break.", Severity.POSITIVE, currentTime, force = true)
                        listener.onRepCountUpdated(repsInCurrentSet, targetRepsPerSet)
                    }
                } else {
                    listener.onRepCountUpdated(repsInCurrentSet, targetRepsPerSet)
                }
            }
        }
    }

    private fun postFeedback(message: String, severity: Severity, currentTime: Long, force: Boolean = false) {
        if (isResting && !force) return

        // Prevent spamming the exact same voice feedback multiple times a second
        if (message == lastPostedMessage && (force || message.all { it.isDigit() })) {
            return
        }

        if (force || currentTime >= messageExpiryTime) {
            activeMessage = message
            lastPostedMessage = message
            
            val isNumber = message.all { it.isDigit() }
            val duration = if (isNumber) 1000L else MIN_MESSAGE_DURATION_MS
            messageExpiryTime = currentTime + duration
            listener.onVoiceFeedback(message, severity)
        }
    }

    private fun postTip(tip: String, currentTime: Long) {
        if (isResting) return

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
            "pendulum" -> listOf("${side}_shoulder", "${side}_wrist")
            "wall_climb", "lateral_arm_raise", "forward_arm_raise" -> listOf("${side}_shoulder", "${side}_elbow", "${side}_wrist", "${side}_hip")
            "crossover" -> listOf("left_shoulder", "right_shoulder", "${side}_wrist")
            "external_rotation" -> listOf("${side}_shoulder", "${side}_elbow", "${side}_wrist", "${side}_hip")
            "hitchhiker" -> listOf("${side}_shoulder", "${side}_elbow", "${side}_wrist")
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
