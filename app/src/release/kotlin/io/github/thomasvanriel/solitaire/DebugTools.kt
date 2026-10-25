package io.github.thomasvanriel.solitaire

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import io.github.thomasvanriel.solitaire.game.GameViewModel

/** Release builds: no debug tools. */
object DebugTools {
    fun install() {}

    @Suppress("UNUSED_PARAMETER")
    fun forcedScorecards(seed: Long): List<String>? = null

    @Suppress("UNUSED_PARAMETER")
    fun handleIntent(owner: LifecycleOwner, intent: Intent?, vm: GameViewModel) {}

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun ExtraButtons(vm: GameViewModel) {}
}
