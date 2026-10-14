package com.pranvirsingh.wordlens.text

/** Recovers the sentence a word appears in, from the text recognised around it. */
object SentenceExtractor {

    const val MAX_LENGTH = 280

    private val terminators = charArrayOf('.', '!', '?', '…')
    private val closingQuotes = charArrayOf('"', '\'', '”', '’', ')', '»')

    fun sentenceAround(frame: OcrFrame, word: OcrWord): String? {
        val block = frame.blocks.getOrNull(word.blockIndex) ?: return null

        // Rebuild the paragraph, rejoining words hyphenated across lines.
        val text = StringBuilder()
        var wordStart = -1
        var wordEnd = -1
        var glue: String? = null
        block.lines.forEachIndexed { lineIndex, line ->
            line.words.forEachIndexed { wordIndex, w ->
                val piece = w.raw.trim()
                if (piece.isEmpty()) return@forEachIndexed
                if (text.isNotEmpty()) text.append(glue ?: " ")
                val isTarget = lineIndex == word.lineIndex && wordIndex == word.wordIndex
                if (isTarget) wordStart = text.length
                val brokenAtLineEnd = wordIndex == line.words.lastIndex &&
                    lineIndex < block.lines.lastIndex &&
                    WordNormalizer.endsWithHyphen(piece)
                text.append(if (brokenAtLineEnd) piece.dropLast(1) else piece)
                if (isTarget) wordEnd = text.length
                glue = when {
                    brokenAtLineEnd -> ""
                    w.joinedBy != null -> w.joinedBy.toString()
                    else -> null
                }
            }
        }
        if (wordStart < 0) return null
        val paragraph = text.toString()

        var start = 0
        for (i in wordStart - 1 downTo 0) {
            if (isSentenceEnd(paragraph, i)) {
                start = i + 1
                break
            }
        }
        var end = paragraph.length
        for (i in wordStart until paragraph.length) {
            if (isSentenceEnd(paragraph, i)) {
                end = i + 1
                while (end < paragraph.length && paragraph[end] in closingQuotes) end++
                break
            }
        }
        while (start < wordStart && (paragraph[start].isWhitespace() || paragraph[start] in closingQuotes)) start++

        val sentence = paragraph.substring(start, end).trim()
        if (sentence.isEmpty()) return null
        if (sentence.length <= MAX_LENGTH) return sentence
        return window(sentence, (wordStart - start).coerceAtLeast(0), (wordEnd - start).coerceAtMost(sentence.length))
    }

    /** A terminator followed by whitespace, a closing quote, or the end of the text. */
    private fun isSentenceEnd(text: String, i: Int): Boolean {
        if (text[i] !in terminators) return false
        val next = text.getOrNull(i + 1) ?: return true
        return next.isWhitespace() || next in closingQuotes
    }

    /** Cuts an over-long sentence down to roughly [MAX_LENGTH] characters centred on the word. */
    private fun window(sentence: String, wordStart: Int, wordEnd: Int): String {
        val half = (MAX_LENGTH - (wordEnd - wordStart)) / 2
        var from = (wordStart - half).coerceAtLeast(0)
        var to = (wordEnd + half).coerceAtMost(sentence.length)
        if (from > 0) from = sentence.indexOf(' ', from).let { if (it in 0 until wordStart) it + 1 else from }
        if (to < sentence.length) to = sentence.lastIndexOf(' ', to).let { if (it >= wordEnd) it else to }
        val prefix = if (from > 0) "…" else ""
        val suffix = if (to < sentence.length) "…" else ""
        return prefix + sentence.substring(from, to).trim() + suffix
    }
}
