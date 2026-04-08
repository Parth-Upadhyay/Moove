package com.example.kinetiq.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.kinetiq.ui.theme.WarmSurface
import com.example.kinetiq.ui.theme.WarmSurfaceVariant

/**
 * A reusable Glass Card component that applies a translucent, blurred background.
 * 
 * @param modifier Modifier for the card's layout
 * @param shape The shape of the card (defaults to MaterialTheme.shapes.medium)
 * @param content The composable content to be displayed inside the card
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit
) {
    val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = 1.dp,
                color = WarmSurfaceVariant.copy(alpha = 0.6f),
                shape = shape
            )
    ) {
        // Glass Background Layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (isApi31Plus) {
                        Modifier
                            .background(Color.White.copy(alpha = 0.45f))
                            .blur(14.dp)
                    } else {
                        Modifier.background(WarmSurface)
                    }
                )
        )

        // Content Layer
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
