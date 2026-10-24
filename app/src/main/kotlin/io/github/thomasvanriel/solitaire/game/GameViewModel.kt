package io.github.thomasvanriel.solitaire.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.rules.Game
import io.github.thomasvanriel.solitaire.engine.rules.Rules
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a dragged or tapped card comes from. */
sealed class Source {
    data object Waste : Source()
    data class Column(val col: Int, val index: Int) : Source()
    data class Foundation(val suit: Int) : Source()
}

/** Where a card can be dropped. */
sealed class Target {
    data class Column(val col: Int) : Target()
    data object Foundation : Target()
}

data class GameUiState(
    val game: Game? = null,
    val seed: Long = 0,
    val ruleSet: RuleSet = RuleSet.DRAW_1_UNLIMITED,
    val tier: Tier = Tier.MEDIUM,
    val elapsedSeconds: Long = 0,
    val loading: Boolean = true,
    val busy: Boolean = false,
    /** Counted as played in the statistics (from the first move). */
    val counted: Boolean = false,
    val usedUndo: Boolean = false,
    /** Played by the debug autoplay: never counted in the statistics. */
    val assisted: Boolean = false,
    /** The win is already in the statistics (so undoing and winning again doesn't count twice). */
    val winRecorded: Boolean = false,
    /** What the win changed, for the win screen. Not saved. */
    val winRecord: WinRecord? = null,
) {
    val won: Boolean get() = game?.isWon == true
    val moveCount: Int get() = game?.moves?.size ?: 0
}

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val deals = DealRepository(app)
    private val store = SavedGameStore(app)
    private val statsStore = StatsStore(app)

    private val _stats = MutableStateFlow<Map<RuleSet, ModeStats>>(emptyMap())
    val stats: StateFlow<Map<RuleSet, ModeStats>> = _stats.asStateFlow()

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    /** Winning moves for the current deal (used by the debug-only autoplay). */
    var solution: List<Move> = emptyList()
        private set

    private var clockRunning = false
    private var scriptJob: Job? = null

    val lastRuleSet: RuleSet get() = store.lastRuleSet
    val lastTier: Tier get() = store.lastTier

    init {
        viewModelScope.launch { restoreOrStart() }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val s = _state.value
                if (clockRunning && s.game != null && !s.won && s.moveCount > 0) {
                    _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                }
            }
        }
    }

    private suspend fun restoreOrStart() {
        _stats.value = withContext(Dispatchers.IO) { statsStore.loadAll() }
        val saved = withContext(Dispatchers.IO) { store.load() }
        if (saved != null) {
            val restored = withContext(Dispatchers.Default) {
                runCatching {
                    val deal = deals.find(saved.seed, saved.ruleSet, saved.tier) ?: return@runCatching null
                    deal to Game.replay(Deals.deal(deal.seed, deal.shuffleVersion), deal.ruleSet, saved.moves)
                }.getOrNull()
            }
            if (restored != null) {
                val (deal, game) = restored
                solution = deal.solution
                _state.value = GameUiState(
                    game, deal.seed, deal.ruleSet, deal.tier, saved.elapsedSeconds, loading = false,
                    counted = saved.counted, usedUndo = saved.usedUndo, assisted = saved.assisted,
                    winRecorded = saved.winRecorded,
                )
                return
            }
        }
        val (ruleSet, tier) = withContext(Dispatchers.IO) { store.lastRuleSet to store.lastTier }
        startNewGame(ruleSet, tier)
    }

    fun newGame(ruleSet: RuleSet, tier: Tier) {
        val s = _state.value
        if (s.counted && !s.won && !s.assisted) {
            statsStore.recordLoss(s.ruleSet)
            refreshStats()
        }
        store.lastRuleSet = ruleSet
        store.lastTier = tier
        scriptJob?.cancel()
        viewModelScope.launch { startNewGame(ruleSet, tier) }
    }

    private suspend fun startNewGame(ruleSet: RuleSet, tier: Tier) {
        _state.update { it.copy(loading = true) }
        val deal = withContext(Dispatchers.IO) { deals.next(ruleSet, tier) }
        solution = deal.solution
        val game = Game.start(Deals.deal(deal.seed, deal.shuffleVersion), deal.ruleSet)
        _state.value = GameUiState(game, deal.seed, deal.ruleSet, deal.tier, 0, loading = false)
        save()
    }

    fun setClockRunning(running: Boolean) {
        clockRunning = running
        if (!running) save()
    }

    // ------------------------------------------------------------------ moves

    private fun play(move: Move): Boolean {
        val s = _state.value
        val game = s.game ?: return false
        if (s.busy || s.won || !game.isLegal(move)) return false
        _state.value = s.copy(game = game.play(move))
        afterMove()
        save()
        maybeFinish()
        return true
    }

    fun undo() {
        val s = _state.value
        val game = s.game ?: return
        if (s.busy || !game.canUndo) return
        _state.value = s.copy(game = game.undo(), usedUndo = true)
        save()
    }

    fun tapStock() {
        val game = _state.value.game ?: return
        play(if (game.state.stockSize > 0) Move.Draw else Move.Recycle)
    }

    /** Sends the tapped card (or run) to the best place: its foundation first, then the most useful column. */
    fun tap(source: Source) {
        val game = _state.value.game ?: return
        for (move in candidateMoves(game.state, source)) {
            if (game.isLegal(move)) {
                play(move)
                return
            }
        }
    }

    fun drop(source: Source, target: Target): Boolean {
        val move = moveFor(source, target) ?: return false
        return play(move)
    }

    /** Targets [source] could legally be dropped on, for highlighting during a drag. */
    fun legalTargets(source: Source): List<Target> {
        val game = _state.value.game ?: return emptyList()
        val all = listOf<Target>(Target.Foundation) + (0 until GameState.COLUMNS).map { Target.Column(it) }
        return all.filter { t -> moveFor(source, t)?.let { game.isLegal(it) } == true }
    }

    private fun moveFor(source: Source, target: Target): Move? {
        val game = _state.value.game ?: return null
        val s = game.state
        return when (source) {
            Source.Waste -> when (target) {
                Target.Foundation -> Move.WasteToFoundation
                is Target.Column -> Move.WasteToTableau(target.col)
            }
            is Source.Column -> {
                val count = s.tabSize[source.col] - source.index
                when (target) {
                    Target.Foundation -> if (count == 1) Move.TableauToFoundation(source.col) else null
                    is Target.Column -> if (target.col == source.col) null else Move.TableauToTableau(source.col, count, target.col)
                }
            }
            is Source.Foundation -> when (target) {
                Target.Foundation -> null
                is Target.Column -> Move.FoundationToTableau(source.suit, target.col)
            }
        }
    }

    private fun candidateMoves(s: GameState, source: Source): List<Move> {
        val columns = columnPreference(s, source)
        return when (source) {
            Source.Waste -> listOf<Move>(Move.WasteToFoundation) + columns.map { Move.WasteToTableau(it) }
            is Source.Column -> {
                val count = s.tabSize[source.col] - source.index
                val toFoundation = if (count == 1) listOf<Move>(Move.TableauToFoundation(source.col)) else emptyList()
                toFoundation + columns.filter { it != source.col }.map { Move.TableauToTableau(source.col, count, it) }
            }
            is Source.Foundation -> columns.map { Move.FoundationToTableau(source.suit, it) }
        }
    }

    /**
     * Column order for a tapped card: non-empty columns first (closest to the left), then empty ones.
     * A King run that already starts a column doesn't move to another empty column.
     */
    private fun columnPreference(s: GameState, source: Source): List<Int> {
        val nonEmpty = (0 until GameState.COLUMNS).filter { s.tabSize[it] > 0 }
        val kingAlreadyHome = source is Source.Column && source.index == 0
        val empty = if (kingAlreadyHome) emptyList() else (0 until GameState.COLUMNS).filter { s.tabSize[it] == 0 }
        return nonEmpty + empty
    }

    /** When everything is face up and the stock is empty, play the remaining cards up automatically. */
    private fun maybeFinish() {
        val game = _state.value.game ?: return
        val s = game.state
        if (game.isWon || s.stockSize > 0 || s.wasteSize > 0) return
        if ((0 until GameState.COLUMNS).any { s.tabDown[it] > 0 }) return
        runScript { finishMoves() }
    }

    private fun finishMoves(): Move? {
        val game = _state.value.game ?: return null
        val s = game.state
        if (game.isWon) return null
        var best: Move? = null
        var bestRank = 99
        for (c in 0 until GameState.COLUMNS) {
            val top = s.columnTop(c)
            if (top >= 0 && Rules.fitsOnFoundation(s, top) && Card.rank(top) < bestRank) {
                best = Move.TableauToFoundation(c)
                bestRank = Card.rank(top)
            }
        }
        return best
    }

    /**
     * Plays moves one at a time with a short pause, so the player can see them. Input is blocked meanwhile.
     * [nextMove] returns null when done.
     */
    fun runScript(stepMillis: Long = 90, nextMove: () -> Move?) {
        scriptJob?.cancel()
        scriptJob = viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                while (true) {
                    val move = nextMove() ?: break
                    val s = _state.value
                    val game = s.game ?: break
                    if (!game.isLegal(move)) break
                    delay(stepMillis)
                    _state.value = s.copy(game = game.play(move))
                    afterMove()
                    save()
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Back to the start of the current deal, with the clock reset. Also used by the debug autoplay. */
    fun restart() {
        scriptJob?.cancel()
        val s = _state.value
        val game = s.game ?: return
        _state.value = s.copy(
            game = game.restart(), busy = false, elapsedSeconds = 0,
            usedUndo = s.usedUndo || game.canUndo,
        )
        save()
    }

    /** Marks the current game as played by the computer (debug autoplay), so it never counts in the statistics. */
    fun markAssisted() {
        _state.update { it.copy(assisted = true) }
        save()
    }

    fun resetStats() {
        statsStore.reset()
        _stats.value = RuleSet.ALL.associateWith { ModeStats() }
    }

    private fun refreshStats() {
        viewModelScope.launch { _stats.value = withContext(Dispatchers.IO) { statsStore.loadAll() } }
    }

    /** Updates the statistics after a move: played from the first move, and the win once. */
    private fun afterMove() {
        val s = _state.value
        if (s.assisted) return
        var next = s
        if (!s.counted && s.moveCount > 0) {
            statsStore.recordPlayed(s.ruleSet, s.tier)
            next = next.copy(counted = true)
        }
        if (s.won && !s.winRecorded) {
            val record = statsStore.recordWin(s.ruleSet, s.tier, s.elapsedSeconds, s.moveCount, s.usedUndo)
            next = next.copy(winRecorded = true, winRecord = record)
        }
        if (next !== s) {
            _state.value = next
            refreshStats()
        }
    }

    private fun save() {
        val s = _state.value
        val game = s.game ?: return
        store.save(
            SavedGame(
                s.seed, s.ruleSet, s.tier, game.moves, s.elapsedSeconds,
                counted = s.counted, usedUndo = s.usedUndo, assisted = s.assisted, winRecorded = s.winRecorded,
            ),
        )
    }
}
