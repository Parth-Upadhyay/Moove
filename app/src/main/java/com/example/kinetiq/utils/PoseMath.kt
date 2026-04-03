package com.example.kinetiq.utils

import com.example.kinetiq.models.Keypoint
import kotlin.math.*

object PoseMath {
    fun calculateAngle(a: Keypoint, b: Keypoint, c: Keypoint): Double {
        val vecA = doubleArrayOf((a.x - b.x).toDouble(), (a.y - b.y).toDouble(), (a.z - b.z).toDouble())
        val vecC = doubleArrayOf((c.x - b.x).toDouble(), (c.y - b.y).toDouble(), (c.z - b.z).toDouble())

        val dotProduct = vecA[0] * vecC[0] + vecA[1] * vecC[1] + vecA[2] * vecC[2]
        val magA = sqrt(vecA[0].pow(2) + vecA[1].pow(2) + vecA[2].pow(2))
        val magC = sqrt(vecC[0].pow(2) + vecC[1].pow(2) + vecC[2].pow(2))

        val angleRadians = acos((dotProduct / (magA * magC)).coerceIn(-1.0, 1.0))
        return Math.toDegrees(angleRadians)
    }

    fun distance(a: Keypoint, b: Keypoint): Double {
        return sqrt(
            (a.x - b.x).toDouble().pow(2) +
            (a.y - b.y).toDouble().pow(2) +
            (a.z - b.z).toDouble().pow(2)
        )
    }
}
