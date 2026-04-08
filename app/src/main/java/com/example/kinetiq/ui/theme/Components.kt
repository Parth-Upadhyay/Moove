package com.example.kinetiq.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MoovePrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MoovePrimary,
            contentColor = MooveOnPrimary,
            disabledContainerColor = MooveOnSurfaceVariant.copy(alpha = 0.12f),
            disabledContentColor = MooveOnSurfaceVariant.copy(alpha = 0.38f)
        ),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
        content = content
    )
}

@Composable
fun MooveCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MooveSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
        color = backgroundColor,
        content = {
            Column(
                modifier = Modifier.padding(20.dp),
                content = content
            )
        }
    )
}

@Composable
fun MooveProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(CardBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MoovePrimary)
        )
    }
}

@Composable
fun MooveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MoovePrimary,
            unfocusedBorderColor = CardBorder,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = MoovePrimary,
            unfocusedLabelColor = MooveOnSurfaceVariant,
            cursorColor = MoovePrimary,
            focusedTextColor = MooveOnBackground,
            unfocusedTextColor = MooveOnBackground,
            focusedPlaceholderColor = MooveOnSurfaceVariant.copy(alpha = 0.6f),
            unfocusedPlaceholderColor = MooveOnSurfaceVariant.copy(alpha = 0.6f)
        )
    )
}

// Aliases for transition
@Composable fun KinetiqPrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) = MoovePrimaryButton(onClick, modifier, enabled, content)
@Composable fun KinetiqCard(modifier: Modifier = Modifier, isGlassmorphic: Boolean = false, content: @Composable ColumnScope.() -> Unit) = MooveCard(modifier, content = content)
@Composable fun KinetiqProgressBar(progress: Float, modifier: Modifier = Modifier) = MooveProgressBar(progress, modifier)
@Composable fun KinetiqTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, placeholder: String? = null, isError: Boolean = false, singleLine: Boolean = true) = MooveTextField(value, onValueChange, modifier, label, placeholder, isError, singleLine)
