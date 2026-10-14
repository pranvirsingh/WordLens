package com.pranvirsingh.wordlens.dictionary

/** Read access to the dictionary that the lemmatizer and lookups need. */
interface Lexicon {
    /** Every stored meaning of [lemma], across all parts of speech. */
    fun senses(lemma: String): List<SenseRecord>

    /** Irregular base forms of [form], e.g. "went" → (VERB, "go"). */
    fun exceptionBases(form: String): List<Pair<PartOfSpeech, String>>
}

/**
 * Finds the dictionary forms behind an inflected word ("running" → "run",
 * "children" → "child", "saw" → "saw"/"see"), following WordNet's own
 * morphology rules and its list of irregular forms.
 */
class Lemmatizer(private val lexicon: Lexicon) {

    data class Candidate(val lemma: String, val pos: PartOfSpeech)

    private val suffixRules = mapOf(
        PartOfSpeech.NOUN to listOf(
            "s" to "", "ses" to "s", "xes" to "x", "zes" to "z",
            "ches" to "ch", "shes" to "sh", "men" to "man", "ies" to "y",
        ),
        PartOfSpeech.VERB to listOf(
            "s" to "", "ies" to "y", "es" to "e", "es" to "",
            "ed" to "e", "ed" to "", "ing" to "e", "ing" to "",
        ),
        PartOfSpeech.ADJECTIVE to listOf(
            "er" to "", "est" to "", "er" to "e", "est" to "e",
        ),
        PartOfSpeech.ADVERB to emptyList(),
    )

    // Suffixes that double a final consonant: "stopping", "bigger", "travelled".
    private val doublingSuffixes = setOf("ed", "ing", "er", "est")

    fun baseForms(word: String): List<Candidate> {
        val exceptions = lexicon.exceptionBases(word)
        val result = LinkedHashSet<Candidate>()
        for (pos in PartOfSpeech.entries) {
            val forms = buildList {
                add(word)
                val irregular = exceptions.filter { it.first == pos }.map { it.second }
                if (irregular.isNotEmpty()) {
                    addAll(irregular)
                } else {
                    addAll(applyRules(word, pos))
                }
            }
            for (form in forms) {
                if (form.isNotEmpty() && hasLemma(form, pos)) result += Candidate(form, pos)
            }
        }
        return result.toList()
    }

    private fun applyRules(word: String, pos: PartOfSpeech): List<String> = buildList {
        for ((suffix, replacement) in suffixRules.getValue(pos)) {
            if (!word.endsWith(suffix) || word.length <= suffix.length + 1) continue
            val stem = word.dropLast(suffix.length)
            add(stem + replacement)
            if (replacement.isEmpty() && suffix in doublingSuffixes && hasDoubledEnding(stem)) {
                add(stem.dropLast(1))
            }
        }
    }

    private fun hasDoubledEnding(stem: String): Boolean =
        stem.length >= 3 && stem.last() == stem[stem.length - 2] && stem.last() !in "aeiou"

    private fun hasLemma(lemma: String, pos: PartOfSpeech): Boolean =
        lexicon.senses(lemma).any { it.pos == pos }
}

/** Remembers answers for the length of one lookup, which asks about the same lemma repeatedly. */
class CachingLexicon(private val source: Lexicon) : Lexicon {
    private val senses = HashMap<String, List<SenseRecord>>()
    private val exceptions = HashMap<String, List<Pair<PartOfSpeech, String>>>()

    override fun senses(lemma: String): List<SenseRecord> =
        senses.getOrPut(lemma) { source.senses(lemma) }

    override fun exceptionBases(form: String): List<Pair<PartOfSpeech, String>> =
        exceptions.getOrPut(form) { source.exceptionBases(form) }
}
