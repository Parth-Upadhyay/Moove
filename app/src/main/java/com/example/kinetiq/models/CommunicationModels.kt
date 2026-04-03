package com.example.kinetiq.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val conversationId: String = "",
    val content: String = "",
    val type: MessageType = MessageType.TEXT,
    @ServerTimestamp
    val timestamp: Date? = null,
    val metadata: Map<String, String> = emptyMap()
)

enum class MessageType {
    TEXT, VOICE, PHOTO, SESSION_SHARE
}

data class RecoveryJournal(
    val id: String = "",
    val patientId: String = "",
    val timestamp: Date = Date(),
    val sleepQuality: Int = 0, // 1-5
    val energyLevel: Int = 1,  // 1-5
    val mood: String = "",
    val entryText: String = "",
    val voiceNoteUrl: String? = null,
    val tags: List<String> = emptyList()
)
