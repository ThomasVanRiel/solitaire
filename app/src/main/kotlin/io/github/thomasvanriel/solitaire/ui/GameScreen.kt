package io.github.thomasvanriel.solitaire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thomasvanriel.solitaire.DebugTools
import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.game.GameUiState
import io.github.thomasvanriel.solitaire.game.GameViewModel

private val Felt = Color(0xFF1B5E20)
private val FeltDark = Color(0xFF0F3D14)

@Composable
fun GameScreen(vm: GameViewModel, dark: Boolean) {
    val ui by vm.state.collectAsStateWithLifecycle()
    var showNewGame by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) FeltDark else Felt),
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(ui, onNewGame = { showNewGame = true }, onUndo = vm::undo, extra = { DebugTools.ExtraButtons(vm) })
            val game = ui.game
            if (game == null || ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            } else {
                Board(
                    state = game.state,
                    drawCount = ui.ruleSet.drawCount,
                    enabled = !ui.busy && !ui.won,
                    onStock = vm::tapStock,
                    onTap = vm::tap,
                    onDrop = { source, target -> vm.drop(source, target) },
                    legalTargets = vm::legalTargets,
                    modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                )
            }
        }
        if (ui.won && !ui.busy) WinOverlay(ui, onNewGame = { showNewGame = true })
    }

    if (showNewGame) {
        NewGameDialog(
            initialRuleSet = vm.lastRuleSet,
            initialTier = vm.lastTier,
            onStart = { rs, tier ->
                showNewGame = false
                vm.newGame(rs, tier)
            },
            onDismiss = { showNewGame = false },
        )
    }
}

@Composable
private fun TopBar(ui: GameUiState, onNewGame: () -> Unit, onUndo: () -> Unit, extra: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onNewGame) { Text("New game") }
        FilledTonalButton(onClick = onUndo, enabled = (ui.game?.canUndo == true) && !ui.busy) { Text("Undo") }
        extra()
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "#${ui.seed} · Draw ${ui.ruleSet.drawCount} · ${ui.tier.label}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "${ui.moveCount} moves · ${formatTime(ui.elapsedSeconds)}",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun WinOverlay(ui: GameUiState, onNewGame: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Card(Modifier.padding(32.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("You won!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Deal #${ui.seed} (${ui.tier.label}, draw ${ui.ruleSet.drawCount})\n" +
                        "${ui.moveCount} moves in ${formatTime(ui.elapsedSeconds)}",
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onNewGame) { Text("New game") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewGameDialog(
    initialRuleSet: RuleSet,
    initialTier: Tier,
    onStart: (RuleSet, Tier) -> Unit,
    onDismiss: () -> Unit,
) {
    var ruleSet by rememberSaveable { mutableStateOf(initialRuleSet.id) }
    var tier by rememberSaveable { mutableStateOf(initialTier.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New game") },
        text = {
            Column {
                Text("Every deal can be won.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("Draw", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (rs in RuleSet.ALL) {
                        FilterChip(
                            selected = ruleSet == rs.id,
                            onClick = { ruleSet = rs.id },
                            label = { Text(if (rs.drawCount == 1) "1 card" else "3 cards") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Difficulty", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (t in Tier.entries) {
                        FilterChip(selected = tier == t.id, onClick = { tier = t.id }, label = { Text(t.label) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onStart(RuleSet.byId(ruleSet), Tier.byId(tier)) }) { Text("Deal") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatTime(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
