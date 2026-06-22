package com.babymomo.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Simple, friendly MOMO avatar drawn on a Compose Canvas — no asset required.
 * A warm amber circle with two eyes, a soft smile arc, and tiny cheeks. Used in the chat
 * empty state and the drawer header for a consistent brand mark.
 */
@Composable
fun MomoAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    faceColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Canvas(modifier = modifier.size(size)) {
        val canvasSize = this.size.minDimension
        val center = Offset(canvasSize / 2f, canvasSize / 2f)
        val radius = canvasSize / 2f

        // Head circle
        drawCircle(color = backgroundColor, radius = radius, center = center)

        // Soft inner highlight (gives a slight dimensional feel)
        drawCircle(
            color = faceColor.copy(alpha = 0.08f),
            radius = radius * 0.78f,
            center = Offset(center.x - radius * 0.12f, center.y - radius * 0.18f)
        )

        // Eyes — two small dots
        val eyeOffsetX = radius * 0.32f
        val eyeY = center.y - radius * 0.10f
        val eyeRadius = radius * 0.075f
        drawCircle(color = faceColor, radius = eyeRadius, center = Offset(center.x - eyeOffsetX, eyeY))
        drawCircle(color = faceColor, radius = eyeRadius, center = Offset(center.x + eyeOffsetX, eyeY))

        // Smile — drawn as an arc stroke (open-bottom)
        val smileRadius = radius * 0.42f
        val smileBoxSize = smileRadius * 2f
        val smileTopLeft = Offset(center.x - smileRadius, center.y - smileRadius * 0.6f)
        drawArc(
            color = faceColor,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = smileTopLeft,
            size = Size(smileBoxSize, smileBoxSize),
            style = Stroke(width = radius * 0.075f)
        )

        // Cheeks — two tiny warm dots for personality
        val cheekColor = faceColor.copy(alpha = 0.20f)
        val cheekOffsetX = radius * 0.52f
        val cheekY = center.y + radius * 0.08f
        drawCircle(color = cheekColor, radius = radius * 0.07f, center = Offset(center.x - cheekOffsetX, cheekY))
        drawCircle(color = cheekColor, radius = radius * 0.07f, center = Offset(center.x + cheekOffsetX, cheekY))
    }
}
