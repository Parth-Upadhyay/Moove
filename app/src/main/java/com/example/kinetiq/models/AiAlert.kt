package com.example.kinetiq.models

import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.Date

@IgnoreExtraProperties
data class AiAlert(
    var id: String = "",
    var doctorId: String = "",
    var patientId: String = "",
    var patientName: String = "",
    var type: String = "SUMMARY", // SUMMARY, OUTLIER
    var message: String = "",
    var priority: String = "MEDIUM",
    var timestamp: Date = Date()
)
