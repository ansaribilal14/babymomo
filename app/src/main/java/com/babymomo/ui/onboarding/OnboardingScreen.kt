package com.babymomo.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babymomo.ui.theme.Amber
import com.babymomo.ui.theme.AmberDeep
import com.babymomo.ui.theme.AmberSoft
import com.babymomo.ui.theme.Cream
import com.babymomo.ui.theme.Sage
import kotlinx.coroutines.delay

/**
 * OnboardingScreen — the CoD/PUBG-style first-launch loading screen.
 *
 * Shows when the user opens BABYMOMO for the first time (no model downloaded yet).
 * Auto-downloads the default SmolLM-135M model (159 MB) with a progress bar.
 * When complete, automatically proceeds to the Chat screen.
 *
 * The user can also "Skip" to use the mock brain (not recommended — real AI needs a model).
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    // When state becomes Ready or Skipped, signal the parent to navigate to Chat
    LaunchedEffect(state) {
        if (state is OnboardingState.Ready || state is OnboardingState.Skipped) {
            delay(500) // brief pause so the user sees the "Ready" state
            onComplete()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Animated warm gradient background
        val infiniteTransition = rememberInfiniteTransition(label = "bg")
        val gradientShift by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gradientShift"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Cream,
                            AmberSoft.copy(alpha = 0.3f + 0.2f * gradientShift),
                            Cream
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 1500f)
                    )
                )
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // MOMO logo — a simple warm circle with a face
                MomoLogo(
                    size = 120.dp,
                    color = Amber,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    text = "BABYMOMO",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Your private AI companion",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(48.dp))

                when (state) {
                    is OnboardingState.Checking -> {
                        CircularProgressIndicator(
                            color = Amber,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Preparing your AI brain...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is OnboardingState.Downloading -> {
                        val dl = state as OnboardingState.Downloading
                        DownloadProgress(dl)
                    }

                    is OnboardingState.Ready -> {
                        Text(
                            "✓ Ready!",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Sage
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Opening BABYMOMO...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is OnboardingState.Error -> {
                        val err = state as OnboardingState.Error
                        Text(
                            "Download failed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            err.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { vm.retry() }) {
                            Text("Retry")
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.skip() }) {
                            Text("Skip for now (use mock brain)")
                        }
                    }

                    is OnboardingState.Skipped -> {
                        CircularProgressIndicator(color = Amber, modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgress(dl: OnboardingState.Downloading) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Downloading AI brain...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            dl.modelName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { dl.progress },
            color = Amber,
            trackColor = AmberSoft.copy(alpha = 0.3f),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Percentage + size
        val percent = (dl.progress * 100).toInt()
        Text(
            "$percent%  ·  ${dl.downloadedMb} / ${dl.totalMb} MB",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(32.dp))

        Text(
            "This happens only once. BABYMOMO runs entirely on your device —\n" +
            "your conversations never leave your phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MomoLogo(
    size: androidx.compose.ui.unit.Dp,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.size(size)
    ) {
        val canvasSize = this.size.minDimension
        val center = androidx.compose.ui.geometry.Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = canvasSize / 2f

        // Background circle
        drawCircle(
            color = color,
            radius = radius,
            center = center
        )

        // Face — two eyes + smile
        val eyeOffset = radius * 0.25f
        val eyeRadius = radius * 0.08f
        val eyeY = center.y - radius * 0.1f

        drawCircle(
            color = Color.White,
            radius = eyeRadius,
            center = Offset(center.x - eyeOffset, eyeY)
        )
        drawCircle(
            color = Color.White,
            radius = eyeRadius,
            center = Offset(center.x + eyeOffset, eyeY)
        )

        // Smile arc
        drawArc(
            color = Color.White,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.35f, center.y + radius * 0.05f),
            size = androidx.compose.ui.geometry.Size(radius * 0.7f, radius * 0.7f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = radius * 0.06f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}
