package com.example.kinetiq.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Moove Color Palette - Warm Olive & Cream
val OlivePrimary = Color(0xFF4E6B21)
val CreamBackground = Color(0xFFF7F4EE)
val SageSecondary = Color(0xFF8FA876)
val DeepOliveTertiary = Color(0xFF2F4213)
val WarmSurface = Color(0xFFEDE8DC)
val WarmSurfaceVariant = Color(0xFFD9D2C0)
val DarkWarmText = Color(0xFF2C2A22)
val MidWarmText = Color(0xFF5C5848)
val LightSageContainer = Color(0xFFC8D9BA)
val MutedWarm = Color(0xFF8C8778)

// Material3 Token Mapping
val MoovePrimary = OlivePrimary
val MooveOnPrimary = CreamBackground
val MoovePrimaryContainer = LightSageContainer
val MooveOnPrimaryContainer = DeepOliveTertiary
val MooveBackground = CreamBackground
val MooveOnBackground = DarkWarmText
val MooveSurface = WarmSurface
val MooveOnSurface = DarkWarmText
val MooveSurfaceVariant = WarmSurfaceVariant
val MooveOnSurfaceVariant = MidWarmText
val MooveSecondary = SageSecondary
val MooveTertiary = DeepOliveTertiary

// UI Helpers
val PrimaryGradient = Brush.linearGradient(listOf(MoovePrimary, MooveSecondary))
val SecondaryGradient = Brush.linearGradient(listOf(MooveSecondary, MoovePrimaryContainer))
val TextDisabled = MutedWarm
val CardBorder = WarmSurfaceVariant

// Legacy names for transition support
val TealPrimary = MoovePrimary
val CreamyWhite = MooveBackground
val TextPrimary = MooveOnBackground
val TextSecondary = MooveOnSurfaceVariant
val SectionBackground = MooveSurface
val SurfaceWhite = MooveOnPrimary
