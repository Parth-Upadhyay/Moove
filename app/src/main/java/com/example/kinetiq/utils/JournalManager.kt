package com.example.kinetiq.utils

object JournalManager {
    private val prompts = listOf(
        "How does the area feel right now — any soreness or tightness?",
        "Did anything feel harder or easier than last time?",
        "Any stiffness or clicking during the exercise today?",
        "How would you rate today's session overall — easy, just right, or tough?",
        "Anything your physio should know about how you're feeling?"
    )

    private var lastPromptIndex = -1

    fun getNextPrompt(): String {
        var index = (prompts.indices).random()
        while (index == lastPromptIndex && prompts.size > 1) {
            index = (prompts.indices).random()
        }
        lastPromptIndex = index
        return prompts[index]
    }

    fun analyzeEntry(text: String): List<String> {
        val tags = mutableListOf<String>()
        val keywords = listOf("sharp", "shooting", "couldn't", "gave way", "numb", "tingling", "swollen", "worse than before")
        
        if (keywords.any { text.contains(it, ignoreCase = true) }) {
            tags.add("CONCERN_KEYWORDS")
        }
        
        if (text.contains("pain", ignoreCase = true)) tags.add("pain_mention")
        if (text.contains("better", ignoreCase = true) || text.contains("easier", ignoreCase = true)) tags.add("progress_mention")
        
        return tags
    }
}
