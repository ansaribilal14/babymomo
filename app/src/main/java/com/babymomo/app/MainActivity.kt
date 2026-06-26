package com.babymomo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.babymomo.app.ui.nav.BabymomoNavHost
import com.babymomo.app.ui.theme.BabymomoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Do NOT use enableEdgeToEdge() — it causes blank space behind status bar
        // because our TopAppBar inside Column doesn't handle insets properly.
        // The dark theme already matches the system bars.
        setContent {
            BabymomoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BabymomoNavHost()
                }
            }
        }
    }
}
