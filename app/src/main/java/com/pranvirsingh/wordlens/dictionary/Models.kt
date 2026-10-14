package com.pranvirsingh.wordlens.dictionary

enum class PartOfSpeech(val code: String) {
    NOUN("n"),
    VERB("v"),
    ADJECTIVE("a"),
    ADVERB("r");

    companion object {
        fun fromCode(code: String): PartOfSpeech? = entries.firstOrNull { it.code == code }
    }
}

/** One stored meaning of a lemma, exactly as the dictionary file holds it. */
data class SenseRecord(
    val pos: PartOfSpeech,
    val rank: Int,
    val tagCount: Int,
    val definition: String,
    val examples: List<String>,
    val lemmas: List<String>,
    val antonyms: List<String> = emptyList(),
)

/** One meaning, ready to show. */
data class Sense(
    val definition: String,
    val examples: List<String>,
    val synonyms: List<String>,
    val antonyms: List<String> = emptyList(),
)

/** All meanings of one word used as one part of speech, e.g. "run" as a verb. */
data class Entry(
    val lemma: String,
    val pos: PartOfSpeech,
    val senses: List<Sense>,
)

/** The answer to "what does this word mean?". Entries are most-likely-first. */
data class LookupResult(
    val query: String,
    val entries: List<Entry>,
)
