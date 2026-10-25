package io.github.thomasvanriel.solitaire.extras

import io.github.thomasvanriel.solitaire.engine.difficulty.Tier
import io.github.thomasvanriel.solitaire.engine.model.GameType
import io.github.thomasvanriel.solitaire.engine.model.RuleSet
import java.time.LocalDateTime

/** The game at the moment the player got stuck. */
data class StuckFacts(
    val ruleSet: RuleSet,
    val tier: Tier,
    val seed: Long,
    val moves: Int,
    val seconds: Long,
    val usedUndo: Boolean,
    val hints: Int,
    /** Times the player has been stuck in this game, including now. */
    val timesStuck: Int,
    val streak: Int,
    val now: LocalDateTime,
)

/** A played game, left unwon for a new one (it counts as a loss). */
data class AbandonFacts(
    val ruleSet: RuleSet,
    val tier: Tier,
    val seed: Long,
    val moves: Int,
    val seconds: Long,
    /** The win streak this loss ended (0 if there was none). */
    val lostStreak: Int,
    /** Games left unwon in a row, including this one. */
    val abandonedInARow: Int,
    val now: LocalDateTime,
)

data class RestartFacts(
    val seed: Long,
    /** Times this deal has been restarted, including now. */
    val restarts: Int,
    val now: LocalDateTime,
)

/**
 * Sarcastic, slightly dark lines for getting stuck, giving up and restarting. The darkness is aimed at the
 * cards and the player's pride, never at real harm.
 */
object Quips {
    fun stuck(f: StuckFacts, previous: String? = null): String {
        val p = Picker(mix(f.seed, f.moves.toLong(), f.timesStuck.toLong(), 0x57L))
        if (f.timesStuck >= 2 && p.chance(0.4)) p.fresh(listOf("Stuck again. Familiar here, isn't it?"), previous)?.let { return it }
        val special = stuckSpecial(f)
        if (special.isNotEmpty() && p.chance(0.4)) p.fresh(special, previous)?.let { return it }
        if (Calendar.lateNight(f.now) && p.chance(0.4)) p.fresh(lateNight(f.now), previous)?.let { return it }
        if (p.chance(0.25)) p.fresh(STUCK_BY_GAME.getValue(f.ruleSet.game), previous)?.let { return it }
        return p.one(STUCK, previous)
    }

    internal fun stuckSpecial(f: StuckFacts): List<String> = buildList {
        if (f.tier == Tier.EXPERT) add("Expert said what it said on the tin.")
        if (f.tier == Tier.EASY) add("Stuck on Easy. We won't tell anyone.")
        if (!f.usedUndo) add("No undo so far. That ends now, I'm afraid.")
        if (f.hints > 0) add("You used ${f.hints} ${if (f.hints == 1) "hint" else "hints"} and still got here. Impressive, in a way.")
        if (f.seconds > 30 * 60) add("${formatTime(f.seconds)} of your life, and this is where it led.")
        if (f.moves < 20) add("Stuck after ${f.moves} moves. A personal best, of sorts.")
        if (f.streak >= 3) add("Your streak of ${f.streak} is looking nervous.")
        if (f.seconds in 1 until 120) add("Stuck in under two minutes. Efficient, in a way.")
        if (f.hints >= 5) add("${f.hints} hints in, and still stuck. The hints are embarrassed.")
    }

    fun abandoned(f: AbandonFacts, previous: String? = null): String {
        val p = Picker(mix(f.seed, f.moves.toLong(), f.abandonedInARow.toLong(), 0xABL))
        if (f.lostStreak >= 10) p.fresh(listOf("A moment of silence for your streak of ${f.lostStreak}."), previous)?.let { return it }
        if (f.lostStreak >= 2) p.fresh(listOf("Your streak of ${f.lostStreak} has died. It had a good life."), previous)?.let { return it }
        if (f.abandonedInARow >= 3 && p.chance(0.6)) {
            p.fresh(listOf("That's ${f.abandonedInARow} in a row. Is everything all right at home?"), previous)?.let { return it }
        }
        val special = buildList {
            if (f.moves < 10) add("You barely said hello.")
            if (f.moves >= 100) add("${f.moves} moves in, and you walk away now?")
            if (f.seconds > 30 * 60) add("${formatTime(f.seconds)} spent, nothing to show for it. Classic.")
            if (f.tier == Tier.EXPERT) add("Expert deals eat players like you for breakfast.")
            if (f.tier == Tier.EASY) add("It was Easy. It said so right there.")
            if (Calendar.lateNight(f.now)) add("Nothing good happens after midnight. Exhibit A: that game.")
        }
        if (special.isNotEmpty() && p.chance(0.5)) p.fresh(special, previous)?.let { return it }
        return p.one(ABANDONED, previous)
    }

