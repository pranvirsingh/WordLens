package com.pranvirsingh.wordlens.meaning

import com.pranvirsingh.wordlens.dictionary.Entry
import com.pranvirsingh.wordlens.dictionary.LookupResult
import com.pranvirsingh.wordlens.dictionary.PartOfSpeech
import com.pranvirsingh.wordlens.dictionary.Sense
import com.pranvirsingh.wordlens.online.OnlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeaningPresenterTest {

    private val offline = LookupResult(
        query = "samples",
        entries = listOf(
            Entry(
                lemma = "sample",
                pos = PartOfSpeech.NOUN,
                senses = listOf(
                    Sense("a small part intended as representative of the whole", listOf("a sample of the cloth"), listOf("example", "sampling"), listOf("whole")),
                    Sense("items selected at random", emptyList(), listOf("random sample")),
                ),
            ),
            Entry("sample", PartOfSpeech.VERB, listOf(Sense("take a sample of", emptyList(), listOf("try", "taste")))),
        ),
    )

    private val online = OnlineEntry(
        source = MeaningSource.SIMPLE_WIKTIONARY,
        pronunciation = "/ˈsæmpəl/",
        groups = listOf(MeaningGroup("noun", listOf(MeaningSense("A sample is a small part.", listOf("Take a sample.", "Two.", "Three."))))),
        synonyms = listOf("piece", "Example"),
        antonyms = listOf("whole"),
    )

    @Test
    fun `offline alone reads as tidy sentences`() {
        val meaning = MeaningPresenter.present(offline, null, "samples")!!
        assertEquals(MeaningSource.OFFLINE, meaning.source)
        assertEquals("sample", meaning.headword)
        assertEquals(listOf("noun", "verb"), meaning.groups.map { it.partOfSpeech })
        assertEquals("A small part intended as representative of the whole.", meaning.groups[0].senses[0].definition)
        assertEquals(listOf("A sample of the cloth."), meaning.groups[0].senses[0].examples)
        assertNull(meaning.pronunciation)
    }

    @Test
    fun `online meanings replace the offline wording`() {
        val meaning = MeaningPresenter.present(offline, online, "samples")!!
        assertEquals(MeaningSource.SIMPLE_WIKTIONARY, meaning.source)
        assertEquals("A sample is a small part.", meaning.groups.single().senses.single().definition)
        assertEquals("/ˈsæmpəl/", meaning.pronunciation)
    }

    @Test
    fun `readings of a different word are kept and named, even after the online upgrade`() {
        val saw = LookupResult(
            query = "saw",
            entries = listOf(
                Entry("see", PartOfSpeech.VERB, listOf(Sense("perceive by sight", emptyList(), emptyList()))),
                Entry("saw", PartOfSpeech.NOUN, listOf(Sense("a hand tool for cutting", emptyList(), emptyList()))),
            ),
        )
        val offlineOnly = MeaningPresenter.present(saw, null, "saw")!!
        assertEquals(listOf(null, "saw"), offlineOnly.groups.map { it.otherWord })

        val seeOnline = online.copy(groups = listOf(MeaningGroup("verb", listOf(MeaningSense("If you see something, you notice it.")))))
        val upgraded = MeaningPresenter.present(saw, seeOnline, "saw")!!
        assertEquals("see", upgraded.headword)
        assertEquals(listOf("verb", "noun"), upgraded.groups.map { it.partOfSpeech })
        assertEquals("If you see something, you notice it.", upgraded.groups[0].senses.single().definition)
        assertEquals("saw", upgraded.groups[1].otherWord)
        assertEquals("A hand tool for cutting.", upgraded.groups[1].senses.single().definition)
    }

    @Test
    fun `examples are capped at two`() {
        val meaning = MeaningPresenter.present(offline, online, "samples")!!
        assertEquals(listOf("Take a sample.", "Two."), meaning.groups.single().senses.single().examples)
    }

    @Test
    fun `similar and opposite words are pooled, online first, without duplicates or the word itself`() {
        val meaning = MeaningPresenter.present(offline, online, "samples")!!
        assertEquals(listOf("piece", "Example", "sampling", "random sample"), meaning.similar)
        assertEquals(listOf("whole"), meaning.opposite)
    }

    @Test
    fun `online synonyms alone keep the offline meanings`() {
        val synonymsOnly = online.copy(source = null, groups = emptyList())
        val meaning = MeaningPresenter.present(offline, synonymsOnly, "samples")!!
        assertEquals(MeaningSource.OFFLINE, meaning.source)
        assertEquals("piece", meaning.similar.first())
    }

    @Test
    fun `a word only the online sources know is still shown`() {
        val meaning = MeaningPresenter.present(null, online, "sample")!!
        assertEquals("sample", meaning.headword)
        assertEquals(MeaningSource.SIMPLE_WIKTIONARY, meaning.source)
    }

    @Test
    fun `nothing from either source gives nothing`() {
        assertNull(MeaningPresenter.present(null, null, "x"))
        assertNull(MeaningPresenter.present(null, online.copy(groups = emptyList()), "x"))
    }

    @Test
    fun `sentences get a capital and a full stop only when they need one`() {
        assertEquals("A cutting tool.", MeaningPresenter.asSentence("a cutting tool"))
        assertEquals("Is it?", MeaningPresenter.asSentence("is it?"))
        assertEquals("“Quoted”", MeaningPresenter.asSentence("“Quoted”"))
        assertEquals("", MeaningPresenter.asSentence("   "))
    }
}
