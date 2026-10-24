package io.github.thomasvanriel.solitaire

import android.content.Intent
import android.os.StrictMode
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.game.GameViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-build-only tools. The release source set has an empty version of this object, so none of
 * this code is in release builds.
 *
 * - StrictMode, logging disk/network access on the main thread.
 * - "Auto" button and an intent extra that replay the deal's stored solution in the real UI:
 *   adb shell am start -S -n io.github.thomasvanriel.solitaire/.MainActivity --ez autoplay true [--el stepMs 60]
 *   (-S force-stops first: if the app is already open, Android only brings it to the front and drops the extras.)
 */
object DebugTools {
    fun install() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
    }

    fun handleIntent(owner: LifecycleOwner, intent: Intent?, vm: GameViewModel) {
        if (intent?.getBooleanExtra("autoplay", false) != true) return
        val step = intent.getLongExtra("stepMs", 60L)
        owner.lifecycleScope.launch {
            vm.state.first { !it.loading && it.game != null }
            autoplay(vm, step)
        }
    }

    /** Restarts the deal and plays its verified solution, one move at a time. */
    fun autoplay(vm: GameViewModel, stepMillis: Long) {
        vm.restart()
        val moves: Iterator<Move> = vm.solution.iterator()
        vm.runScript(stepMillis) { if (moves.hasNext()) moves.next() else null }
    }

    @Composable
    fun ExtraButtons(vm: GameViewModel) {
        TextButton(onClick = { autoplay(vm, 60L) }) { Text("Auto", color = Color.White) }
    }
}
