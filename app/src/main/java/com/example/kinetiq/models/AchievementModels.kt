package com.example.kinetiq.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Achievement(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val icon: String = "", // Resource name or URL
    val targetValue: Int = 0,
    val type: AchievementType = AchievementType.SESSIONS_COMPLETED,
    var isClaimed: Boolean = false,
    var progress: Int = 0,
    val rewardPoints: Int = 100
)

enum class AchievementType {
    SESSIONS_COMPLETED,
    REPS_TOTAL,
    STREAK_DAYS,
    ROM_IMPROVEMENT,
    PAIN_REDUCTION
}

@IgnoreExtraProperties
data class UserAchievement(
    val achievementId: String = "",
    val userId: String = "",
    val progress: Int = 0,
    val isCompleted: Boolean = false,
    val isClaimed: Boolean = false,
    val timestampCompleted: Long? = null
)
