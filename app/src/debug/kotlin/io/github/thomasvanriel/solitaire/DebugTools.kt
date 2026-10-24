package io.github.thomasvanriel.solitaire

import android.content.Intent
import android.os.StrictMode
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * - Screenshot mode, for README and store screenshots: `--ez hideDebugUi true` hides the Auto button, and
 *   `--ei stopAt N` makes the autoplay stop after N moves (so a mid-game position can be captured).
 */
object DebugTools {
    private var hideDebugUi by mutableStateOf(false)

    fun install() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
    }

    fun handleIntent(owner: LifecycleOwner, intent: Intent?, vm: GameViewModel) {
        if (intent == null) return
        hideDebugUi = intent.getBooleanExtra("hideDebugUi", false)
        if (!intent.getBooleanExtra("autoplay", false)) return
        val step = intent.getLongExtra("stepMs", 60L)
        val stopAt = intent.getIntExtra("stopAt", -1)
        owner.lifecycleScope.launch {
            vm.state.first { !it.loading && it.game != null }
            autoplay(vm, step, stopAt)
        }
    }

    /** Restarts the deal and plays its verified solution, one move at a time ([stopAt] moves at most, if set). */
    fun autoplay(vm: GameViewModel, stepMillis: Long, stopAt: Int = -1) {
        vm.restart()
        vm.markAssisted()
        val moves: List<Move> = if (stopAt >= 0) vm.solution.take(stopAt) else vm.solution
        val it: Iterator<Move> = moves.iterator()
        vm.runScript(stepMillis) { if (it.hasNext()) it.next() else null }
    }

    @Composable
    fun ExtraButtons(vm: GameViewModel) {
        if (hideDebugUi) return
        TextButton(onClick = { autoplay(vm, 60L) }) { Text("Auto", color = Color.White) }
    }
}
