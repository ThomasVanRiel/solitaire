package io.github.thomasvanriel.solitaire.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.thomasvanriel.solitaire.BuildConfig
import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.extras.Achievement
import io.github.thomasvanriel.solitaire.extras.Achievements
import io.github.thomasvanriel.solitaire.game.ModeStats
import io.github.thomasvanriel.solitaire.game.TierStats
import io.github.thomasvanriel.solitaire.game.combinedStats

@Composable
fun StatsScreen(
    stats: Map<RuleSet, ModeStats>,
    unlocked: Set<String>,
    initialRuleSet: RuleSet,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var ruleSetId by rememberSaveable { mutableStateOf(initialRuleSet.id) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val ruleSet = RuleSet.byId(ruleSetId)
    val mode = if (showAll) combinedStats(stats.values) else stats[ruleSet] ?: ModeStats()

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Statistics", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close") }
            }
            Spacer(Modifier.height(8.dp))
            RuleSetPicker(
                ruleSet,
                onSelect = {
                    ruleSetId = it.id
                    showAll = false
                },
                showTitles = false,
                onSelectAll = { showAll = true },
                allSelected = showAll,
            )
            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SummaryLine("Played", mode.played.toString())
                    SummaryLine("Won", mode.won.toString())
                    SummaryLine("Win rate", percent(mode.won, mode.played))
                    if (showAll) {
                        SummaryLine("Best streak (one game)", mode.bestStreak.toString())
                    } else {
                        SummaryLine("Current streak", mode.currentStreak.toString())
                        SummaryLine("Best streak", mode.bestStreak.toString())
                    }
                    SummaryLine("Won without undo", mode.wonWithoutUndo.toString())
                    SummaryLine("Won without hints", mode.wonWithoutHints.toString())
                    SummaryLine("Hints used", mode.hints.toString())
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("By difficulty", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (showAll) {
                TableRow(listOf("", "Played", "Won", "Win %"), header = true)
                HorizontalDivider()
                for (tier in Tier.entries) {
                    val t = mode.tiers[tier] ?: TierStats()
                    TableRow(listOf(tier.label, t.played.toString(), t.won.toString(), percent(t.won, t.played)))
                }
                Spacer(Modifier.height(16.dp))
                Text("By game", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                TableRow(listOf("", "Played", "Won", "Win %"), header = true)
                HorizontalDivider()
                for (rs in RuleSet.ALL) {
                    val m = stats[rs] ?: ModeStats()
                    val name = if (rs.game == GameType.KLONDIKE) "Klondike ${rs.label.lowercase()}" else rs.label
                    TableRow(listOf(name, m.played.toString(), m.won.toString(), percent(m.won, m.played)))
                }
            } else {
                TableRow(listOf("", "Played", "Won", "Win %", "Fastest", "Fewest"), header = true)
                HorizontalDivider()
                for (tier in Tier.entries) {
                    val t = mode.tiers[tier] ?: TierStats()
                    TableRow(
                        listOf(
                            tier.label,
                            t.played.toString(),
                            t.won.toString(),
                            percent(t.won, t.played),
                            if (t.bestTime < 0) "–" else formatSeconds(t.bestTime),
                            if (t.fewestMoves < 0) "–" else t.fewestMoves.toString(),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Fewest = fewest moves in a win. Hints used counts every hint shown. A game counts as played from its first move; starting a new " +
                    "game before winning ends the streak. Statistics stay on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
            val earned = Achievements.ALL.count { it.id in unlocked }
            Text("Achievements ($earned of ${Achievements.ALL.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            // Earned ones first; the rest are greyed out, with how to get them.
            for (a in Achievements.ALL.sortedBy { it.id !in unlocked }) AchievementRow(a, a.id in unlocked)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { confirmReset = true }) { Text("Reset statistics") }
            Spacer(Modifier.height(16.dp))
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset statistics?") },
            text = { Text("All statistics and achievements for every game are deleted. This can't be undone.") },
            confirmButton = {
                Button(onClick = {
                    confirmReset = false
                    onReset()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AchievementRow(a: Achievement, earned: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).alpha(if (earned) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (earned) "🏆" else "🔒", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(a.title, fontWeight = FontWeight.Bold)
            Text(a.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, text ->
            Text(
                text,
                modifier = Modifier.weight(if (i == 0) 1.5f else 1f),
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.Bold else null,
                maxLines = 1,
            )
        }
    }
}

private fun percent(won: Int, played: Int): String = if (played == 0) "–" else "${(100.0 * won / played).toInt()}%"

fun formatSeconds(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
