package com.example.kinetiq.models

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class NotificationSettings(
    val userId: String = "",
    val isEnabled: Boolean = true,
    val isVoiceFeedbackEnabled: Boolean = false, // Default to OFF as requested
    val isVoiceCountEnabled: Boolean = true,     // Default to ON as requested
    val reminderTimes: List<String> = listOf("09:00", "18:00"), // HH:mm format
    val lastNotifiedDate: String = ""
)
