package com.example.kinetiq.repository

import android.net.Uri
import com.example.kinetiq.models.Message
import com.example.kinetiq.models.MessageType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
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
    private val storage: FirebaseStorage,
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

        val query = db.collection("messages")
            .whereEqualTo("conversationId", conversationId)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                return@addSnapshotListener
            }
            
            val messages = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Message::class.java)
            }?.sortedBy { it.timestamp ?: Date() } ?: emptyList()
            
            trySend(Result.success(messages))
        }

        awaitClose { registration.remove() }
    }

    suspend fun sendMessage(
        receiverId: String, 
        content: String, 
        type: MessageType = MessageType.TEXT,
        fileUrl: String? = null,
        fileName: String? = null,
        fileSize: Long? = null
    ): Result<Unit> {
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
                fileUrl = fileUrl,
                fileName = fileName,
                fileSize = fileSize,
                timestamp = null
            )
            
            db.collection("messages").document(messageId).set(message).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFile(uri: Uri, fileName: String): Result<String> {
        return try {
            // Generate a unique path for the file
            val storageRef = storage.reference.child("chat_files").child("${UUID.randomUUID()}_$fileName")
            
            // Standard putFile is the most robust way to handle URIs from the system picker
            storageRef.putFile(uri).await()
            
            // Explicitly retrieve the download URL after the upload confirms
            val downloadUrl = storageRef.downloadUrl.await().toString()
            
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
