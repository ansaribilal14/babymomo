package com.babymomo.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babymomo.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is OnboardingState.Ready || state is OnboardingState.Skipped) {
            delay(600)
            onComplete()
        }
    }

    // Animated gradient background
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFF8F1),
                        Color(0xFFFFE8D6).copy(alpha = 0.5f + 0.3f * animProgress),
                        Color(0xFFFFF0E0),
                        Color(0xFFFFF8F1)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(800f, 1600f)
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // === MOMO Logo ===
            MomoLogoAnimated(
                size = 100.dp,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // === App name ===
            Text(
                text = "BABYMOMO",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2A1F18),
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(6.dp))

            // === Tagline ===
            Text(
                text = "Your private AI companion",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8A7866),
                fontWeight = FontWeight.Normal
            )

            Spacer(Modifier.height(56.dp))

            // === State-dependent content ===
            when (state) {
                is OnboardingState.Checking -> {
                    CheckingContent()
                }

                is OnboardingState.Downloading -> {
                    DownloadContent(state as OnboardingState.Downloading)
                }

                is OnboardingState.Ready -> {
                    ReadyContent()
                }

                is OnboardingState.Error -> {
                    ErrorContent(
                        message = (state as OnboardingState.Error).message,
                        onRetry = { vm.retry() },
                        onSkip = { vm.skip() }
                    )
                }

                is OnboardingState.Skipped -> {
                    CircularProgressIndicator(
                        color = Color(0xFFD97F3F),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

// === CHECKING STATE ===
@Composable
private fun CheckingContent() {
    // Pulsing dots indicator
    val transition = rememberInfiniteTransition(label = "dots")
    val scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        Color(0xFFD97F3F).copy(
                            alpha = 0.4f + 0.6f * (if (i == 0) scale else if (i == 1) (scale + 1) % 2 else (scale + 2) % 2)
                        )
                    )
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    Text(
        "Preparing your AI brain...",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF8A7866),
        fontWeight = FontWeight.Medium
    )
}

// === DOWNLOADING STATE ===
@Composable
private fun DownloadContent(dl: OnboardingState.Downloading) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Download icon
        Text(
            text = "↓",
            style = MaterialTheme.typography.displaySmall,
            color = Color(0xFFD97F3F),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            "Downloading AI brain",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2A1F18)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            dl.modelName,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFA8967F)
        )

        Spacer(Modifier.height(28.dp))

        // Progress bar — custom styled
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFE8DCC8))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(dl.progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFD97F3F), Color(0xFFE8B584))
                        )
                    )
            )
        }

        Spacer(Modifier.height(12.dp))

        // Percentage + size
        val percent = (dl.progress * 100).toInt()
        Text(
            "$percent%  ·  ${dl.downloadedMb} / ${dl.totalMb} MB",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF6B5A4C),
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(40.dp))

        // Privacy reassurance
        Text(
            "This happens only once.\nYour conversations never leave your phone.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFA8967F),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

// === READY STATE ===
@Composable
private fun ReadyContent() {
    val transition = rememberInfiniteTransition(label = "ready")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Checkmark in a circle
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color(0xFF7A8C6A).copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "✓",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "Ready!",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF7A8C6A)
    )

    Spacer(Modifier.height(4.dp))

    Text(
        "Opening BABYMOMO...",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF8A7866)
    )
}

// === ERROR STATE ===
@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onSkip: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "⚠",
            color = Color(0xFFB0492E),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            "Download failed",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB0492E)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8A7866),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97F3F)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text("Retry", color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onSkip) {
            Text("Skip for now", color = Color(0xFFA8967F), fontSize = 13.sp)
        }
    }
}

// === ANIMATED LOGO ===
@Composable
private fun MomoLogoAnimated(
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = (12 * breathe).dp,
                shape = CircleShape,
                ambientColor = Color(0xFFD97F3F).copy(alpha = 0.3f),
                spotColor = Color(0xFFD97F3F).copy(alpha = 0.5f)
            )
    ) {
        val canvasW = this.size.width
        val canvasH = this.size.height
        val center = Offset(canvasW / 2f, canvasH / 2f)
        val radius = (minOf(canvasW, canvasH) / 2f) * breathe

        // Outer circle with gradient
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE8B584), Color(0xFFD97F3F)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        // Inner highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.15f),
            radius = radius * 0.7f,
            center = Offset(center.x - radius * 0.15f, center.y - radius * 0.15f)
        )

        // Face
        val eyeOffset = radius * 0.22f
        val eyeRadius = radius * 0.07f
        val eyeY = center.y - radius * 0.08f

        drawCircle(color = Color(0xFF2A1F18), radius = eyeRadius, center = Offset(center.x - eyeOffset, eyeY))
        drawCircle(color = Color(0xFF2A1F18), radius = eyeRadius, center = Offset(center.x + eyeOffset, eyeY))

        // Smile
        drawArc(
            color = Color(0xFF2A1F18),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.3f, center.y + radius * 0.02f),
            size = androidx.compose.ui.geometry.Size(radius * 0.6f, radius * 0.6f),
            style = Stroke(width = radius * 0.05f, cap = StrokeCap.Round)
        )
    }
}
