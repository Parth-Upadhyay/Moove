package com.example.kinetiq.repository

import com.example.kinetiq.models.Message
import com.example.kinetiq.models.MessageType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val authRepo: AuthRepository
) {
    private fun getConversationId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }

    fun getMessages(otherUserId: String): Flow<Result<List<Message>>> = callbackFlow {
        val currentUserId = authRepo.getCurrentUserId()
        if (currentUserId == null) {
            trySend(Result.failure(Exception("User not authenticated")))
            close()
            return@callbackFlow
        }

        val conversationId = getConversationId(currentUserId, otherUserId)

        // We remove orderBy("timestamp") to avoid index requirements and to ensure
        // messages with null timestamps (local writes) are still included.
        // We will sort them in memory.
        val query = db.collection("messages")
            .whereEqualTo("conversationId", conversationId)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            
            val now = Date()
            val messages = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Message::class.java)
            }?.sortedBy { it.timestamp ?: now } ?: emptyList()
            
            trySend(Result.success(messages))
        }

        awaitClose { registration.remove() }
    }

    suspend fun sendMessage(receiverId: String, content: String, type: MessageType = MessageType.TEXT): Result<Unit> {
        return try {
            val senderId = authRepo.getCurrentUserId() ?: throw Exception("Not authenticated")
            val conversationId = getConversationId(senderId, receiverId)
            val messageId = db.collection("messages").document().id
            
            val message = Message(
                id = messageId,
                senderId = senderId,
                receiverId = receiverId,
                conversationId = conversationId,
                content = content,
                type = type,
                timestamp = null // Firestore ServerTimestamp handles this
            )
            
            db.collection("messages").document(messageId).set(message).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
