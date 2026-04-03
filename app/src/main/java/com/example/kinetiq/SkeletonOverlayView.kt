package com.example.kinetiq

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.kinetiq.models.Keypoint

class SkeletonOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paintGreen = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val paintRed = Paint().apply {
        color = Color.RED
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val pointPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 12f
        style = Paint.Style.FILL
    }

    private var keypoints: Map<String, Keypoint>? = null
    private var incorrectJoints: List<String> = emptyList()

    fun updateKeypoints(newKeypoints: Map<String, Keypoint>, incorrectJoints: List<String>) {
        this.keypoints = newKeypoints
        this.incorrectJoints = incorrectJoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kps = keypoints ?: return

        // Helper to draw a line between two named landmarks
        fun drawLine(from: String, to: String) {
            val start = kps[from]
            val end = kps[to]
            if (start != null && end != null && start.conf > 0.5 && end.conf > 0.5) {
                // Logic to determine color: if either joint is "incorrect", color the line red.
                // For "torso" error, we might color shoulders/hips red.
                val isRed = incorrectJoints.any { it in from || it in to } || 
                           (incorrectJoints.contains("torso") && (from.contains("shoulder") || from.contains("hip")))
                
                val p = if (isRed) paintRed else paintGreen
                canvas.drawLine(
                    start.x * width, start.y * height,
                    end.x * width, end.y * height,
                    p
                )
            }
        }

        // Draw Skeleton Connections
        drawLine("left_shoulder", "right_shoulder")
        drawLine("left_shoulder", "left_elbow")
        drawLine("left_elbow", "left_wrist")
        drawLine("right_shoulder", "right_elbow")
        drawLine("right_elbow", "right_wrist")
        drawLine("left_shoulder", "left_hip")
        drawLine("right_shoulder", "right_hip")
        drawLine("left_hip", "right_hip")
        drawLine("left_hip", "left_knee")
        drawLine("left_knee", "left_ankle")
        drawLine("right_hip", "right_knee")
        drawLine("right_knee", "right_ankle")

        // Draw Joint Points
        kps.forEach { (name, kp) ->
            if (kp.conf > 0.5) {
                val isRed = incorrectJoints.any { name.contains(it) } || (incorrectJoints.contains("torso") && (name.contains("shoulder") || name.contains("hip")))
                pointPaint.color = if (isRed) Color.RED else Color.YELLOW
                canvas.drawCircle(kp.x * width, kp.y * height, 8f, pointPaint)
            }
        }
    }
}
