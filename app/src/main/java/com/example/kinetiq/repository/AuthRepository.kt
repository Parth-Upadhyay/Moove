package com.example.kinetiq.repository

import com.example.kinetiq.models.UserRole
import com.example.kinetiq.models.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data class Success(val role: UserRole) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    suspend fun login(email: String, pass: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val uid = result.user?.uid ?: return AuthResult.Error("Authentication failed")
            
            val userDoc = db.collection("users").document(uid).get().await()
            val roleStr = userDoc.getString("role")?.uppercase() ?: return AuthResult.Error("Role not found")
            val role = try {
                UserRole.valueOf(roleStr)
            } catch (e: Exception) {
                UserRole.PATIENT
            }
            
            AuthResult.Success(role)
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "An unknown error occurred")
        }
    }

    suspend fun signUp(email: String, pass: String, role: UserRole, displayName: String): AuthResult {
        return try {
            val normalizedEmail = email.trim().lowercase(Locale.ROOT)
            val result = auth.createUserWithEmailAndPassword(normalizedEmail, pass).await()
            val uid = result.user?.uid ?: return AuthResult.Error("Registration failed")
            
            val userProfile = UserProfile(
                email = normalizedEmail,
                role = role.name,
                displayName = displayName.trim()
            )
            
            db.collection("users").document(uid).set(userProfile).await()
            
            if (role == UserRole.DOCTOR) {
                db.collection("doctors").document(uid).set(mapOf("specialization" to "General"))
            } else {
                db.collection("patients").document(uid).set(mapOf("injuryType" to "None"))
            }
            
            AuthResult.Success(role)
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Registration failed")
        }
    }

    fun logout() = auth.signOut()
    
    fun getCurrentUserId(): String? = auth.currentUser?.uid
}
