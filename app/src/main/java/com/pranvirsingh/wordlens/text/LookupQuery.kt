package com.pranvirsingh.wordlens.text

/**
 * What to look up for one word on the page.
 *
 * [candidates] are tried in order until one is in the dictionary. A word
 * split across two lines ("won-" / "derful") is rejoined first, then tried
 * as a real hyphenated compound ("well-" / "known" → "well-known").
 */
data class LookupQuery(
    val display: String,
    val candidates: List<String>,
) {
    /** Stable identity of the word, used to tell "still the same word" between frames. */
    val key: String get() = candidates.first()
}

object LookupQueryBuilder {

    fun build(frame: OcrFrame, word: OcrWord): LookupQuery? {
        val line = frame.lineOf(word) ?: return single(word.raw)
        val lines = frame.blocks[word.blockIndex].lines

        if (word.wordIndex == line.words.lastIndex && WordNormalizer.endsWithHyphen(word.raw)) {
            val next = lines.getOrNull(word.lineIndex + 1)?.words?.firstOrNull()
            if (next != null) joined(word.raw, next.raw, aimed = word.raw)?.let { return it }
        }
        if (word.wordIndex == 0) {
            val previous = lines.getOrNull(word.lineIndex - 1)?.words?.lastOrNull()
            if (previous != null && WordNormalizer.endsWithHyphen(previous.raw)) {
                joined(previous.raw, word.raw, aimed = word.raw)?.let { return it }
            }
        }
        return single(word.raw)
    }

    /**
     * The two halves rejoined, then as a hyphenated compound, and finally the
     * aimed-at word on its own — the "hyphen" may really be a dash or an
     * OCR misread, and an ordinary word must still be found then.
     */
    private fun joined(first: String, second: String, aimed: String): LookupQuery? {
        val head = WordNormalizer.clean(first)
        val tail = WordNormalizer.clean(second)
        val candidates = listOfNotNull(
            WordNormalizer.normalize(head + tail),
            WordNormalizer.normalize("$head-$tail"),
            WordNormalizer.normalize(aimed),
        ).distinct()
        if (candidates.isEmpty()) return null
        return LookupQuery(display = displayOf(head + tail), candidates = candidates)
    }

    private fun single(raw: String): LookupQuery? {
        val key = WordNormalizer.normalize(raw) ?: return null
        val candidates = buildList {
            add(key)
            if ('-' in key) add(key.replace("-", ""))
        }
        return LookupQuery(display = displayOf(WordNormalizer.clean(raw)), candidates = candidates)
    }

    private fun displayOf(cleaned: String): String =
        if (cleaned.endsWith("'s", ignoreCase = true)) cleaned.dropLast(2) else cleaned
}
