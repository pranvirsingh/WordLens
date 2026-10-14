package com.pranvirsingh.wordlens.dictionary

import com.pranvirsingh.wordlens.dictionary.PartOfSpeech.ADJECTIVE
import com.pranvirsingh.wordlens.dictionary.PartOfSpeech.NOUN
import com.pranvirsingh.wordlens.dictionary.PartOfSpeech.VERB
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DictionaryRepositoryTest {

    private val lexicon = FakeLexicon()
        .sense("saw", NOUN, "a cutting tool", tagCount = 2)
        .sense("saw", VERB, "cut with a saw", tagCount = 1)
        .sense("see", VERB, "perceive with the eyes", tagCount = 500, examples = listOf("I see the light"))
        .sense("see", VERB, "understand", tagCount = 200, lemmas = listOf("see", "understand", "get", "grasp", "realize", "dig"))
        .sense("wonderful", ADJECTIVE, "extraordinarily good", lemmas = listOf("wonderful", "fantastic"))
        .sense("blank", NOUN, "  ")
        .irregular("saw", VERB, "see")

    private fun repository(source: Lexicon = lexicon) =
        DictionaryRepository(openLexicon = { source })

    @Test
    fun `the most common reading comes first`() = runTest {
        val result = repository(lexicon).lookup(listOf("saw"))!!
        assertEquals(listOf("see" to VERB, "saw" to NOUN, "saw" to VERB), result.entries.map { it.lemma to it.pos })
    }

    @Test
    fun `senses keep their own order and carry examples`() = runTest {
        val see = repository().lookup(listOf("see"))!!.entries.single()
        assertEquals(listOf("perceive with the eyes", "understand"), see.senses.map { it.definition })
        assertEquals(listOf("I see the light"), see.senses.first().examples)
    }

    @Test
    fun `synonyms leave out the word itself and are capped`() = runTest {
        val see = repository().lookup(listOf("see"))!!.entries.single()
        assertEquals(listOf("understand", "get", "grasp", "realize"), see.senses[1].synonyms)
    }

    @Test
    fun `falls through to the next spelling when the first is unknown`() = runTest {
        val result = repository().lookup(listOf("wonder-ful", "wonderful"))!!
        assertEquals("wonderful", result.query)
        assertEquals(listOf("fantastic"), result.entries.single().senses.single().synonyms)
    }

    @Test
    fun `unknown words return null`() = runTest {
        assertNull(repository().lookup(listOf("zyxwv")))
        assertNull(repository().lookup(emptyList()))
    }

    @Test
    fun `entries with only blank definitions are skipped`() = runTest {
        assertNull(repository().lookup(listOf("blank")))
    }

    @Test
    fun `each lemma is fetched from storage only once per lookup`() = runTest {
        lexicon.sensesCalls.clear()
        repository().lookup(listOf("saw"))
        assertEquals(lexicon.sensesCalls.distinct(), lexicon.sensesCalls)
    }

    @Test(expected = IOException::class)
    fun `a broken dictionary surfaces as an error, not as 'not found'`() = runTest {
        val broken = object : Lexicon {
            override fun senses(lemma: String): List<SenseRecord> = throw IOException("disk error")
            override fun exceptionBases(form: String): List<Pair<PartOfSpeech, String>> = emptyList()
        }
        repository(broken).lookup(listOf("anything"))
    }

    @Test
    fun `runs on the supplied dispatcher`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val result = DictionaryRepository(openLexicon = { lexicon }, io = dispatcher).lookup(listOf("see"))
        assertTrue(result != null)
    }
}
