package com.example.kinetiq.models

data class CalibrationData(
    val shoulderYBaseline: Float,
    val hipMidpointXBaseline: Float,
    val shoulderVectorBaseline: Double,
    val patientRomMax: Double,
    val internalBaseline: Double,
    val limbLengths: LimbLengths
)

data class LimbLengths(
    val upperArmLength: Double,
    val forearmLength: Double,
    val totalArmLength: Double,
    val thighLength: Double,
    val shinLength: Double
)
