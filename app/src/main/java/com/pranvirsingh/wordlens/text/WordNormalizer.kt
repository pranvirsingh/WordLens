package com.pranvirsingh.wordlens.text

import java.util.Locale

/** Turns a word as printed on the page into a clean dictionary lookup key. */
object WordNormalizer {

    private const val MIN_LENGTH = 2

    // "'s" here is "is"/"has"/"us", not a possessive — looking up the
    // stripped word ("it") would show a meaningless definition.
    private val contractions = setOf(
        "it's", "that's", "there's", "here's", "what's", "who's", "where's",
        "when's", "why's", "how's", "he's", "she's", "let's",
    )

    private val apostrophes = charArrayOf('’', '‘', 'ʼ', '`', '´')
    private val hyphens = charArrayOf('‐', '‑', '‒', '­')

    /**
     * Lookup key for a word or short phrase chosen on the card ("ice cream",
     * "look after") rather than read off the page, so spaces are allowed.
     */
    fun normalizePhrase(text: String): String? {
        val s = unify(text).trim().lowercase(Locale.ROOT).replace(whitespace, " ")
        if (s.length < MIN_LENGTH || s.none { it.isLetter() }) return null
        if (!s.all { it.isLetter() || it == ' ' || it == '\'' || it == '-' }) return null
        return s
    }

    private val whitespace = Regex("\\s+")

    /** Unifies look-alike apostrophes and hyphens, keeping the original case. */
    fun clean(raw: String): String = unify(raw).trim { !it.isLetter() }

    /** True when [raw] ends in a hyphen, i.e. it may be a word broken across two lines. */
    fun endsWithHyphen(raw: String): Boolean =
        unify(raw).replace('–', '-').trimEnd()
            .let { it.endsWith('-') && it.dropLast(1).lastOrNull()?.isLetter() == true }

    private fun unify(raw: String): String {
        var s = raw
        for (c in apostrophes) s = s.replace(c, '\'')
        for (c in hyphens) s = s.replace(c, '-')
        return s
    }

    /**
     * Returns the lowercase lookup key for [raw], or null when it isn't a word
     * worth looking up (numbers, stray symbols, single letters).
     */
    fun normalize(raw: String): String? {
        // clean() already drops the trailing apostrophe of a plural possessive ("dogs'").
        var s = clean(raw)
        if (s.lowercase(Locale.ROOT) in contractions) return null
        if (s.endsWith("'s", ignoreCase = true)) s = s.dropLast(2)
        s = s.trim { !it.isLetter() }.lowercase(Locale.ROOT)
        if (s.length < MIN_LENGTH) return null
        if (!s.all { it.isLetter() || it == '\'' || it == '-' }) return null
        return s
    }
}
