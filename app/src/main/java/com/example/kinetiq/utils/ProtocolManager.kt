package com.example.kinetiq.utils

import com.example.kinetiq.models.SessionInput

object ProtocolManager {

    enum class Stage(val level: Int, val allowedExercises: List<String>, val painGate: Int) {
        STAGE_1(1, listOf("pendulum"), 4),
        STAGE_2(2, listOf("pendulum", "crossover"), 5),
        STAGE_3(3, listOf("pendulum", "crossover", "external_rotation"), 5),
        STAGE_4(4, listOf("pendulum", "crossover", "external_rotation", "wall_climb", "hitchhiker"), 6)
    }

    fun isExerciseAllowed(input: SessionInput): Boolean {
        val currentStage = when (input.patient_context.protocol_stage) {
            1 -> Stage.STAGE_1
            2 -> Stage.STAGE_2
            3 -> Stage.STAGE_3
            else -> Stage.STAGE_4
        }
        
        return input.prescription.exercise.lowercase() in currentStage.allowedExercises
    }

    fun getStageMessage(stage: Int): String {
        return when (stage) {
            1 -> "This is early recovery — gentle movement is the goal, not effort."
            2 -> "You're building range of motion. Slow and steady is right."
            3 -> "Now we start working the muscle. Form matters more than range."
            4 -> "You're in the strengthening phase. Push a little each session."
            else -> ""
        }
    }
}
