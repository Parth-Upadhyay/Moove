package com.example.kinetiq.utils

import com.example.kinetiq.models.*
import kotlin.math.*

class CalibrationManager {
    private var calibrationStartTime: Long = 0
    private val baselineSamples = mutableListOf<Map<String, Keypoint>>()
    private var isCalibrating = false
    
    var calibrationData: CalibrationData? = null
        private set

    fun startCalibration(timestamp: Long) {
        calibrationStartTime = timestamp
        baselineSamples.clear()
        isCalibrating = true
    }

    fun processFrame(input: SessionInput): String? {
        if (!isCalibrating) return null

        val elapsed = input.timestamp_ms - calibrationStartTime
        if (elapsed < 3000) {
            baselineSamples.add(input.keypoints)
            return "Step 1: Baseline posture. Hold still..."
        } else if (elapsed < 10000) {
            // In a real app, Step 2 would involve moving through ROM.
            // For now, we collect data.
            baselineSamples.add(input.keypoints)
            return "Step 2: Move through the exercise slowly..."
        } else {
            finalizeCalibration()
            isCalibrating = false
            return "Calibration complete!"
        }
    }

    private fun finalizeCalibration() {
        if (baselineSamples.isEmpty()) return

        val avgShoulderY = baselineSamples.mapNotNull { it["right_shoulder"]?.y }.average().toFloat()
        val avgLeftHipX = baselineSamples.mapNotNull { it["left_hip"]?.x }.average()
        val avgRightHipX = baselineSamples.mapNotNull { it["right_hip"]?.x }.average()
        val avgHipMidX = ((avgLeftHipX + avgRightHipX) / 2.0).toFloat()

        // Simplified limb lengths
        val sample = baselineSamples.last()
        val rs = sample["right_shoulder"]
        val re = sample["right_elbow"]
        val rw = sample["right_wrist"]
        
        val limbLengths = if (rs != null && re != null && rw != null) {
            val upper = PoseMath.distance(rs, re)
            val forearm = PoseMath.distance(re, rw)
            LimbLengths(upper, forearm, upper + forearm, 0.0, 0.0)
        } else {
            LimbLengths(0.0, 0.0, 0.0, 0.0, 0.0)
        }

        calibrationData = CalibrationData(
            shoulderYBaseline = avgShoulderY,
            hipMidpointXBaseline = avgHipMidX,
            shoulderVectorBaseline = 0.0,
            patientRomMax = 0.0,
            internalBaseline = 0.0,
            limbLengths = limbLengths
        )
    }
}
