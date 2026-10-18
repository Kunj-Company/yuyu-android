package com.twilighty.yuyu.core.designsystem.brand

/**
 * Yuyu dot sign patterns, ported verbatim from `components/brand/patterns.ts`
 * (Figma "Brand — Dot sign", file oVuNh4VRXQ9fIpdnUCoZqt, page 111:292).
 * Rows are separated by `|`:
 *
 * `X` marigold (lit) · `O` ink base dot · `.` soft (unlit) · `D` deep marigold ·
 * `W` white · `m` soft dot on marigold · space = no dot.
 */
object DotPatterns {
    /** The mark: a Y on a 5×5 grid; six marigold dots and one ink base dot, the host. */
    const val MARK = "X...X|.X.X.|..X..|..X..|..O.."
    /** Under 48dp: bright dots only. */
    const val MARK_BRIGHT = "X   X| X X |  X  |  X  |  O  "
    /** On marigold backgrounds: an ink Y, soft dots in a deeper marigold, a white host. */
    const val MARK_ON_MARIGOLD = "OmmmO|mOmOm|mmOmm|mmOmm|mmWmm"

    /** "YUYU" in dots, for posters and stage screens (not UI headers). */
    val DOT_WORDMARK = listOf(
        "X   X O   O X   X O   O",
        " X X  O   O  X X  O   O",
        "  X   O   O   X   O   O",
        "  X   O   O   X   O   O",
        "  X    OOO    X    OOO ",
    ).joinToString("|")

    /** Split a pattern into equal-width rows of cells. */
    fun parse(pattern: String): List<List<Char>> {
        val rows = pattern.split("|")
        val width = rows.maxOf { it.length }
        return rows.map { it.padEnd(width, ' ').toList() }
    }
}

/**
 * Dot faces on a 7×7 grid. Meanings are fixed (DESIGN §3): Sleepy = empty,
 * Cheering = "You're in", Wink = checked in, Sad = errors, Waiting = waitlist,
 * Thinking = loading; also Happy (hello), Love (feedback), Done (all set),
 * Surprised (event full).
 */
enum class DotFaceMood(val pattern: String, val blinks: Boolean) {
    Happy(" XXXXX |XXXXXXX|XOXXXOX|XXXXXXX|XOXXXOX|XXOOOXX| XXXXX ", true),
    Cheering(" XXXXX |XOXXXOX|OXOXOXO|XXXXXXX|XOOOOOX|XXOOOXX| XXXXX ", true),
    Wink(" XXXXX |XXXXXXX|XOXXOOX|XXXXXXX|XOXXXOX|XXOOOXX| XXXXX ", true),
    Love(" XX XX |XXXXXXX|XXXXXXX|XXXXXXX| XXXXX |  XXX  |   X   ", false),
    Done(" XXXXX |XXXXXXX|XXXXXOX|XXXXOXX|XOXOXXX|XXOXXXX| XXXXX ", false),
    Waiting(" XXXXX |XXXOXXX|XXXOXXX|XXXOOXX|XXXXXXX|XXXXXXX| XXXXX ", false),
    Surprised(" XXXXX |XXXXXXX|XOXXXOX|XXXXXXX|XXXOXXX|XXOXOXX| XXOXX ", true),
    Thinking(" XXXXX |XXOXXOX|XXXXXXX|XXXXXXX|XXXOOOX|XXXXXXX| XXXXX ", true),
    Sleepy(" XXXXX |XXXXXXX|XOOXOOX|XXXXXXX|XXXXXXX|XXXOXXX| XXXXX ", true),
    Sad(" XXXXX |XXXXXXX|XOXXXOX|XXXXXXX|XXOOOXX|XOXXXOX| XXXXX ", true),
}
