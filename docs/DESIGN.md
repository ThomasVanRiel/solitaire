# Design notes

How the engine, solver, difficulty rating, deal packs and app work. For features, building and the
`dealgen` options, see the [README](../README.md).

## The winnability guarantee

- **Winnable** means winnable with perfect play and full knowledge of the deal, face-down cards included.
  The player can't see those cards, so they can still get stuck and need to undo. Undo is unlimited, so a
  winnable deal stays winnable whatever the player does.
- **One rules engine.** `rules/Rules` is the only rules implementation. The solver, the difficulty
  players, `ProofVerifier` and the UI all use it. "Legal" is *defined* as "appears in
  `Rules.legalMoveCodes`".
- **Every served deal has a proof.** `ProofVerifier.verify(seed, ruleSet, moves)` deals the cards, checks
  each move with `Rules.isLegal`, plays it with `Rules.apply`, and requires `isWon` at the end. It is kept
  deliberately simple. Proofs are replayed when packs are written, again on read-back, in
  `ShippedPacksTest` for every shipped deal, and in the app (`DealRepository`) right before a deal is
  served. A deal whose proof fails is skipped.
- The solver may only err one way: it can miss winnable deals (we skip that seed), but it can't let an
  unwinnable one through, because the proof replay would fail.
- Solvability depends on the rule set, so each deal is verified for one `RuleSet` and the pack records it.

## Rules

In every game a card on a foundation is out of play: it never comes back to the tableau. (Klondike and Yukon
used to allow it; since version 0.6.1 no game does. `Move.FoundationToTableau` remains only so that old saved
move lists still decode; a saved game that used it no longer replays, and a new deal is served.)

### Klondike (`DRAW_1_UNLIMITED` id 1, `DRAW_3_UNLIMITED` id 2)
- 7 columns dealt 1..7, top card face up, 24 cards in the stock. Build down in alternating colours; any
  face-up run moves. Only a King (or a run starting with one) goes into an empty column.
- Draw-3 at the end of the stock draws whatever is left. A recycle turns the waste over, so the first card
  drawn comes first again. Recycling is unlimited; `RuleSet.custom(draw, maxRecycles)` supports a pass limit.
- Foundations are indexed by suit (C, D, H, S), not by slot; the UI places them as it likes.

### Spider (1, 2, 4 suits: ids 3, 4, 5)
- Two decks (1 suit = eight sets of spades; 2 suits = spades and hearts; 4 suits = two full decks),
  10 columns. 54 cards dealt (columns 1–4 get 6, the rest 5), top card face up; 50 in the stock (five deals).
- Build down regardless of suit; only a same-suit run moves as a unit; any card or run may go into an empty
  column. A deal puts one card face up on every column and is only allowed when no column is empty.

