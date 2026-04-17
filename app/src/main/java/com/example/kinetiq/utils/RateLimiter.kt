package com.example.kinetiq.utils

import java.util.concurrent.TimeUnit

class RateLimiter(
    private val maxAttempts: Int,
    private val windowMillis: Long
) {
    private val attempts = mutableListOf<Long>()

    @Synchronized
    fun shouldAllow(): Boolean {
        val now = System.currentTimeMillis()
        // Remove attempts outside the window
        attempts.removeAll { it < now - windowMillis }
        
        return if (attempts.size < maxAttempts) {
            attempts.add(now)
            true
        } else {
            false
        }
    }

    @Synchronized
    fun getRemainingTimeMillis(): Long {
        val now = System.currentTimeMillis()
        attempts.removeAll { it < now - windowMillis }
        if (attempts.size < maxAttempts) return 0
        
        val oldestAttempt = attempts.firstOrNull() ?: return 0
        return (oldestAttempt + windowMillis) - now
    }
}