    fun restart(f: RestartFacts, previous: String? = null): String {
        val p = Picker(mix(f.seed, f.restarts.toLong(), 0x5EL))
        val bored = "Restart number ${f.restarts}. The cards are getting bored."
        if (f.restarts >= 5) p.fresh(listOf("We've been here before, haven't we?", bored), previous)?.let { return it }
        if (f.restarts >= 3) p.fresh(listOf(bored), previous)?.let { return it }
        if (f.restarts == 2 && p.chance(0.5)) p.fresh(listOf("Third attempt. Third time's the charm?"), previous)?.let { return it }
        return p.one(RESTART, previous)
    }

    private fun lateNight(now: LocalDateTime): List<String> {
        val time = formatClock(now.hour, now.minute)
        return listOf(
            "Stuck at $time. Bold use of your sleep.",
            "It's $time. Even the cards want to go to bed.",
            "Nothing good happens after midnight. Exhibit A: this game.",
            "$time. The cards are asleep. You should be too.",
            "A dead end at $time. Sleep on it?",
        )
    }

    internal val STUCK = listOf(
        "No moves left. Just you, the cards, and the silence.",
        "It's winnable. Just not from here. Not by you. Not like this.",
        "The cards have stopped talking to you.",
        "This is the part of the film where the music goes quiet.",
        "You have played yourself into a corner. A very tidy corner.",
        "Somewhere, a few moves ago, you made a choice. It was wrong.",
        "The deal was winnable. Was.",
        "Every path from here leads nowhere. Undo is the only way out.",
        "The face-down cards are laughing at you. Quietly. Face down.",
        "Stuck. The solver would like a word.",
        "The solver won this deal in a few milliseconds. Just saying.",
        "Remember when you had options? Good times.",
        "Congratulations, you've found a way to lose a winnable deal.",
        "Plot twist: the problem was you.",
        "The cards are sorted into a perfect monument to your mistakes.",
        "You can't win from here, but you can stare at it for a while. Many do.",
        "This is fine. (It is not fine.)",
        "Your Kings have nowhere to go. They are kings of nothing.",
        "Hope is a face-down card, and you've run out of those.",
        "Abandon all hope, ye who moved the Seven there.",
        "Stuck. Undo exists for people like you. That's a compliment. Mostly.",
        "The deck has filed a complaint.",
        "You were so close. No, you weren't.",
        "Somewhere a computer is shaking its head.",
        "It's not a dead end. It's a cul-de-sac with no exit.",
        "In the end, we're all just cards waiting for a column.",
        "The good news: the deal is winnable. The bad news: see above.",
        "You've reached the end of the road. It was a short road.",
        "No more moves. Have you tried turning it off and on again? (That's Restart.)",
        "Somewhere, a fortune cookie is quietly taking back its prediction.",
        "The cards have unionised. They're on strike.",
        "Every card is exactly where you put it. That's the problem.",
        "This position will be studied by future generations. As a warning.",
        "Checkmate. Wrong game, same feeling.",
        "The game isn't over. It's just not going anywhere.",
        "Stuck. Have a biscuit and think about what you did.",
        "You and the cards have reached an impasse.",
        "A bold strategy. It did not pay off.",
        "If the cards could talk, they'd sigh.",
        "You have arrived at your destination. It's a wall.",
        "This is what experts call a learning opportunity.",
        "Even the Aces have stopped waiting.",
        "Out of moves. Not out of undos, though.",
        "That's a dead end. A very well-built one.",
        "A masterclass in how not to do it.",
        "The cards are as stuck as you are. Small comfort.",
        "Somewhere in the undo history is the moment it all went wrong.",
        "Your columns are full. Your options are not.",
        "The solver is too polite to say anything. We aren't.",
        "Stuck. Breathe in. Press Undo. Breathe out.",
        "Well, this is awkward.",
        "The cards would like to speak to the manager.",
        "You've painted yourself into a corner. Nice paint, though.",
        "Game over? Not quite. Game paused, indefinitely.",
    )

