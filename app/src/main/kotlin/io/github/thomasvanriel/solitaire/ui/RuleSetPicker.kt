package io.github.thomasvanriel.solitaire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet

/**
 * Two rows of chips: the game (Klondike, Spider, Scorpion II), then its variant (draw 1 or 3, 1/2/4 suits)
 * when it has more than one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleSetPicker(selected: RuleSet, onSelect: (RuleSet) -> Unit, showTitles: Boolean = true) {
    Column {
        if (showTitles) Text("Game", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (game in GameType.entries) {
                FilterChip(
                    selected = selected.game == game,
                    onClick = { if (selected.game != game) onSelect(defaultFor(game)) },
                    label = { Text(game.label) },
                )
            }
        }
        val variants = RuleSet.ALL.filter { it.game == selected.game }
        if (variants.size > 1) {
            Spacer(Modifier.height(4.dp))
            if (showTitles) Text(if (selected.game == GameType.KLONDIKE) "Draw" else "Suits", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (rs in variants) {
                    FilterChip(
                        selected = rs.id == selected.id,
                        onClick = { onSelect(rs) },
                        label = { Text(if (rs.game == GameType.KLONDIKE) (if (rs.drawCount == 1) "1 card" else "3 cards") else rs.variantLabel) },
                    )
                }
            }
        }
    }
}

/** The variant picked when switching to [game]: draw 1 for Klondike, 1 suit for Spider (the gentlest). */
private fun defaultFor(game: GameType): RuleSet = RuleSet.ALL.first { it.game == game }
