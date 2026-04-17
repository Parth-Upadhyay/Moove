package com.example.kinetiq.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader

@Composable
fun ExerciseDemoPlayer(
    modelPath: String,
    modifier: Modifier = Modifier
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine = engine)
    val environmentLoader = rememberEnvironmentLoader(engine = engine)

    val modelNode = remember(modelPath) {
        val modelInstance = modelLoader.createModelInstance(assetFileLocation = modelPath)
        modelInstance?.let {
            if (modelPath.contains("crossover", ignoreCase = true)) {
                // Special handling for crossover: hold ending frame for 2 seconds
                object : ModelNode(modelInstance = it, autoAnimate = false, scaleToUnits = 0.05f) {
                    private var startTimeNanos = -1L

                    override fun onFrame(frameTimeNanos: Long) {
                        if (startTimeNanos == -1L) startTimeNanos = frameTimeNanos
                        
                        val duration = animator.getAnimationDuration(0)
                        val holdDuration = 2.0f // seconds
                        val totalCycleDuration = duration + holdDuration
                        
                        val elapsedSeconds = (frameTimeNanos - startTimeNanos).toDouble() / 1_000_000_000.0
                        val timeInCycle = (elapsedSeconds % totalCycleDuration).toFloat()
                        
                        // If the animation is a perfect loop, the last frame (duration) is identical to the first frame (0).
                        // We hold a frame slightly before the absolute end to ensure the 'finished' pose is visible.
                        val animationTime = if (timeInCycle < duration) {
                            timeInCycle
                        } else {
                            // Hold at slightly before the end to avoid wrapping back to the start frame (0)
                            (duration - 0.05f).coerceAtLeast(0f)
                        }
                        
                        animator.applyAnimation(0, animationTime)
                        animator.updateBoneMatrices()
                        
                        super.onFrame(frameTimeNanos)
                    }
                }.apply {
                    position = Position(y = -2f)
                }
            } else {
                ModelNode(
                    modelInstance = it,
                    scaleToUnits = 0.05f
                ).apply {
                    position = Position(y = -2f)
                    playAnimation(0, loop = true)
                }
            }
        }
    }

    Scene(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        cameraNode = rememberCameraNode(engine) {
            position = Position(z = 8f)
        },
        childNodes = listOfNotNull(modelNode)
    )
}