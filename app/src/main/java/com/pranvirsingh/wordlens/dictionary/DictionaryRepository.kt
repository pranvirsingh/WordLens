package com.pranvirsingh.wordlens.dictionary

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Looks words up in the offline dictionary. */
class DictionaryRepository(
    private val openLexicon: suspend () -> Lexicon,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Tries each spelling in [candidates] in order and returns the meanings
     * for the first one the dictionary knows, or null if it knows none.
     * Readings of the word are ordered most-common-first, so "saw" leads with
     * the past tense of "see" rather than the tool.
     */
    suspend fun lookup(candidates: List<String>): LookupResult? = withContext(io) {
        val lexicon = CachingLexicon(openLexicon())
        val lemmatizer = Lemmatizer(lexicon)
        for (form in candidates) {
            val ranked = lemmatizer.baseForms(form)
                .mapNotNull { rankedEntry(lexicon, it) }
                .sortedByDescending { it.usage }
            if (ranked.isNotEmpty()) return@withContext LookupResult(form, ranked.map { it.entry })
        }
        null
    }

    private class RankedEntry(val entry: Entry, val usage: Int)

    private fun rankedEntry(lexicon: Lexicon, candidate: Lemmatizer.Candidate): RankedEntry? {
        val records = lexicon.senses(candidate.lemma)
            .filter { it.pos == candidate.pos && it.definition.isNotBlank() }
            .sortedBy { it.rank }
        if (records.isEmpty()) return null
        val senses = records.map { record ->
            Sense(
                definition = record.definition,
                examples = record.examples,
                synonyms = record.lemmas
                    .filterNot { it.equals(candidate.lemma, ignoreCase = true) }
                    .take(MAX_SYNONYMS),
            )
        }
        return RankedEntry(Entry(candidate.lemma, candidate.pos, senses), records.sumOf { it.tagCount })
    }

    private companion object {
        const val MAX_SYNONYMS = 4
    }
}