### Scorpion II (id 6)
- 7 columns of 7; columns 1–3 have their lowest 3 cards face down, everything else face up. Build down in
  suit. Any face-up card moves with everything on top of it. Only a King (with what's on it) goes into an
  empty column. The 3 reserve cards are dealt once, one onto each of columns 1–3 (also onto empty columns).

### Yukon (`YUKON` id 7) and Russian Solitaire (`RUSSIAN` id 8)
- One deck, 7 columns, no stock. Dealt like Klondike first (column c gets c+1 cards, top one face up), then
  four rows of face-up cards onto columns 2–7: column 1 has 1 card, column n has n−1 face-down cards under 5
  face-up ones. All 52 cards are dealt.
- Build down in alternating colours (Russian: in suit). Any face-up card moves together with everything on
  top of it, in order or not. Only a King (with what's on it) goes into an empty column. Foundations by suit,
  Ace up.
- Russian is hard: only about 1 seed in 15 is winnable (an exact breadth-first search over `Rules` agrees with
  the solver on the ones it calls unsolvable).

### FreeCell (`FREECELL` id 9)
- One deck dealt face up, row by row, into 8 columns (columns 1–4 get 7 cards, 5–8 get 6), plus 4 free
  cells (`GameState.cells`, -1 = empty) holding one card each.
- Build down in alternating colours; any card or run goes into an empty column. A run of n cards moves as
  one move when n ≤ (free cells + 1) × 2^(empty columns, not counting the target) (`Rules.maxRunMove`).
  Foundations by suit, Ace up.
- A card going into a cell takes the first empty one, so the move needs no cell index.

### Spider and Scorpion II
- A complete King-to-Ace run of one suit leaves the board. This is a **forced move in the rules**
  (`TableauToFoundation(col)`): while one shows, it is the only legal move, so the solver, proofs, verifier
  and UI agree. (Classic Scorpion leaves runs in place; taking them off only makes play easier.)
- Won when every card has left the board.

## Engine (`engine/`, pure Kotlin/JVM)

- `model/Card`: an Int `suit*13 + rank`. Suits C, D, H, S; D and H are red.
- `model/Move`: sealed type with a 17-bit `code`. The rules engine works on codes directly. Klondike code
  values are unchanged from the original 13-bit format. The FreeCell cell moves (`TableauToCell`,
  `CellToTableau`, `CellToFoundation`) share type 7 and keep their kind in the `c` field.
- `model/GameState`: int arrays (columns with a face-down count, stock, waste, foundation counts, recycle
  count, free cells), sized from the rule set (`GameState.forRules`). `found[suit]` counts cards. `StateFormat` is a text
  format for hand-built test positions.
- `model/RuleSet`: `game` (`GameType`: KLONDIKE, SPIDER, SCORPION, YUKON, FREECELL), `suits`, `buildInSuit`
  (Russian), `columns`, `deckSize`, `cells`, labels, and rule flags the UI and engine share (`hasStock`,
  `buildsFoundations`, `foundationToTableau`, `kingsOnlyInEmpty`).
- `rules/Rules`: `legalMoveCodes` plus in-place `applyCode`/`undoCode` with an int undo token; convenience
  `legalMoves`, `isLegal` and `apply`.
- `rules/Endgame`: when a game can finish itself (nothing face down, no stock or waste, every column in
  order: the lowest card left is then always free and fits), and the next move of that finish.
- `rules/Game`: immutable game (start, moves, intermediate states) with play, undo, restart and replay.
- `rules/StateValidator`: invariant checker (card multiset, face-down cards only under face-up ones,
  foundations in sequence, run order in Klondike).
- `shuffle/`: SplitMix64 seeding xoshiro256** and an unbiased Fisher–Yates shuffle, versioned as
  `Deals.SHUFFLE_V1` and pinned by `ShuffleGoldenTest`. Never change it; add a new version instead.
  Spider shuffles its 104 cards with the same RNG.

## Solvers (`engine/solver/`)

`DealSolver.create` picks the solver for a rule set. All three are depth-first searches over the shared `Rules`
code with in-place apply/undo and a 64-bit Zobrist transposition table.

**Restarts**: the first attempt gets 20k nodes and each further attempt doubles that, with ties between
equal-priority moves broken at random (DFS is heavy-tailed; this raised the draw-1 solve rate from 79.5% to
83.5% at 300k nodes). `Unsolvable` is reported only when an attempt exhausts the search within its budget.
Budgets: node limit, time limit and cancel callback. Outcomes: `Solved(moves)`, `Unsolvable`, `Unknown`.

### Klondike (`Solver`)
- Column hashes are summed, so column order doesn't matter. Stock/waste order is part of the key; the
  recycle count only for pass-limited rules.
- **Stock macro-moves**: one candidate per waste card that drawing can bring to the top and that can then be
  played, expanded into primitive `Draw`/`Recycle` moves in the solution. At most one recycle is simulated.
- **Safe auto-moves**: Aces and Twos, or a card whose opposite-colour foundations both hold rank−1.
- **Ordering**: foundation moves, moves that reveal a face-down card (more hidden first), moves that empty a
  column, waste plays, useful partial runs.
- **Pruning**: Kings only into the first empty column, and a run already at the bottom of a column never
  moves to another empty column. A partial run moves only if the uncovered card can go up, or something
  could then go on it.
  A position with no stock, waste or face-down cards is finished directly.

### Spider and Scorpion II (`RunGameSolver`)
- Complete runs come off straight away (forced). Ordering: build in suit, reveal a face-down card, empty a
  column, King to an empty column (Scorpion), other moves, deal last.
- Pruning (can lose solutions, never invents them): never split a same-suit run (Spider), never move a run
  from one off-suit card to another of the same rank, only uncover a face-up card if some run head could then
  go on it, never move a whole column into an empty one, and once the stock is empty only use the first empty
  column. Columns are hashed in order while the stock has cards, and as a sum after that.

### Yukon, Russian and FreeCell (`OpenGameSolver`)
- Candidates are exactly `Rules.legalMoveCodes`, ranked, with some left out; nothing is generated on the side.
- Safe auto-moves: Aces and Twos, a card whose opposite-colour foundations both hold rank−1, and in Russian
  every card (only the same-suit card one lower could ever rest on it, and that one is already up).
- Ordering: foundation moves, reveals (more hidden first), emptying a column, cell to column (FreeCell),
  moving a card that doesn't rest on its proper card onto one that it does, a King or run into an empty column,
  moves that uncover something useful, cell to an empty column, column to cell.
- Pruning (can lose solutions, never invents them): only the first empty column is used, a whole column never
  moves into an empty one, a card resting on its proper card only moves if what it uncovers can go up or take
  another card. Columns
  and free cells are hashed as sums, so their order doesn't matter.

### Benchmark

```sh
./gradlew :engine:test -Pbench --tests '*SolverBenchmark' -PbenchSeeds=1000 -PbenchNodes=1000000
./gradlew :engine:test -Pbench --tests '*SolverBenchmark' -PbenchRules=SPIDER_2_SUITS,SCORPION_II
```

Single thread, desktop JVM. Klondike: 1,000 seeds, 1M node budget. Others: 100 seeds (Yukon, Russian and
FreeCell: 200), 300k nodes.

| Rule set | Solved | Proved unsolvable | Unknown | Median time | Median solution |
|---|---|---|---|---|---|
| Draw 1 | 84.3% | 2.8% | 12.9% | 1.7 ms | 178 moves |
| Draw 3 | 77.1% | 12.4% | 10.5% | 3.9 ms | 151 moves |
| Spider 1 suit | 99% | 0% | 1% | 0.5 ms | 119 moves |
| Spider 2 suits | 94% | 0% | 6% | 11 ms | 159 moves |
| Spider 4 suits | 11% | 0% | 89% | 563 ms | 326 moves |
| Scorpion II | 24% | 43% | 33% | 318 ms | 55 moves |
| Yukon | 87% | 1% | 12% | 0.9 ms | 96 moves |
| Russian | 9.5% | 90.5% | 0% | 0.2 ms | 88 moves |
| FreeCell | 99.5% | 0% | 0.5% | 1.6 ms | 123 moves |

Klondike p95 is about 5 s: the `Unknown` deals using their whole budget. The Klondike solver runs at about
200k nodes/s. At 3M nodes, 4-suit Spider reaches about 30%.

## Difficulty (`engine/difficulty/`)

Difficulty is measured for a player who **can't** see hidden cards. Tests shuffle all hidden cards and check
that each player's choices don't change.

- `HeuristicPlayer` (Klondike) reads only face-up tableau cards, face-down counts, the waste top, the
  foundations and the stock size. Priorities: foundation moves that reveal a card, safe foundation moves,
  reveals (more hidden first), other foundation moves, partial runs that free a foundation card, King to an
  empty column, waste to tableau, emptying a column when a King is waiting, draw, recycle. Score noise
  between close options, and a random acceptable move 15% of the time. Gives up after two full stock passes
  without a play.
- `RunGamePlayer` (Spider, Scorpion II) never looks at face-down cards or the stock and never returns to a
  position it has seen in the same game. Scorpion: simple priorities (reveal, empty a column, build, King to
  an empty column, deal). Spider: one-move look-ahead on a visible score; the card a move would turn over is
  ignored, and it deals only when nothing improves the position.
- `OpenGamePlayer` (Yukon, Russian, FreeCell) never looks at face-down cards and never returns to a position
  it has seen in the same game. Priorities: safe foundation moves, reveals, other foundation moves, cell to
  column, emptying a column, moving a card that doesn't rest on its proper card onto one, a King (Yukon) or
  run (FreeCell) into an empty column, column to cell (better when it frees a card for the foundations).
- `DifficultyRater` runs 30 playouts seeded from the deal seed, so ratings are deterministic.
  - Klondike, Scorpion II, Yukon and Russian: wins out of 30.
  - Spider (the player almost never wins 2- or 4-suit games): the average over 30 games of 1 for a win,
    otherwise 0.8 × (half the share of face-down cards turned over + half the share of cards taken off),
    as a percentage. The pack's `wins` byte holds this percentage.
  - FreeCell (the player loses about 40% of deals outright, which people find easy): the same, with a lost
    game worth 0.8 × the share of cards it put on the foundations.

Tier cut-offs (`Difficulty.tierFor`), calibrated from the distribution over winnable deals:

| Rule set | Expert | Hard | Medium | Easy |
|---|---|---|---|---|
| Draw 1 (wins of 30) | 0 | 1–6 | 7–20 | 21–30 |
| Draw 3 (wins of 30) | 0 | 1–3 | 4–11 | 12–30 |
| Scorpion II (wins of 30) | 0 | 1–5 | 6–16 | 17–30 |
| Yukon (wins of 30) | 0 | 1–4 | 5–10 | 11–30 |
| Russian (wins of 30) | 0 | 1–6 | 7–17 | 18–30 |
| FreeCell (rating %) | ≤4 | 5–10 | 11–20 | ≥21 |
| Spider 1 suit (rating %) | ≤33 | 34–44 | 45–51 | ≥52 |
| Spider 2 suits (rating %) | ≤14 | 15–17 | 18–20 | ≥21 |
| Spider 4 suits (rating %) | ≤11 | 12–13 | 14–15 | ≥16 |

## Hints and the stuck check (`engine/hint/MoveAdvisor`)

- A move **helps** when it can't be taken back: it turns over a face-down card, plays a card from the stock
  or waste (Klondike), deals (Spider, Scorpion II) or takes off a complete run. Every other move only
  rearranges face-up cards or cycles the Klondike stock, and there are finitely many such positions.
- `MoveAdvisor.advise` searches those positions breadth first (recycle count ignored with unlimited passes)
  for one where a helpful move is legal. Results: `Hint(move)` (a helpful move, or the first step towards
  one), `Stuck` (none reachable: the position can't be won), `Unknown` (over the state budget) or `Won`.
- Yukon and FreeCell have no stock, so a card going up to a foundation also helps (a win needs every card
  to go up, so "stuck" stays correct). Progress is always measured against the player's position, not the
  previous step of the search, so losing ground and winning it back never counts. (When Yukon still let
  foundation cards come down, the hint once suggested taking a card down only to put it back up.) In FreeCell nothing is hidden and almost every position can be
  rearranged a lot, so the stuck check often runs out of states and says nothing.
- Helpful moves at the current position are preferred in this order: a run comes off, a card turns over, a
  card leaves the stock/waste, a deal. It never searches for the best move, so a hint doesn't solve the game.
- It only applies moves from `Rules`, and never plays a Spider/Scorpion deal (it only notes that one is
  legal), so it sees nothing the player couldn't see by cycling the Klondike stock.
- The app runs it in the background 300 ms after every change to the game (5,000 states) and shows a
  banner when the result is `Stuck`. The Hint button allows 40,000 states. On a desktop JVM a check takes
  under 1 ms on average for Klondike and up to about 170 ms for Spider. `MoveAdvisorTest` replays stored
  winning lines and checks that no position on them is ever called stuck.

## Deal packs (`engine/pack/`)

- `DealPack`: gzipped binary. Header: magic `WPAK`, format version 1, shuffle version, rule set id, tier id,
  playouts, count. Entries: seed, wins (rating), solver nodes, solution. `verifiedSolution(entry)` decodes and
  replays through `ProofVerifier`.
- `SolutionCodec`: each move is its index in `Rules.legalMoveCodes`, one byte; an index of 255 or more
  (Spider only) is 255 plus two bytes. Decoding can only yield legal moves, and the result is still replayed.
- `DealCode`: a two-letter variant prefix and the deal number (`K1`, `K3`, `S1`, `S2`, `S4`, `SC`, `YU`,
  `RU`, `FC`, e.g. `S2-17`). Case, spaces, `#` and the dash are ignored.
- `dealgen` processes seeds in ordered parallel batches, so output is deterministic.
- Shipped: 2,500 deals per tier per rule set (90,000 deals), about 7.9 MB in total. Generating them took
  50 minutes on 27 worker threads (i7-14700K); 4-suit Spider and Scorpion II take most of it, because few
  of their deals are solved. The README has the times per game.
- Changing the rules or the order of the legal-move list invalidates the packs; regenerate them.

## App (`app/`)

- `GameViewModel` exposes `StateFlow<GameUiState>`; moves go through the engine's `Game`/`Rules`. There is
  no solver on the device.
- `DealRepository` serves pack deals in order per (rule set, tier), cycling when a pack runs out, and replays
  each proof before serving (`next` is synchronized, so two requests never get the same deal). `findInAnyTier`
  looks up a deal code. The view model keeps one deal-loading job; a newer request cancels the older one, so
  a slow load can't replace a newer game.
- `SavedGameStore` (SharedPreferences) saves seed, rule set, tier, moves (3 bytes each, key `moves3`; the old
  2-byte `moves` key still loads), time and the stats flags after every move. The game is rebuilt by replay.
  Preferences open lazily, first accessed on `Dispatchers.IO`.
- `StatsStore`: per variant and tier. The Statistics screen also has an "All games" chip: totals over every
  game and difficulty (`combinedStats`), the best streak of any one game, and a played/won table per game.
  Fastest time and fewest moves aren't shown there, since they can't be compared across games, and nor are
  the averages. Each win adds its time, moves and failed moves to running totals per tier, with a count of
  the wins totalled (`totalledWins`); the averages divide by that count, so wins from before 0.6.2, which
  have no totals, don't pull them down. The screen shows them for the game and per tier ("Wins by
  difficulty"). Undos, player Restarts and times stuck (each time the stuck banner comes up) are counted per
  tier as they happen; the game also keeps its own undo count for the "undos per win" average. Time played
  adds a game's clock when it is won, abandoned or restarted (Restart zeroes the clock), and never for a game
  whose win is already recorded, so no second counts twice; the game in progress isn't in it yet. Days played,
  the current run of days (0 once a day is skipped) and the longest run come from `ExtrasStore`, over all
  games; before 0.6.2 only the run was kept, so the other two start from it. Hints shown are counted, and so are wins that used a hint ("won
  without hints" is won minus those, so wins from before hints existed count as without). A game counts
  from its first move. New game before winning is a loss
  and ends the streak, counted once even if New game is pressed again before the next deal shows. Restart
  is the same game but counts as using undo. There is no undo after a win, so a recorded win can't turn
  into a loss. Reset statistics also resets the game on the table: it counts as played again from its next
  move and forgets hints shown before. Debug autoplay never counts.
- Board (`ui/BoardLayout`, `ui/Board`, `ui/CardPainter`, `ui/SuitShapes`): one Compose Canvas; suits are
  vector shapes. Portrait: foundations left, stock far right with the waste beside it. Landscape: foundations
  down the left edge, stock down the right. Spider and Scorpion II leave a small gap where a same-suit
  sequence breaks (12% and 7% of a card height).
- Tap sends a card to the best place (Klondike and Yukon: non-empty columns, then the foundation, then an
  empty column; FreeCell: non-empty columns, foundation, a free cell, an empty column; a card that fits on
  the board stays in play rather than going up; Spider/Scorpion:
  same-suit card, any card, empty column). Drag highlights legal targets; dropping on any free cell uses the
  first empty one. A FreeCell run that fits but is too long for the free room gets a notice saying how many
  cards can move.
- Tapping the card a tap just moved (while that is still the last move) takes the move back and plays the
  tapped card's next legal place in the same order, going round, so the move count doesn't grow. It stops
  once the card is on a foundation (nothing there can be tapped). The board animates such a swapped last
  move from where the card was shown.
- Left-handed (*Stock on the left* in New game, stored in the `game` preferences): `BoardLayout` mirrors
  everything but the columns, which keep their order; foundations and free cells still run left to right,
  and draw-3's waste still fans to the left so ranks show.
- Portrait FreeCell has its free cells at the top left and the foundations at the top right; in landscape
  the cells are a 2×2 block on the left and the foundations one on the right. Yukon has no stock slot.
- The game finishes itself when `Endgame.canFinish` says so (Klondike, Yukon, FreeCell).
- Top bar: New game and Restart as text buttons; Undo, Hint and Statistics as small icon buttons drawn on a
  Canvas (no icon library), each with a content description. The second row has the deal (tap to share)
  and the move count, failed moves (only once there is one) and clock. A failed move is a tap on a card
  with no legal move, a drop that the rules refuse, or a Spider deal refused for an empty column; dropping a
  card back on its own column or on a place that can't take a move at all isn't one. The count is per game,
  saved with it, kept across Restart like hints, and shown on the win screen; it isn't in the statistics. The win screen dims the board only: New game, Restart and Statistics stay
  usable, while Undo and Hint are off once the game is won.
- `ui/MoveAnimation`: 300 ms per move with a gentle ease-in, long ease-out curve (170 ms while the game
  plays itself; auto-finish pauses 200 ms between moves). Undo, Restart and New game are instant.
  `Board` works out a move's animation steps in the same composition that brings the new game, and draws the
  first step at its start until the animation runs, so no frame shows the cards at their target (or a dropped
  card back where the drag began). The step and its progress are plain state set together, not an
  `Animatable` (whose `snapTo` suspends between them).
- `ui/Theme`: the calm look. Muted sage felt, linen card faces with terracotta and charcoal pips, a dusty
  blue back, a faint shadow under each card, and a warm wood-and-cream Material colour scheme instead of
  the bright defaults. The win screen, notices, the stuck banner and achievement pop-ups fade in and out
  over `SOFT_FADE_MS` (600 ms) rather than popping.
- `ui/FeltSurprise`: a faint swirl on the felt on about 1 deal in 25, chosen by a hash of the deal number.
- The fun bits (`extras/`, pure Kotlin, tested by `:extras:test`): `Fortunes` (win line), `Greetings`
  (New game), `Quips` (stuck banner, leaving a game unwon, Restart), `Achievements` and `Scorecards`.
  Notable moments (first wins, milestones, streaks, holidays) always get their line; otherwise a line about the
  game (records included) sometimes, else a random one seeded by the deal number. A line is never the same as the last one of
  its kind. Greetings go: first game, holiday, a long absence, then sometimes a line for coming back, an
  unfinished game, a streak or the day of the week, else one for the time of day; the pick is the same
  within an hour. `ExtrasStore` ("extras" preferences) keeps unlocked achievement ids, the counters the stats
  don't have (weekend wins, days played in a row, games left unwon in a row, the streak a loss ended) and
  the last line of each kind. Achievements are checked after a win, a hint and a game's first move; new ones
  queue as pop-ups with no touch handling, so taps go through. Reset statistics also clears them.
  Restarts per deal are saved with the game.
- Debug-only tools live in `app/src/debug/.../DebugTools.kt` (StrictMode, autoplay, screenshot mode,
  forced scorecards); the
  `release` source set has an empty `DebugTools`.

## Known issues

- 4-suit Spider packs only hold deals the solver cracks within 300k nodes (about 15%), so they lean towards
  ones it finds easier.
- The Spider difficulty player rarely wins, so Spider tiers rest on progress rather than wins.
- In landscape, a Spider column with 15 or more face-up cards is tight.
- After debug autoplay the win screen time can read 0:00.
- An autoplay intent sent while the app is open is dropped by Android; use `am start -S`.
