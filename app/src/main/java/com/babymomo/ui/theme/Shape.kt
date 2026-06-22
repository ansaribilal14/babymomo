package com.babymomo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Cohesive shape scale — rounded but consistent.
 * The intervals (8 / 14 / 20 / 28) step evenly enough to feel designed, and the small/large
 * jump is gentle enough that cards, sheets, and chips all sit comfortably together.
 */
val BABYMOMOShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Chat bubble radii (asymmetric tails). Not part of M3 Shapes — used directly by ChatScreen. */
val BubbleUserShape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
val BubbleMomoShape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
