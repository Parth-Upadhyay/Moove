package com.example.kinetiq

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.kinetiq.models.Keypoint

class SkeletonOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val tealPrimary = Color.parseColor("#2EC4B6")
    private val errorRed = Color.parseColor("#E57373")
    private val accentOrange = Color.parseColor("#FFB703")

    private val paintCorrect = Paint().apply {
        color = tealPrimary
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val paintIncorrect = Paint().apply {
        color = errorRed
        strokeWidth = 12f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val pointPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pointStrokePaint = Paint().apply {
        color = tealPrimary
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
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
                val isRed = incorrectJoints.any { it in from || it in to } || 
                           (incorrectJoints.contains("torso") && (from.contains("shoulder") || from.contains("hip")))
                
                val p = if (isRed) paintIncorrect else paintCorrect
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
                val isRed = incorrectJoints.any { name.contains(it) } || 
                           (incorrectJoints.contains("torso") && (name.contains("shoulder") || name.contains("hip")))
                
                val x = kp.x * width
                val y = kp.y * height
                
                pointPaint.color = Color.WHITE
                canvas.drawCircle(x, y, 10f, pointPaint)
                
                pointStrokePaint.color = if (isRed) errorRed else tealPrimary
                canvas.drawCircle(x, y, 10f, pointStrokePaint)
            }
        }
    }
}
