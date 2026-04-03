package com.example.kinetiq.repository

import android.util.Log
import com.example.kinetiq.models.ConnectionRequest
import com.example.kinetiq.models.Patient
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val authRepo: AuthRepository
) {
    private val TAG = "ConnectionRepo"

    suspend fun sendRequestToDoctor(doctorEmail: String): Result<Unit> {
        return try {
            val currentUserId = authRepo.getCurrentUserId() ?: throw Exception("Not authenticated")
            val userDoc = db.collection("users").document(currentUserId).get().await()
            val userName = userDoc.getString("displayName") ?: "Patient"
            val userEmail = userDoc.getString("email") ?: ""
            
            val targetEmail = doctorEmail.trim().lowercase(Locale.ROOT)

            var doctorSnapshot = db.collection("users")
                .whereEqualTo("email", targetEmail)
                .whereEqualTo("role", "DOCTOR")
                .get()
                .await()
            
            if (doctorSnapshot.isEmpty && doctorEmail.trim() != targetEmail) {
                doctorSnapshot = db.collection("users")
                    .whereEqualTo("email", doctorEmail.trim())
                    .whereEqualTo("role", "DOCTOR")
                    .get()
                    .await()
            }

            if (doctorSnapshot.isEmpty) throw Exception("Doctor not found with email: $doctorEmail")

            val doctorId = doctorSnapshot.documents.first().id

            val requestData = hashMapOf(
                "fromId" to currentUserId,
                "fromName" to userName,
                "fromEmail" to userEmail,
                "toId" to doctorId,
                "toEmail" to targetEmail,
                "status" to "PENDING",
                "timestamp" to FieldValue.serverTimestamp()
            )

            db.collection("connection_requests").add(requestData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending request", e)
            Result.failure(e)
        }
    }

    fun getIncomingRequestsForDoctor(): Flow<Result<List<ConnectionRequest>>> = callbackFlow {
        val doctorId = authRepo.getCurrentUserId()
        if (doctorId == null) {
            trySend(Result.failure(Exception("Not authenticated")))
            return@callbackFlow
        }

        val registration = db.collection("connection_requests")
            .whereEqualTo("toId", doctorId)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                
                val requests = mutableListOf<ConnectionRequest>()
                snapshot?.documents?.forEach { doc ->
                    try {
                        doc.toObject(ConnectionRequest::class.java)?.let {
                            it.id = doc.id
                            requests.add(it)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing request ${doc.id}", e)
                    }
                }
                trySend(Result.success(requests))
            }
        awaitClose { registration.remove() }
    }

    suspend fun acceptRequest(request: ConnectionRequest): Result<Unit> {
        if (request.id.isEmpty()) return Result.failure(Exception("Invalid Request ID"))
        
        return try {
            db.runTransaction { transaction ->
                val requestRef = db.collection("connection_requests").document(request.id)
                val patientRef = db.collection("patients").document(request.fromId)

                transaction.update(requestRef, "status", "ACCEPTED")
                // Added displayName to the patient record
                transaction.set(patientRef, mapOf(
                    "doctorId" to request.toId,
                    "displayName" to request.fromName
                ), SetOptions.merge())
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Transaction failed for acceptRequest", e)
            Result.failure(e)
        }
    }

    suspend fun rejectRequest(requestId: String): Result<Unit> {
        return try {
            db.collection("connection_requests").document(requestId)
                .update("status", "REJECTED")
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error rejecting request", e)
            Result.failure(e)
        }
    }
}
