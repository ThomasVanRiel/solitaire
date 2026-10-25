package io.github.thomasvanriel.solitaire

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import io.github.thomasvanriel.solitaire.game.GameViewModel
import io.github.thomasvanriel.solitaire.ui.GameScreen
import io.github.thomasvanriel.solitaire.ui.cosyColorScheme

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        DebugTools.install()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = cosyColorScheme(dark)) {
                GameScreen(vm, dark)
            }
        }
        if (savedInstanceState == null) DebugTools.handleIntent(this, intent, vm)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DebugTools.handleIntent(this, intent, vm)
    }

    override fun onResume() {
        super.onResume()
        vm.setClockRunning(true)
    }

    override fun onPause() {
        vm.setClockRunning(false)
        super.onPause()
    }
}
