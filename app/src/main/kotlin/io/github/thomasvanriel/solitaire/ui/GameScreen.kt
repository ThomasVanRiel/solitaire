package io.github.thomasvanriel.solitaire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Surface
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
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.game.GameUiState
import io.github.thomasvanriel.solitaire.game.GameViewModel

private val Felt = Color(0xFF1B5E20)
private val FeltDark = Color(0xFF0F3D14)

@Composable
fun GameScreen(vm: GameViewModel, dark: Boolean) {
    val ui by vm.state.collectAsStateWithLifecycle()
    var showNewGame by rememberSaveable { mutableStateOf(false) }
    var showRestart by rememberSaveable { mutableStateOf(false) }
    var showStats by rememberSaveable { mutableStateOf(false) }

    if (showStats) {
        val stats by vm.stats.collectAsStateWithLifecycle()
        StatsScreen(stats, initialRuleSet = ui.ruleSet, onReset = vm::resetStats, onClose = { showStats = false })
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) FeltDark else Felt),
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(
                ui,
                onNewGame = { showNewGame = true },
                onRestart = { showRestart = true },
                onUndo = vm::undo,
                onStats = { showStats = true },
                extra = { DebugTools.ExtraButtons(vm) },
            )
            val game = ui.game
            if (game == null || ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            } else {
                Board(
                    state = game.state,
                    ruleSet = ui.ruleSet,
                    enabled = !ui.busy && !ui.won,
                    onStock = vm::tapStock,
                    onTap = vm::tap,
                    onDrop = { source, target -> vm.drop(source, target) },
                    legalTargets = vm::legalTargets,
                    seed = ui.seed,
                    modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                )
            }
        }
        val notice = ui.notice
        if (notice != null) {
            Surface(
                Modifier.align(Alignment.BottomCenter).padding(24.dp),
                shape = MaterialTheme.shapes.medium,
                color = Color(0xE6212121),
            ) {
                Text(notice, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
        if (ui.won && !ui.busy) WinOverlay(ui, onNewGame = { showNewGame = true })
    }

    if (showRestart) {
        AlertDialog(
            onDismissRequest = { showRestart = false },
            title = { Text("Restart this deal?") },
            text = { Text("All moves are cleared and the clock starts again. This can't be undone.") },
            confirmButton = {
                Button(onClick = {
                    showRestart = false
                    vm.restart()
                }) { Text("Restart") }
            },
            dismissButton = { TextButton(onClick = { showRestart = false }) { Text("Cancel") } },
        )
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
private fun TopBar(
    ui: GameUiState,
    onNewGame: () -> Unit,
    onRestart: () -> Unit,
    onUndo: () -> Unit,
    onStats: () -> Unit,
    extra: @Composable () -> Unit,
) {
    val hasMoves = (ui.game?.canUndo == true) && !ui.busy
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onNewGame, modifier = Modifier.weight(1f)) { Text("New game", maxLines = 1) }
            FilledTonalButton(onClick = onRestart, enabled = hasMoves, modifier = Modifier.weight(1f)) {
                Text("Restart", maxLines = 1)
            }
            FilledTonalButton(onClick = onUndo, enabled = hasMoves, modifier = Modifier.weight(1f)) {
                Text("Undo", maxLines = 1)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onStats) { Text("Stats", color = Color.White) }
            Text(
                "#${ui.seed} · ${ui.ruleSet.label} · ${ui.tier.label}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.weight(1f))
            extra()
            Text(
                "${ui.moveCount} moves · ${formatTime(ui.elapsedSeconds)}",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge,
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
                    "Deal #${ui.seed} (${ui.ruleSet.label}, ${ui.tier.label})\n" +
                        "${ui.moveCount} moves in ${formatTime(ui.elapsedSeconds)}",
                    textAlign = TextAlign.Center,
                )
                val record = ui.winRecord
                if (record != null) {
                    val news = buildList {
                        add("Streak: ${record.streak}")
                        if (record.newBestTime) add("New best time!")
                        if (record.newFewestMoves) add("Fewest moves yet!")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(news.joinToString(" · "), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                }
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
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Every deal can be won. Each one comes with a winning sequence that the game's own " +
                        "rules have checked. You can't see the face-down cards, so you may still get " +
                        "stuck: use Undo and try another way.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                RuleSetPicker(RuleSet.byId(ruleSet), onSelect = { ruleSet = it.id })
                Spacer(Modifier.height(4.dp))
                Text(rulesSummary(RuleSet.byId(ruleSet)), style = MaterialTheme.typography.bodySmall)
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

/** The rules in one or two sentences, for the New game dialog. */
private fun rulesSummary(rs: RuleSet): String = when (rs.game) {
    GameType.KLONDIKE -> "Build down in alternating colours and move every card to the foundations, Ace to King."
    GameType.SPIDER -> "Build down in any suit, but only runs of one suit move together. A King-to-Ace run of " +
        "one suit is taken off. Tap the stock to deal a card onto every column (no column may be empty)."
    GameType.SCORPION -> "Build down in suit. Any face-up card moves together with all the cards on top of it. " +
        "Only a King may fill an empty column. A King-to-Ace run is taken off. The stock deals once, onto " +
        "the first three columns."
}

private fun formatTime(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
