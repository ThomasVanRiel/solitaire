package io.github.thomasvanriel.solitaire.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thomasvanriel.solitaire.DebugTools
import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.pack.DealCode
import io.github.thomasvanriel.solitaire.extras.Achievement
import io.github.thomasvanriel.solitaire.game.GameUiState
import io.github.thomasvanriel.solitaire.game.GameViewModel
import kotlinx.coroutines.delay

private val Felt = Color(0xFF1B5E20)
private val FeltDark = Color(0xFF0F3D14)

@Composable
fun GameScreen(vm: GameViewModel, dark: Boolean) {
    val ui by vm.state.collectAsStateWithLifecycle()
    var showNewGame by rememberSaveable { mutableStateOf(false) }
    var showRestart by rememberSaveable { mutableStateOf(false) }
    var showShare by rememberSaveable { mutableStateOf(false) }
    var showStats by rememberSaveable { mutableStateOf(false) }

    if (showStats) {
        val stats by vm.stats.collectAsStateWithLifecycle()
        val unlocked by vm.unlockedAchievements.collectAsStateWithLifecycle()
        StatsScreen(stats, unlocked, initialRuleSet = ui.ruleSet, onReset = vm::resetStats, onClose = { showStats = false })
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
                onHint = vm::hint,
                onStats = { showStats = true },
                onShare = { showShare = true },
                extra = { DebugTools.ExtraButtons(vm) },
            )
            val game = ui.game
            if (game == null || ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            } else {
                Board(
                    game = game,
                    enabled = !ui.busy && !ui.won,
                    onStock = vm::tapStock,
                    onTap = vm::tap,
                    onDrop = { source, target -> vm.drop(source, target) },
                    legalTargets = vm::legalTargets,
                    seed = ui.seed,
                    // Shorter while the game plays moves itself (auto-finish, debug autoplay).
                    animationMillis = if (ui.busy) 70 else 170,
                    modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                    hint = ui.hint,
                )
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val notice = ui.notice
            if (notice != null) {
                Surface(shape = MaterialTheme.shapes.medium, color = Color(0xE6212121)) {
                    Text(notice, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
            if (ui.stuck && !ui.won) StuckBanner(ui.stuckLine, onUndo = vm::undo, onNewGame = { showNewGame = true })
        }
        // Let the last cards finish sliding before the win screen covers them.
        var winShown by remember { mutableStateOf(false) }
        LaunchedEffect(ui.won, ui.busy) {
            winShown = false
            if (ui.won && !ui.busy) {
                delay(650)
                winShown = true
            }
        }
        if (winShown && ui.won && !ui.busy) WinOverlay(ui, onNewGame = { showNewGame = true }, onShare = { showShare = true })
        // On top of everything, and lets every tap through.
        val popups by vm.achievementPopups.collectAsStateWithLifecycle()
        AchievementPopup(popups.firstOrNull(), onShown = vm::achievementShown, modifier = Modifier.align(Alignment.TopCenter))
    }

    if (showShare) ShareDialog(ui, onDismiss = { showShare = false })

    if (showRestart) {
        AlertDialog(
            onDismissRequest = { showRestart = false },
            title = { Text("Restart this deal?") },
            text = { Text("All moves are cleared and the clock starts again. This can't be undone.") },
            confirmButton = {
                Button(onClick = {
                    showRestart = false
                    vm.restartByPlayer()
                }) { Text("Restart") }
            },
            dismissButton = { TextButton(onClick = { showRestart = false }) { Text("Cancel") } },
        )
    }

    if (showNewGame) {
        val greeting = remember { vm.greeting() }
        NewGameDialog(
            greeting = greeting,
            initialRuleSet = vm.lastRuleSet,
            initialTier = vm.lastTier,
            onPlayCode = { rs, seed ->
                showNewGame = false
                vm.playDeal(rs, seed)
            },
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
    onHint: () -> Unit,
    onStats: () -> Unit,
    onShare: () -> Unit,
    extra: @Composable () -> Unit,
) {
    val hasMoves = (ui.game?.canUndo == true) && !ui.busy
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Two text buttons and three icon buttons share a phone's width.
            val pad = PaddingValues(horizontal = 8.dp)
            val canHint = ui.game != null && !ui.busy && !ui.won && !ui.loading
            Button(onClick = onNewGame, modifier = Modifier.weight(1.1f), contentPadding = pad) { Text("New game", maxLines = 1) }
            FilledTonalButton(onClick = onRestart, enabled = hasMoves, modifier = Modifier.weight(1f), contentPadding = pad) {
                Text("Restart", maxLines = 1)
            }
            IconTopButton("Undo", onUndo, enabled = hasMoves) { UndoIcon() }
            IconTopButton("Hint", onHint, enabled = canHint) {
                Text("?", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            IconTopButton("Statistics", onStats) { BarChartIcon() }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Tap the deal to share its code. It gets the space left over, and is cut short if it must be.
            Box(Modifier.weight(1f)) {
                TextButton(onClick = onShare, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(
                        "#${ui.seed} · ${ui.ruleSet.label} · ${ui.tier.label}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            extra()
            Text(
                "${ui.moveCount} moves · ${formatTime(ui.elapsedSeconds)}",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun WinOverlay(ui: GameUiState, onNewGame: () -> Unit, onShare: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Room is kept for the scorecards while they show, so the win card doesn't jump when they go.
            ui.scorecards?.let { Box(Modifier.height(80.dp)) { Scorecards(it) } }
            Card(Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You won!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Deal #${ui.seed} (${ui.ruleSet.label}, ${ui.tier.label})\n" +
                            "${ui.moveCount} moves in ${formatTime(ui.elapsedSeconds)}",
                        textAlign = TextAlign.Center,
                    )
                    if (ui.hintsUsed > 0) {
                        Text(if (ui.hintsUsed == 1) "1 hint used" else "${ui.hintsUsed} hints used", textAlign = TextAlign.Center)
                    }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onShare) { Text("Share deal") }
                        Button(onClick = onNewGame) { Text("New game") }
                    }
                    ui.fortune?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Stays up while no move that helps is left, until the player undoes or starts again. */
@Composable
private fun StuckBanner(quip: String?, onUndo: () -> Unit, onNewGame: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = Color(0xF2212121)) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text("No moves left that help", color = Color.White, fontWeight = FontWeight.Bold)
            if (quip != null) {
                Text(quip, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
            }
            Text(
                "This position can't be won any more. Undo a few moves and try another way.",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onNewGame) { Text("New game", color = Color.White) }
                TextButton(onClick = onUndo) { Text("Undo", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewGameDialog(
    greeting: String,
    initialRuleSet: RuleSet,
    initialTier: Tier,
    onPlayCode: (RuleSet, Long) -> Unit,
    onStart: (RuleSet, Tier) -> Unit,
    onDismiss: () -> Unit,
) {
    var ruleSet by rememberSaveable { mutableStateOf(initialRuleSet.id) }
    var tier by rememberSaveable { mutableStateOf(initialTier.id) }
    var code by rememberSaveable { mutableStateOf("") }
    val parsed = DealCode.parse(code)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New game") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(greeting, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Italic)
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(24) },
                    label = { Text("Or play a deal code") },
                    placeholder = { Text("e.g. S2-17") },
                    singleLine = true,
                    isError = code.isNotBlank() && parsed == null,
                    supportingText = {
                        Text(
                            when {
                                code.isBlank() -> "Someone shared a deal? Type its code here."
                                parsed == null -> "Not a deal code. Codes look like K1-100 or SC-6."
                                else -> "${parsed.first.label}, deal #${parsed.second}"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                    // The keyboard's Go key plays a valid code, since the keyboard can cover the buttons.
                    keyboardActions = KeyboardActions(onGo = { parsed?.let { onPlayCode(it.first, it.second) } }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (parsed != null) {
                Button(onClick = { onPlayCode(parsed.first, parsed.second) }) { Text("Play ${DealCode.format(parsed.first, parsed.second)}") }
            } else {
                Button(onClick = { onStart(RuleSet.byId(ruleSet), Tier.byId(tier)) }, enabled = code.isBlank()) { Text("Deal") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Shows the current deal's code, to copy or send with the system share sheet. */
@Composable
private fun ShareDialog(ui: GameUiState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val code = DealCode.format(ui.ruleSet, ui.seed)
    val message = "Can you win Solitaire deal $code (${ui.ruleSet.label}, ${ui.tier.label})? " +
        "Type the code in New game to play it."
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share this deal") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(code, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${ui.ruleSet.label} · ${ui.tier.label}. Anyone with this app can type the code in New game " +
                        "to play exactly this deal.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
                context.startActivity(Intent.createChooser(send, "Share deal $code"))
                onDismiss()
            }) { Text("Share") }
        },
        dismissButton = {
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Deal code", code))
                onDismiss()
            }) { Text("Copy code") }
        },
    )
}

/** A small square button with a drawn icon; [label] is read out by screen readers. */
@Composable
private fun IconTopButton(label: String, onClick: () -> Unit, enabled: Boolean = true, icon: @Composable () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(48.dp).semantics { contentDescription = label },
        contentPadding = PaddingValues(0.dp),
    ) { icon() }
}

/** A curved arrow pointing back, drawn in code (no icon library). */
@Composable
private fun UndoIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val curve = Path().apply {
            moveTo(0.86f * w, 0.86f * h)
            cubicTo(0.86f * w, 0.52f * h, 0.66f * w, 0.36f * h, 0.36f * w, 0.36f * h)
            lineTo(0.24f * w, 0.36f * h)
        }
        drawPath(curve, color, style = Stroke(width = 0.12f * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val head = Path().apply {
            moveTo(0.04f * w, 0.36f * h)
            lineTo(0.36f * w, 0.10f * h)
            lineTo(0.36f * w, 0.62f * h)
            close()
        }
        drawPath(head, color)
    }
}

/** Three bars, drawn in code (no icon library). */
@Composable
private fun BarChartIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(18.dp)) {
        val bar = size.width / 4.6f
        val gap = (size.width - 3 * bar) / 2
        listOf(0.55f, 1f, 0.75f).forEachIndexed { i, h ->
            drawRoundRect(
                color,
                topLeft = Offset(i * (bar + gap), size.height * (1 - h)),
                size = Size(bar, size.height * h),
                cornerRadius = CornerRadius(bar / 4),
            )
        }
    }
}

/**
 * A just-earned achievement, sliding in at the top for a few seconds. It has no touch handling, so taps go
 * straight through to whatever is under it. [onShown] is called once it has slid away.
 */
@Composable
private fun AchievementPopup(achievement: Achievement?, onShown: (Achievement) -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<Achievement?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(achievement) {
        if (achievement == null) return@LaunchedEffect
        shown = achievement
        visible = true
        delay(3000)
        visible = false
        delay(400) // let it slide away
        onShown(achievement)
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        val a = shown ?: return@AnimatedVisibility
        Row(
            Modifier
                .padding(12.dp)
                .background(Color(0xF2212121), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🏆", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Achievement: ${a.title}", color = Color.White, fontWeight = FontWeight.Bold)
                Text(a.description, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The rare easter egg: three cards holding up judges' scores for a moment, then dropping out of sight. */
@Composable
private fun Scorecards(scores: List<String>, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(scores) {
        visible = true
        delay(2200)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn() + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (score in scores) {
                Box(
                    Modifier
                        .size(width = 52.dp, height = 72.dp)
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF9E9E9E), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(score, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
            }
        }
    }
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
