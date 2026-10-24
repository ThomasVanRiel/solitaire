package io.github.thomasvanriel.solitaire.engine.model

/**
 * A card is a plain Int from 0 to 51: `suit * 13 + rank`.
 * Rank 0 is the Ace and rank 12 the King. Suits are Clubs, Diamonds, Hearts, Spades (0..3).
 * Diamonds and Hearts are red.
 */
object Card {
    const val CLUBS = 0
    const val DIAMONDS = 1
    const val HEARTS = 2
    const val SPADES = 3

    const val ACE = 0
    const val KING = 12

    private const val RANK_CHARS = "A23456789TJQK"
    private const val SUIT_CHARS = "CDHS"
    private val SUIT_SYMBOLS = arrayOf("♣", "♦", "♥", "♠")
    private val RANK_NAMES = arrayOf(
        "Ace", "Two", "Three", "Four", "Five", "Six", "Seven",
        "Eight", "Nine", "Ten", "Jack", "Queen", "King",
    )
    private val SUIT_NAMES = arrayOf("clubs", "diamonds", "hearts", "spades")

    fun of(suit: Int, rank: Int): Int = suit * 13 + rank
    fun rank(card: Int): Int = card % 13
    fun suit(card: Int): Int = card / 13

    /** 0 for black, 1 for red. */
    fun color(card: Int): Int = ((card / 13 + 1) shr 1) and 1
    fun isRed(card: Int): Boolean = color(card) == 1

    /** True if [card] can be placed on [onto] in the tableau (one rank lower, opposite colour). */
    fun stacksOn(card: Int, onto: Int): Boolean =
        rank(card) + 1 == rank(onto) && color(card) != color(onto)

    /** Short code such as "TH" (ten of hearts) or "AS". */
    fun code(card: Int): String = "${RANK_CHARS[rank(card)]}${SUIT_CHARS[suit(card)]}"

    /** Display form such as "10♥". */
    fun display(card: Int): String {
        val r = rank(card)
        val rs = if (r == 9) "10" else RANK_CHARS[r].toString()
        return rs + SUIT_SYMBOLS[suit(card)]
    }

    /** Spoken form such as "Seven of hearts". */
    fun spoken(card: Int): String = "${RANK_NAMES[rank(card)]} of ${SUIT_NAMES[suit(card)]}"

    fun parse(code: String): Int {
        require(code.length == 2) { "Bad card code '$code'" }
        val r = RANK_CHARS.indexOf(code[0].uppercaseChar())
        val s = SUIT_CHARS.indexOf(code[1].uppercaseChar())
        require(r >= 0 && s >= 0) { "Bad card code '$code'" }
        return of(s, r)
    }
}
