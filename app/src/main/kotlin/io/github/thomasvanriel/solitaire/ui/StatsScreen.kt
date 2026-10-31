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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.github.thomasvanriel.solitaire.BuildConfig
import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.extras.Achievement
import io.github.thomasvanriel.solitaire.extras.Achievements
import io.github.thomasvanriel.solitaire.game.DayStats
import io.github.thomasvanriel.solitaire.game.ModeStats
import io.github.thomasvanriel.solitaire.game.TierStats
import io.github.thomasvanriel.solitaire.game.combinedStats
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Where the source code and new releases are. */
private const val SOURCE_URL = "https://github.com/ThomasVanRiel/solitaire"

@Composable
fun StatsScreen(
    stats: Map<RuleSet, ModeStats>,
    days: DayStats,
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

            StatsCard {
                SummaryLine("Played", mode.played.toString())
                SummaryLine("Won", mode.won.toString())
                SummaryLine("Win rate", percent(mode.won, mode.played))
                if (showAll) {
                    SummaryLine("Best streak (one game)", mode.bestStreak.toString())
                } else {
                    SummaryLine("Current streak", mode.currentStreak.toString())
                    SummaryLine("Best streak", mode.bestStreak.toString())
                }
                SummaryLine("Time played", formatDuration(mode.playSeconds))
            }
            Spacer(Modifier.height(12.dp))
            StatsCard {
                SummaryLine("Won without undo", mode.wonWithoutUndo.toString())
                SummaryLine("Won without hints", mode.wonWithoutHints.toString())
                SummaryLine("Hints used", mode.hints.toString())
                SummaryLine("Undos", mode.undos.toString())
                SummaryLine("Restarts", mode.restarts.toString())
                SummaryLine("Times stuck", mode.stuck.toString())
            }
            // Times and move counts only compare within one game.
            val avg = mode.averages
            if (!showAll && avg != null) {
                Spacer(Modifier.height(12.dp))
                StatsCard {
                    SummaryLine("Average win time", formatSeconds(avg.seconds.roundToLong()))
                    SummaryLine("Average moves per win", avg.moves.roundToInt().toString())
                    SummaryLine("Average failed moves per win", oneDecimal(avg.failedMoves))
                    SummaryLine("Average undos per win", oneDecimal(avg.undos))
                }
            }
            Spacer(Modifier.height(12.dp))
            // Days are over every game, so they read the same in each view.
            StatsCard {
                SummaryLine("Days played", days.daysPlayed.toString())
                SummaryLine("Days in a row", days.currentStreak.toString())
                SummaryLine("Most days in a row", days.bestStreak.toString())
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
                TableRow(listOf("", "Played", "Won", "Win %"), header = true)
                HorizontalDivider()
                for (tier in Tier.entries) {
                    val t = mode.tiers[tier] ?: TierStats()
                    TableRow(listOf(tier.label, t.played.toString(), t.won.toString(), percent(t.won, t.played)))
                }
                Spacer(Modifier.height(16.dp))
                Text("Wins by difficulty", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                // Explicit breaks, so the "Avg" labels all wrap alike instead of only where they overflow.
                TableRow(listOf("", "Fastest", "Avg\ntime", "Fewest", "Avg\nmoves", "Avg\nfailed"), header = true)
                HorizontalDivider()
                for (tier in Tier.entries) {
                    val t = mode.tiers[tier] ?: TierStats()
                    val avg = t.averages
                    TableRow(
                        listOf(
                            tier.label,
                            if (t.bestTime < 0) "–" else formatSeconds(t.bestTime),
                            avg?.let { formatSeconds(it.seconds.roundToLong()) } ?: "–",
                            if (t.fewestMoves < 0) "–" else t.fewestMoves.toString(),
                            avg?.let { it.moves.roundToInt().toString() } ?: "–",
                            avg?.let { oneDecimal(it.failedMoves) } ?: "–",
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Fewest = fewest moves in a win. Averages are over wins. A failed move is one the rules refuse. " +
                    "A game counts as played from its first move; starting a new one before winning ends the streak.",
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
            // Opening the link hands it to the browser, so the app itself still needs no network access.
            val uriHandler = LocalUriHandler.current
            val linkStyle = TextLinkStyles(
                SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
            )
            Text(
                buildAnnotatedString {
                    append("Version ${BuildConfig.VERSION_NAME} · ")
                    // No browser installed must not crash the app, so the click is handled here.
                    val link = LinkAnnotation.Url(SOURCE_URL, linkStyle) {
                        runCatching { uriHandler.openUri(SOURCE_URL) }
                    }
                    withLink(link) {
                        append(SOURCE_URL.removePrefix("https://"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
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
private fun StatsCard(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
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
    // Headers sit on the bottom line, next to the divider, whether they take one line or two.
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = if (header) Alignment.Bottom else Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { i, text ->
            Text(
                text,
                // A small gap, so neighbouring right-aligned labels never run into each other.
                modifier = Modifier.weight(if (i == 0) 1.5f else 1f).padding(start = if (i == 0) 0.dp else 4.dp),
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.Bold else null,
                // Header labels such as "Avg time" may wrap on a narrow screen.
                maxLines = if (header) 2 else 1,
            )
        }
    }
}

/** "3 h 12 min", or "12 min" under an hour. */
private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

private fun oneDecimal(x: Double): String = "%.1f".format(x)

private fun percent(won: Int, played: Int): String = if (played == 0) "–" else "${(100.0 * won / played).toInt()}%"

fun formatSeconds(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)
