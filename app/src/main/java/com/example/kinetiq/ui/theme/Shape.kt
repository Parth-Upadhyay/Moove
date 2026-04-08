package com.example.kinetiq.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp), // Chips and Tags
    small = RoundedCornerShape(20.dp),      // Small components
    medium = RoundedCornerShape(20.dp),     // Cards
    large = RoundedCornerShape(
        topStart = 28.dp, 
        topEnd = 28.dp, 
        bottomEnd = 0.dp, 
        bottomStart = 0.dp
    ), // Bottom sheets and Modals
    extraLarge = RoundedCornerShape(50.dp)  // Buttons (Pill shape)
)
