package com.pranvirsingh.wordlens.text

import kotlin.math.hypot
import kotlin.math.max

/** An axis-aligned rectangle in screen (preview view) pixels. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float, padX: Float = 0f, padY: Float = 0f): Boolean =
        x >= left - padX && x <= right + padX && y >= top - padY && y <= bottom + padY

    /**
     * Distance from a point to the nearest edge of this box (0 when inside).
     * Vertical distance is weighted up, because landing on the neighbouring
     * line is a much worse guess than landing on the neighbouring word.
     */
    fun distanceTo(x: Float, y: Float, verticalWeight: Float = 1.5f): Float {
        val dx = max(max(left - x, 0f), x - right)
        val dy = max(max(top - y, 0f), y - bottom)
        return hypot(dx, dy * verticalWeight)
    }
}

/**
 * One recognised word, positioned within its block and line. [joinedBy] is
 * the character that originally joined it to the next word when the
 * recogniser saw them as one ("hope—despite"); null means a normal space.
 */
data class OcrWord(
    val raw: String,
    val box: Box,
    val blockIndex: Int,
    val lineIndex: Int,
    val wordIndex: Int,
    val joinedBy: Char? = null,
)

data class OcrLine(val words: List<OcrWord>)

data class OcrBlock(val lines: List<OcrLine>)

/** Everything the text recogniser found in a single camera frame. */
data class OcrFrame(val blocks: List<OcrBlock>) {
    val words: List<OcrWord> by lazy { blocks.flatMap { block -> block.lines.flatMap { it.words } } }

    fun lineOf(word: OcrWord): OcrLine? =
        blocks.getOrNull(word.blockIndex)?.lines?.getOrNull(word.lineIndex)

    companion object {
        val EMPTY = OcrFrame(emptyList())
    }
}
