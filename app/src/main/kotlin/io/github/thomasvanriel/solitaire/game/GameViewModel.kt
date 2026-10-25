package io.github.thomasvanriel.solitaire.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.hint.Advice
import io.github.thomasvanriel.solitaire.engine.hint.MoveAdvisor
import io.github.thomasvanriel.solitaire.engine.model.Card
import io.github.thomasvanriel.solitaire.engine.model.GameState
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.Move
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import io.github.thomasvanriel.solitaire.engine.pack.DealCode
import io.github.thomasvanriel.solitaire.engine.rules.Game
import io.github.thomasvanriel.solitaire.engine.rules.Rules
import io.github.thomasvanriel.solitaire.engine.shuffle.Deals
import io.github.thomasvanriel.solitaire.DebugTools
import io.github.thomasvanriel.solitaire.extras.AbandonFacts
import io.github.thomasvanriel.solitaire.extras.Achievement
import io.github.thomasvanriel.solitaire.extras.AchievementFacts
import io.github.thomasvanriel.solitaire.extras.Achievements
import io.github.thomasvanriel.solitaire.extras.Fortunes
import io.github.thomasvanriel.solitaire.extras.GreetingFacts
import io.github.thomasvanriel.solitaire.extras.Greetings
import io.github.thomasvanriel.solitaire.extras.Quips
import io.github.thomasvanriel.solitaire.extras.RestartFacts
import io.github.thomasvanriel.solitaire.extras.Scorecards
import io.github.thomasvanriel.solitaire.extras.StuckFacts
import io.github.thomasvanriel.solitaire.extras.WinFacts
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    /** A short message shown for a moment, such as why the stock can't deal. */
    val notice: String? = null,
    /** Hints shown in this game (Restart doesn't reset it). */
    val hintsUsed: Int = 0,
    /** The hint on screen, until the next move. */
    val hint: Move? = null,
    /** No move that helps is left: the game can't be won from here without undoing. */
    val stuck: Boolean = false,
    /** Times the player restarted this deal. */
    val restarts: Int = 0,
    /** Times the player got stuck in this game. Not saved. */
    val timesStuck: Int = 0,
    /** The quip in the stuck banner. */
    val stuckLine: String? = null,
    /** The fortune on the win screen. Not saved. */
    val fortune: String? = null,
    /** The rare scorecards easter egg on the win screen, or null. Not saved. */
    val scorecards: List<String>? = null,
) {
    val won: Boolean get() = game?.isWon == true
    val moveCount: Int get() = game?.moves?.size ?: 0
}

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val deals = DealRepository(app)
    private val store = SavedGameStore(app)
    private val statsStore = StatsStore(app)
    private val extras = ExtrasStore(app)

    private val _stats = MutableStateFlow<Map<RuleSet, ModeStats>>(emptyMap())
    val stats: StateFlow<Map<RuleSet, ModeStats>> = _stats.asStateFlow()

    private val _unlocked = MutableStateFlow<Set<String>>(emptySet())

    /** Ids of the achievements earned so far. */
    val unlockedAchievements: StateFlow<Set<String>> = _unlocked.asStateFlow()

    private val _popups = MutableStateFlow<List<Achievement>>(emptyList())

    /** Achievements just earned, to pop up one after another. */
    val achievementPopups: StateFlow<List<Achievement>> = _popups.asStateFlow()

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    /** Winning moves for the current deal (used by the debug-only autoplay). */
    var solution: List<Move> = emptyList()
        private set

    private var clockRunning = false
    private var scriptJob: Job? = null
    private var noticeJob: Job? = null
    private var hintJob: Job? = null

    val lastRuleSet: RuleSet get() = store.lastRuleSet
    val lastTier: Tier get() = store.lastTier

    init {
        viewModelScope.launch { restoreOrStart() }
        viewModelScope.launch { watchForStuck() }
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

    /** After every change to the game: clear the hint, and check in the background whether the player is stuck. */
    private suspend fun watchForStuck() {
        _state.map { it.game to it.busy }.distinctUntilChanged().collectLatest { (game, busy) ->
            _state.update { if (it.hint == null && !it.stuck) it else it.copy(hint = null, stuck = false) }
            if (game == null || game.isWon || busy) return@collectLatest
            delay(300) // let a burst of moves (auto-finish, autoplay) settle first
            val advice = withContext(Dispatchers.Default) { MoveAdvisor.advise(game.state, game.ruleSet) }
            if (advice is Advice.Stuck) markStuck(game)
        }
    }

    /** Shows the stuck banner, with a fresh quip. */
    private fun markStuck(game: Game) {
        val s = _state.value
        if (s.game !== game || s.busy || s.stuck) return
        val times = s.timesStuck + 1
        val facts = StuckFacts(
            s.ruleSet, s.tier, s.seed, s.moveCount, s.elapsedSeconds, s.usedUndo, s.hintsUsed, times,
            streak = _stats.value[s.ruleSet]?.currentStreak ?: 0, now = LocalDateTime.now(),
        )
        val line = Quips.stuck(facts, extras.lastLine("stuck"))
        extras.setLastLine("stuck", line)
        _state.value = s.copy(stuck = true, timesStuck = times, stuckLine = line)
    }

    /**
     * Shows a legal move that helps (or leads to one), without playing it. It isn't the best move, so it
     * doesn't solve the game. Every hint shown counts in the statistics.
     */
    fun hint() {
        val s = _state.value
        val game = s.game ?: return
        if (s.busy || s.won || s.hint != null || hintJob?.isActive == true) return
        hintJob = viewModelScope.launch {
            val advice = withContext(Dispatchers.Default) {
                MoveAdvisor.advise(game.state, game.ruleSet, maxStates = HINT_MAX_STATES)
            }
            val now = _state.value
            if (now.game !== game) return@launch
            when (advice) {
                is Advice.Hint -> {
                    if (!now.assisted) statsStore.recordHint(now.ruleSet, now.tier)
                    _state.value = now.copy(hint = advice.move, hintsUsed = now.hintsUsed + 1)
                    save()
                    refreshStats()
                    if (!now.assisted) checkAchievements(null)
                }
                Advice.Stuck -> markStuck(game)
                Advice.Unknown -> showNotice("No hint found. Try moving some cards around.")
                Advice.Won -> {}
            }
        }
    }

    private suspend fun restoreOrStart() {
        _stats.value = withContext(Dispatchers.IO) { statsStore.loadAll() }
        _unlocked.value = withContext(Dispatchers.IO) {
            extras.warmUp()
            extras.unlocked
        }
        val saved = withContext(Dispatchers.IO) { store.load() }
        if (saved != null) {
            val restored = withContext(Dispatchers.Default) {
                runCatching {
                    val deal = deals.find(saved.seed, saved.ruleSet, saved.tier) ?: return@runCatching null
                    deal to Game.replay(Deals.deal(deal.seed, deal.ruleSet, deal.shuffleVersion), deal.ruleSet, saved.moves)
                }.getOrNull()
            }
            if (restored != null) {
                val (deal, game) = restored
                solution = deal.solution
                _state.value = GameUiState(
                    game, deal.seed, deal.ruleSet, deal.tier, saved.elapsedSeconds, loading = false,
                    counted = saved.counted, usedUndo = saved.usedUndo, assisted = saved.assisted,
                    winRecorded = saved.winRecorded, hintsUsed = saved.hintsUsed, restarts = saved.restarts,
                )
                return
            }
        }
        val (ruleSet, tier) = withContext(Dispatchers.IO) { store.lastRuleSet to store.lastTier }
        startNewGame(ruleSet, tier)
    }

    fun newGame(ruleSet: RuleSet, tier: Tier) {
        val quip = abandonCurrent()
        store.lastRuleSet = ruleSet
        store.lastTier = tier
        viewModelScope.launch {
            startNewGame(ruleSet, tier)
            quip?.let { showNotice(it, QUIP_MILLIS) }
        }
    }

    /**
     * Plays the deal named by a deal code. It must be in this version's packs (so its proof can be replayed
     * first); otherwise the current game carries on and a notice says why.
     */
    fun playDeal(ruleSet: RuleSet, seed: Long) {
        viewModelScope.launch {
            val deal = withContext(Dispatchers.IO) { deals.findInAnyTier(seed, ruleSet) }
            if (deal == null) {
                showNotice("Deal ${DealCode.format(ruleSet, seed)} isn't in this version of the app")
                return@launch
            }
            val quip = abandonCurrent()
            store.lastRuleSet = ruleSet
            store.lastTier = deal.tier
            start(deal)
            quip?.let { showNotice(it, QUIP_MILLIS) }
        }
    }

    /**
     * Leaving a game that was played but not won counts as a loss (and ends the streak).
     * Returns a quip about it, to show once the next game is on screen.
     */
    private fun abandonCurrent(): String? {
        val s = _state.value
        scriptJob?.cancel()
        if (!s.counted || s.won || s.assisted) return null
        val lostStreak = statsStore.load(s.ruleSet).currentStreak
        statsStore.recordLoss(s.ruleSet)
        extras.recordAbandoned(s.ruleSet, lostStreak)
        refreshStats()
        val facts = AbandonFacts(
            s.ruleSet, s.tier, s.seed, s.moveCount, s.elapsedSeconds, lostStreak, extras.abandonedInARow, LocalDateTime.now(),
        )
        val line = Quips.abandoned(facts, extras.lastLine("abandoned"))
        extras.setLastLine("abandoned", line)
        return line
    }

    /** The greeting for the New game dialog. */
    fun greeting(): String {
        val s = _state.value
        val stats = _stats.value
        val now = LocalDateTime.now()
        return Greetings.pick(
            GreetingFacts(
                now,
                firstGameEver = stats.isNotEmpty() && stats.values.sumOf { it.played } == 0,
                daysSinceLastPlayed = extras.daysSinceLastPlayed(now.toLocalDate()),
                leavingUnfinished = s.counted && !s.won,
                streak = stats[store.lastRuleSet]?.currentStreak ?: 0,
            ),
        )
    }

    /** Pops up [a] without unlocking it (for the debug tools). */
    fun previewAchievement(a: Achievement) {
        _popups.update { it + a }
    }

    /** The popped-up achievement [a] has been shown. */
    fun achievementShown(a: Achievement) {
        _popups.update { it - a }
    }

    /** Unlocks the achievements earned so far (after a win, if [win] is set) and pops them up. */
    private fun checkAchievements(win: WinFacts?) {
        val all = statsStore.loadAll()
        val facts = AchievementFacts(
            wins = all.mapValues { (_, mode) -> mode.tiers.mapValues { it.value.won } },
            winsWithoutHints = all.values.sumOf { it.wonWithoutHints },
            totalHints = all.values.sumOf { it.hints },
            weekendWins = extras.weekendWins,
            dayStreak = extras.dayStreak,
            win = win,
        )
        val earned = Achievements.newlyEarned(facts, _unlocked.value)
        if (earned.isEmpty()) return
        extras.unlock(earned.map { it.id })
        _unlocked.update { it + earned.map { a -> a.id } }
        _popups.update { it + earned }
    }

    private suspend fun startNewGame(ruleSet: RuleSet, tier: Tier) {
        _state.update { it.copy(loading = true) }
        start(withContext(Dispatchers.IO) { deals.next(ruleSet, tier) })
    }

    private fun start(deal: ServedDeal) {
        solution = deal.solution
        val game = Game.start(Deals.deal(deal.seed, deal.ruleSet, deal.shuffleVersion), deal.ruleSet)
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
        _state.value = s.copy(game = takeOffCompleteRuns(game.play(move)))
        afterMove()
        save()
        maybeFinish()
        return true
    }

    /**
     * Spider and Scorpion: a complete King-to-Ace run must leave the board (the rules allow nothing else),
     * so it goes straight after the move that completed it.
     */
    private fun takeOffCompleteRuns(game: Game): Game {
        if (game.ruleSet.game == GameType.KLONDIKE) return game
        var g = game
        while (true) {
            val run = (0 until g.state.columns).firstOrNull { Rules.hasCompleteRun(g.state, it) } ?: return g
            g = g.play(Move.TableauToFoundation(run))
        }
    }

    fun undo() {
        val s = _state.value
        val game = s.game ?: return
        if (s.busy || !game.canUndo) return
        // In Spider and Scorpion, taking off a run belongs to the move before it: undo both together.
        var g = game
        if (g.ruleSet.game != GameType.KLONDIKE) {
            while (g.canUndo && g.moves.last() is Move.TableauToFoundation) g = g.undo()
        }
        if (g.canUndo) g = g.undo()
        _state.value = s.copy(game = g, usedUndo = true)
        save()
    }

    fun tapStock() {
        val game = _state.value.game ?: return
        val s = game.state
        if (game.ruleSet.game == GameType.KLONDIKE) {
            play(if (s.stockSize > 0) Move.Draw else Move.Recycle)
            return
        }
        if (s.stockSize == 0) return
        if (!Rules.canDeal(s, game.ruleSet)) {
            showNotice("Fill every empty column before dealing")
            return
        }
        play(Move.Draw)
    }

    private fun showNotice(text: String, millis: Long = 2500) {
        noticeJob?.cancel()
        _state.update { it.copy(notice = text) }
        noticeJob = viewModelScope.launch {
            delay(millis)
            _state.update { it.copy(notice = null) }
        }
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
        val columns = (0 until game.state.columns).map { Target.Column(it) }
        val all = if (game.ruleSet.game == GameType.KLONDIKE) listOf<Target>(Target.Foundation) + columns else columns
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
                    Target.Foundation ->
                        if (count == 1 && game.ruleSet.game == GameType.KLONDIKE) Move.TableauToFoundation(source.col) else null
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
        val ruleSet = _state.value.game?.ruleSet ?: return emptyList()
        if (ruleSet.game != GameType.KLONDIKE) return runGameCandidates(s, source)
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
     * Spider and Scorpion: the tapped card and everything on it go to a column, best first: on a card of the
     * same suit, then on any other card, then into an empty column (from left to right within each).
     */
    private fun runGameCandidates(s: GameState, source: Source): List<Move> {
        if (source !is Source.Column) return emptyList()
        val count = s.tabSize[source.col] - source.index
        val card = s.tab[source.col][source.index]
        val others = (0 until s.columns).filter { it != source.col }
        val inSuit = others.filter { s.tabSize[it] > 0 && Card.suit(s.columnTop(it)) == Card.suit(card) }
        val otherCards = others.filter { s.tabSize[it] > 0 && it !in inSuit }
        // Cards that already head their column gain nothing from moving to an empty one.
        val empty = if (source.index == 0) emptyList() else others.filter { s.tabSize[it] == 0 }
        return (inSuit + otherCards + empty).map { Move.TableauToTableau(source.col, count, it) }
    }

    /**
     * Column order for a tapped card: non-empty columns first (closest to the left), then empty ones.
     * A King run that already starts a column doesn't move to another empty column.
     */
    private fun columnPreference(s: GameState, source: Source): List<Int> {
        val nonEmpty = (0 until s.columns).filter { s.tabSize[it] > 0 }
        val kingAlreadyHome = source is Source.Column && source.index == 0
        val empty = if (kingAlreadyHome) emptyList() else (0 until s.columns).filter { s.tabSize[it] == 0 }
        return nonEmpty + empty
    }

    /** When everything is face up and the stock is empty, play the remaining cards up automatically. */
    private fun maybeFinish() {
        val game = _state.value.game ?: return
        if (game.ruleSet.game != GameType.KLONDIKE) return
        val s = game.state
        if (game.isWon || s.stockSize > 0 || s.wasteSize > 0) return
        if ((0 until s.columns).any { s.tabDown[it] > 0 }) return
        runScript { finishMoves() }
    }

    private fun finishMoves(): Move? {
        val game = _state.value.game ?: return null
        val s = game.state
        if (game.isWon) return null
        var best: Move? = null
        var bestRank = 99
        for (c in 0 until s.columns) {
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

    /** The Restart button: [restart], plus a quip about it. */
    fun restartByPlayer() {
        if (_state.value.game == null) return
        restart()
        val s = _state.value
        val restarts = s.restarts + 1
        _state.value = s.copy(restarts = restarts)
        save()
        val line = Quips.restart(RestartFacts(s.seed, restarts, LocalDateTime.now()), extras.lastLine("restart"))
        extras.setLastLine("restart", line)
        showNotice(line, QUIP_MILLIS)
    }

    /** Marks the current game as played by the computer (debug autoplay), so it never counts in the statistics. */
    fun markAssisted() {
        _state.update { it.copy(assisted = true) }
        save()
    }

    fun resetStats() {
        statsStore.reset()
        extras.reset()
        _unlocked.value = emptySet()
        _stats.value = RuleSet.ALL.associateWith { ModeStats() }
    }

    private fun refreshStats() {
        viewModelScope.launch { _stats.value = withContext(Dispatchers.IO) { statsStore.loadAll() } }
    }

    /** Updates the statistics after a move: played from the first move, and the win once. */
    private fun afterMove() {
        val s = _state.value
        if (s.assisted) {
            // Not counted, but the win screen still gets a fortune (handy for screenshots).
            if (s.won && s.fortune == null) {
                val facts = WinFacts(
                    s.ruleSet, s.tier, s.seed, s.elapsedSeconds, s.moveCount, solution.size, s.usedUndo, s.hintsUsed,
                    s.restarts, streak = 0, now = LocalDateTime.now(),
                )
                _state.value = s.copy(fortune = Fortunes.pick(facts), scorecards = DebugTools.forcedScorecards(s.seed))
            }
            return
        }
        var next = s
        var newDay = false
        if (!s.counted && s.moveCount > 0) {
            statsStore.recordPlayed(s.ruleSet, s.tier)
            extras.recordPlayed(LocalDate.now())
            newDay = true
            next = next.copy(counted = true)
        }
        var win: WinFacts? = null
        if (s.won && !s.winRecorded) {
            val before = statsStore.loadAll()
            val record = statsStore.recordWin(s.ruleSet, s.tier, s.elapsedSeconds, s.moveCount, s.usedUndo, s.hintsUsed > 0)
            val now = LocalDateTime.now()
            val lostStreak = extras.lostStreak(s.ruleSet)
            extras.recordWin(s.ruleSet, now)
            val mode = before.getValue(s.ruleSet)
            val winsBefore = before.values.sumOf { it.won }
            win = WinFacts(
                s.ruleSet, s.tier, s.seed, s.elapsedSeconds, s.moveCount, solution.size, s.usedUndo, s.hintsUsed, s.restarts,
                streak = record.streak,
                lostStreak = lostStreak,
                newBestTime = record.newBestTime,
                newFewestMoves = record.newFewestMoves,
                firstWinEver = winsBefore == 0,
                firstWinInRuleSet = mode.won == 0,
                firstWinInTier = (mode.tiers[s.tier]?.won ?: 0) == 0,
                totalWins = winsBefore + 1,
                totalPlayed = before.values.sumOf { it.played },
                now = now,
            )
            val fortune = Fortunes.pick(win, extras.lastLine("fortune"))
            extras.setLastLine("fortune", fortune)
            next = next.copy(
                winRecorded = true, winRecord = record, fortune = fortune,
                scorecards = DebugTools.forcedScorecards(s.seed) ?: Scorecards.scores(win),
            )
        }
        if (next !== s) {
            _state.value = next
            refreshStats()
        }
        if (newDay || win != null) checkAchievements(win)
    }

    private fun save() {
        val s = _state.value
        val game = s.game ?: return
        store.save(
            SavedGame(
                s.seed, s.ruleSet, s.tier, game.moves, s.elapsedSeconds,
                counted = s.counted, usedUndo = s.usedUndo, assisted = s.assisted, winRecorded = s.winRecorded,
                hintsUsed = s.hintsUsed, restarts = s.restarts,
            ),
        )
    }

    private companion object {
        /** Hints may search further than the stuck check, since the player is waiting for an answer. */
        const val HINT_MAX_STATES = 40_000

        /** Quips are longer than other notices, so they stay up a little longer. */
        const val QUIP_MILLIS = 4500L
    }
}
