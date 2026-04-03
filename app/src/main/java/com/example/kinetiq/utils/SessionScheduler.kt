package com.example.kinetiq.utils

import java.util.*

object SessionScheduler {

    data class Reminder(
        val message: String,
        val minutesBefore: Int
    )

    fun getNextReminder(sessionHistory: List<Long>): Reminder? {
        if (sessionHistory.isEmpty()) return null

        val calendar = Calendar.getInstance()
        val hourCounts = IntArray(24)
        
        sessionHistory.forEach { timestamp ->
            calendar.timeInMillis = timestamp
            hourCounts[calendar.get(Calendar.HOUR_OF_DAY)]++
        }

        val optimalHour = hourCounts.indices.maxByOrNull { hourCounts[it] } ?: 0
        val variance = calculateVariance(sessionHistory)

        return if (variance < 1.5) {
            Reminder("Ready for today's session? It only takes a few minutes.", 15)
        } else if (variance > 3.0) {
            Reminder("When works best for you today?", 0)
        } else {
            null
        }
    }

    private fun calculateVariance(timestamps: List<Long>): Double {
        if (timestamps.isEmpty()) return 0.0
        val calendar = Calendar.getInstance()
        val hours = timestamps.map {
            calendar.timeInMillis = it
            calendar.get(Calendar.HOUR_OF_DAY).toDouble()
        }
        val mean = hours.average()
        return hours.sumOf { (it - mean) * (it - mean) } / hours.size
    }

    fun getMissedSessionMessage(daysMissed: Int): String? {
        return when {
            daysMissed == 1 -> "Ready for today's session? Consistency is key."
            daysMissed == 2 -> "You haven't exercised in 2 days — even a short session helps."
            daysMissed >= 4 -> "It's been a few days — your physiotherapist has been notified."
            else -> null
        }
    }
}
