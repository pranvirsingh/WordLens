package com.pranvirsingh.wordlens.text

/**
 * Lays text out like a printed page for tests: every character is 10px wide,
 * a line is 20px tall, lines are 30px apart, words are one space apart.
 * Each string is one block (paragraph); '\n' separates its lines.
 */
fun page(vararg blocks: String): OcrFrame {
    var y = 0f
    return OcrFrame(
        blocks.mapIndexed { blockIndex, block ->
            OcrBlock(
                block.split('\n').mapIndexed { lineIndex, line ->
                    var x = 0f
                    val words = line.split(' ').filter { it.isNotEmpty() }.mapIndexed { wordIndex, raw ->
                        val box = Box(x, y, x + raw.length * CHAR_WIDTH, y + LINE_HEIGHT)
                        x += (raw.length + 1) * CHAR_WIDTH
                        OcrWord(raw, box, blockIndex, lineIndex, wordIndex)
                    }
                    y += LINE_GAP
                    OcrLine(words)
                },
            ).also { y += LINE_GAP }
        },
    )
}

fun OcrFrame.word(raw: String): OcrWord = words.first { it.raw == raw }

const val CHAR_WIDTH = 10f
const val LINE_HEIGHT = 20f
const val LINE_GAP = 30f
