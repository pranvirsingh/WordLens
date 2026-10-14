package com.pranvirsingh.wordlens.meaning

/** Where the explanation on the card came from (shown as a small credit line). */
enum class MeaningSource {
    /** The built-in WordNet dictionary — works with no internet. */
    OFFLINE,

    /** Simple English Wiktionary — written in plain, everyday English. */
    SIMPLE_WIKTIONARY,

    /** The full English Wiktionary — used when Simple English has no entry. */
    WIKTIONARY,
}

/** One meaning, with a few everyday example sentences. */
data class MeaningSense(
    val definition: String,
    val examples: List<String> = emptyList(),
    /** A topic or usage note such as "baseball" or "informal", if any. */
    val label: String? = null,
)

/** All meanings of the word as one part of speech, e.g. as a verb. */
data class MeaningGroup(
    val partOfSpeech: String,
    val senses: List<MeaningSense>,
    /**
     * Set when these meanings belong to a different dictionary word than the
     * headword — e.g. "saw" the tool, on a card headed "see".
     */
    val otherWord: String? = null,
)

/** Everything the meaning card shows for one word, whatever its source. */
data class WordMeaning(
    val headword: String,
    val pronunciation: String?,
    val groups: List<MeaningGroup>,
    val similar: List<String>,
    val opposite: List<String>,
    val source: MeaningSource,
)
