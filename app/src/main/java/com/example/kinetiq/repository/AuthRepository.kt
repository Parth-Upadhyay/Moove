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
    object VerificationSent : AuthResult()
    object Unverified : AuthResult()
    object PasswordResetSent : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    suspend fun login(email: String, pass: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: return AuthResult.Error("Authentication failed")
            
            // Reload user to ensure isEmailVerified is up to date
            user.reload().await()
            
            if (!user.isEmailVerified) {
                // We keep them signed in so they can trigger 'resend' if needed, 
                // but we don't allow them into the app.
                return AuthResult.Unverified
            }
            
            val uid = user.uid
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
            val user = result.user ?: return AuthResult.Error("Registration failed")
            
            // Send verification email
            user.sendEmailVerification().await()
            
            val uid = user.uid
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
            
            // We stay signed in so the confirmation screen can offer a 'resend' button
            AuthResult.VerificationSent
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Registration failed")
        }
    }

    suspend fun resendVerification(): AuthResult {
        return try {
            val user = auth.currentUser
            if (user != null) {
                user.sendEmailVerification().await()
                AuthResult.VerificationSent
            } else {
                AuthResult.Error("No user logged in to send verification to.")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Failed to resend verification email")
        }
    }

    suspend fun resetPassword(email: String): AuthResult {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            AuthResult.PasswordResetSent
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Failed to send password reset email")
        }
    }

    fun logout() = auth.signOut()
    
    fun getCurrentUserId(): String? = auth.currentUser?.uid
}
