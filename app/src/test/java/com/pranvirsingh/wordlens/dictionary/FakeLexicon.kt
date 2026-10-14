package com.pranvirsingh.wordlens.dictionary

/** A tiny in-memory dictionary for tests. */
class FakeLexicon : Lexicon {
    private val senses = mutableMapOf<String, MutableList<SenseRecord>>()
    private val exceptions = mutableMapOf<String, MutableList<Pair<PartOfSpeech, String>>>()
    val sensesCalls = mutableListOf<String>()

    fun sense(
        lemma: String,
        pos: PartOfSpeech,
        definition: String,
        tagCount: Int = 0,
        examples: List<String> = emptyList(),
        lemmas: List<String> = listOf(lemma),
    ): FakeLexicon = apply {
        val list = senses.getOrPut(lemma) { mutableListOf() }
        val rank = list.count { it.pos == pos }
        list += SenseRecord(pos, rank, tagCount, definition, examples, lemmas)
    }

    fun irregular(form: String, pos: PartOfSpeech, base: String): FakeLexicon = apply {
        exceptions.getOrPut(form) { mutableListOf() } += pos to base
    }

    override fun senses(lemma: String): List<SenseRecord> {
        sensesCalls += lemma
        return senses[lemma].orEmpty()
    }

    override fun exceptionBases(form: String): List<Pair<PartOfSpeech, String>> = exceptions[form].orEmpty()
}
