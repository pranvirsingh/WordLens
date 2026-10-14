package com.pranvirsingh.wordlens.dictionary

import com.pranvirsingh.wordlens.dictionary.Lemmatizer.Candidate
import com.pranvirsingh.wordlens.dictionary.PartOfSpeech.ADJECTIVE
import com.pranvirsingh.wordlens.dictionary.PartOfSpeech.NOUN
import com.pranvirsingh.wordlens.dictionary.PartOfSpeech.VERB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LemmatizerTest {

    private val lexicon = FakeLexicon()
        .sense("walk", VERB, "use your feet")
        .sense("walk", NOUN, "a stroll")
        .sense("hope", VERB, "want something")
        .sense("hop", VERB, "jump lightly")
        .sense("stop", VERB, "come to a halt")
        .sense("box", NOUN, "a container")
        .sense("city", NOUN, "a large town")
        .sense("woman", NOUN, "an adult female")
        .sense("big", ADJECTIVE, "large")
        .sense("late", ADJECTIVE, "after the time")
        .sense("glass", NOUN, "a hard clear material")
        .sense("child", NOUN, "a young person")
        .sense("go", VERB, "move")
        .sense("saw", NOUN, "a cutting tool")
        .sense("saw", VERB, "cut with a saw")
        .sense("see", VERB, "perceive with the eyes")
        .sense("travel", VERB, "make a journey")
        .irregular("children", NOUN, "child")
        .irregular("went", VERB, "go")
        .irregular("saw", VERB, "see")

    private val lemmatizer = Lemmatizer(lexicon)

    private fun lemmas(word: String) = lemmatizer.baseForms(word).map { it.lemma to it.pos }

    @Test
    fun `a dictionary word finds itself in every part of speech`() {
        assertEquals(listOf("walk" to NOUN, "walk" to VERB), lemmas("walk"))
    }

    @Test
    fun `regular inflections reduce to the base form`() {
        assertEquals(listOf("walk" to NOUN, "walk" to VERB), lemmas("walks"))
        assertEquals(listOf("walk" to VERB), lemmas("walked"))
        assertEquals(listOf("walk" to VERB), lemmas("walking"))
        assertEquals(listOf("box" to NOUN), lemmas("boxes"))
        assertEquals(listOf("city" to NOUN), lemmas("cities"))
        assertEquals(listOf("woman" to NOUN), lemmas("women"))
        assertEquals(listOf("late" to ADJECTIVE), lemmas("later"))
    }

    @Test
    fun `an ending that fits two base words returns both, e-restored first`() {
        assertEquals(listOf("hope" to VERB, "hop" to VERB), lemmas("hoping"))
    }

    @Test
    fun `doubled final consonants are undone`() {
        assertEquals(listOf("hop" to VERB), lemmas("hopping"))
        assertEquals(listOf("stop" to VERB), lemmas("stopped"))
        assertEquals(listOf("big" to ADJECTIVE), lemmas("biggest"))
        assertEquals(listOf("travel" to VERB), lemmas("travelled"))
    }

    @Test
    fun `irregular forms use the exception list`() {
        assertEquals(listOf("child" to NOUN), lemmas("children"))
        assertEquals(listOf("go" to VERB), lemmas("went"))
    }

    @Test
    fun `an ambiguous form returns every reading`() {
        val result = lemmatizer.baseForms("saw")
        assertEquals(
            listOf(Candidate("saw", NOUN), Candidate("saw", VERB), Candidate("see", VERB)),
            result,
        )
    }

    @Test
    fun `words ending in double s are not mangled`() {
        assertEquals(listOf("glass" to NOUN), lemmas("glass"))
    }

    @Test
    fun `unknown words give nothing`() {
        assertTrue(lemmatizer.baseForms("zyxwv").isEmpty())
        assertTrue(lemmatizer.baseForms("s").isEmpty())
    }
}
