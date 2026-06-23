package com.babymomo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign

/**
 * Minimal Activity — no Hilt, no DI, no ViewModel.
 * Shows a simple screen to verify the app can open without crashing.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeModeScreen()
        }
    }
}

@Composable
private fun SafeModeScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFFFF8F1)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // MOMO logo — simple circle
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFFD97F3F), RoundedCornerShape(40.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("😊", fontSize = 36.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "BABYMOMO",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2A1F18)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Safe mode — diagnostic build",
                fontSize = 14.sp,
                color = Color(0xFF8A7866)
            )

            Spacer(Modifier.height(32.dp))

            Text(
                "If you see this screen, the app can open.\n" +
                "The full AI features will be re-enabled in the next update.\n\n" +
                "Version: 0.5.4-safe-mode",
                fontSize = 13.sp,
                color = Color(0xFFA8967F),
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