    internal val STUCK_BY_GAME = mapOf(
        GameType.KLONDIKE to listOf(
            "Seven columns, zero ideas.",
            "The stock has nothing more to give. Neither, it seems, do you.",
            "Turned every card, found no answers.",
            "The King you need is under the Queen you don't.",
        ),
        GameType.SPIDER to listOf(
            "The spider has caught something. It's you.",
            "Tangled in your own web.",
            "Ten columns and not one of them likes you.",
            "Eight legs and still no way forward.",
        ),
        GameType.SCORPION to listOf(
            "The scorpion stings. It's in its nature.",
            "You put a King in the wrong column, didn't you.",
            "Scorpion II takes no prisoners. You're the prisoner.",
            "Those three reserve cards were your last hope. They weren't enough.",
        ),
        GameType.YUKON to listOf(
            "Lost in the Yukon. Bring a coat.",
            "The gold rush is over. You found rocks.",
            "Snowed in. No moves till spring.",
        ),
        GameType.FREECELL to listOf(
            "Four free cells, and not one of them is free.",
            "All the cards are face up. So is the problem.",
            "Every card in plain sight, and no way through.",
            "FreeCell? More like StuckCell.",
        ),
    )

    internal val ABANDONED = listOf(
        "Walking away. The cards will remember this.",
        "That deal will haunt you. Not literally. Probably.",
        "Another deal goes into the drawer of things we don't talk about.",
        "Running away is also a strategy.",
        "The cards are going back in the box. In shame.",
        "Okay. We'll pretend that never happened. The stats won't.",
        "Rage quit detected. Your secret is safe. Except in the stats.",
        "Gone, but not forgotten. Mostly forgotten.",
        "A winnable deal, left to die alone.",
        "Somewhere out there, that deal is still waiting for you.",
        "You fold. Wrong card game, but okay.",
        "That deal will be spoken of in hushed tones.",
        "Fine. Be that way.",
        "The deal wasn't the problem. Just saying.",
        "Out with the old. The old didn't deserve you anyway.",
        "You'll never know how that one ended. Neither will we.",
        "Leaving so soon? The cards were just warming up.",
        "That deal has been left on the doorstep. Unloved.",
        "Goodbye, deal. It wasn't you. Actually, it was.",
        "A loss is a loss, even when you call it a fresh start.",
        "Some deals aren't meant to be. That one was, but okay.",
        "Another one bites the dust.",
        "You've left the building. The cards are still in it.",
        "Unfinished business. The worst kind.",
        "That deal will tell all its friends about you.",
        "Off to greener tableaux, then.",
        "The cards say goodbye. Coldly.",
        "Let's never speak of it again. The stats will, though.",
        "Next! The old deal wasn't listening anyway.",
        "Left on read. By you. Harsh.",
    )

    internal val RESTART = listOf(
        "Same cards, same you. Let's see how that goes.",
        "Again. Because insanity is doing the same thing and expecting different results.",
        "Back to the start. Do not pass Go.",
        "The cards remember what you did last time.",
        "Groundhog Day.",
        "Once more, with feeling.",
        "Restarting. The deck sighs.",
        "Different choices this time, yes?",
        "Take two. Action!",
        "From the top.",
        "Rewind complete. The cards look suspicious.",
        "Same deal, fresh hope.",
        "Let's pretend that last attempt was practice.",
        "Back where it all began. The cards haven't changed. Have you?",
        "The cards are back exactly as they were. Unlike your confidence.",
        "Do-over. Everyone gets one. Or several.",
        "Reset. Deep breath.",
        "Here we go again.",
        "Second time lucky? It isn't luck. It was always winnable.",
        "Starting over. The cards pretend not to recognise you.",
    )
}
