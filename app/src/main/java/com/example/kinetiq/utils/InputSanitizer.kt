package com.example.kinetiq.utils

object InputSanitizer {
    private const val MAX_INPUT_LENGTH = 1000
    private const val MAX_EMAIL_LENGTH = 254
    private const val MAX_NAME_LENGTH = 100

    fun sanitizeEmail(email: String): String {
        return email.trim().take(MAX_EMAIL_LENGTH).filter { it.isLetterOrDigit() || it == '@' || it == '.' || it == '_' || it == '-' }
    }

    fun sanitizeString(input: String, maxLength: Int = MAX_INPUT_LENGTH): String {
        // Basic HTML/Script tag removal
        return input.trim()
            .take(maxLength)
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("&", "&amp;")
    }

    fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$".toRegex()
        return email.isNotBlank() && email.length <= MAX_EMAIL_LENGTH && emailRegex.matches(email)
    }

    fun isValidPassword(password: String): Boolean {
        // Min 8 chars, at least one digit and one letter
        return password.length in 8..128 && password.any { it.isDigit() } && password.any { it.isLetter() }
    }
}
