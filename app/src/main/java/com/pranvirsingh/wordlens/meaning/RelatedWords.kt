package com.pranvirsingh.wordlens.meaning

/**
 * Pools similar/opposite word lists: first list first, never the word itself,
 * and no repeats — including spelling variants one letter apart ("loath" and
 * "loth"), which would only waste a chip.
 */
object RelatedWords {
    private const val MIN_VARIANT_LENGTH = 4

    fun merge(headword: String, limit: Int, vararg lists: List<String>): List<String> {
        val kept = mutableListOf<String>()
        for (word in lists.asSequence().flatten().map { it.trim() }) {
            if (kept.size == limit) break
            if (word.isEmpty() || word.equals(headword, ignoreCase = true)) continue
            val lower = word.lowercase()
            if (kept.any { it.lowercase() == lower || isSpellingVariant(it.lowercase(), lower) }) continue
            kept += word
        }
        return kept
    }

    /** True when the two words differ by exactly one inserted, removed or changed letter. */
    private fun isSpellingVariant(a: String, b: String): Boolean {
        if (a.length < MIN_VARIANT_LENGTH || b.length < MIN_VARIANT_LENGTH) return false
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
                continue
            }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> {
                    i++
                    j++
                }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
