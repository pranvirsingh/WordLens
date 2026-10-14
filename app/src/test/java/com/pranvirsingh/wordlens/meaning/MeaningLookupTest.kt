package com.pranvirsingh.wordlens.meaning

import com.pranvirsingh.wordlens.dictionary.DictionaryRepository
import com.pranvirsingh.wordlens.dictionary.FakeLexicon
import com.pranvirsingh.wordlens.dictionary.PartOfSpeech
import com.pranvirsingh.wordlens.online.OnlineEntry
import com.pranvirsingh.wordlens.online.OnlineResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeaningLookupTest {

    private val lexicon = FakeLexicon()
        .sense("sample", PartOfSpeech.NOUN, "a small part", tagCount = 5)
        .irregular("samplez", PartOfSpeech.NOUN, "sample")

    private class FakeOnline(var result: OnlineResult, var known: OnlineResult? = null) : OnlineSource {
        val looked = mutableListOf<String>()
        val prefetched = mutableListOf<String>()
        override fun cached(word: String): OnlineResult? = known
        override fun prefetch(word: String) {
            prefetched += word
        }
        override suspend fun lookup(word: String): OnlineResult {
            looked += word
            return result
        }
    }

    private val simple = OnlineResult.Found(
        OnlineEntry(
            source = MeaningSource.SIMPLE_WIKTIONARY,
            pronunciation = null,
            groups = listOf(MeaningGroup("noun", listOf(MeaningSense("A sample is a small part.")))),
            synonyms = emptyList(),
            antonyms = emptyList(),
        ),
    )

    private fun lookup(online: OnlineSource, enabled: Boolean = true) =
        MeaningLookup(DictionaryRepository(openLexicon = { lexicon }), online, onlineEnabled = { enabled })

    @Test
    fun `offline meaning first, then the plainer online one`() = runTest {
        val steps = lookup(FakeOnline(simple)).lookUp(listOf("sample")).toList()
        assertEquals(2, steps.size)
        val first = steps[0] as MeaningLookup.Step.Ready
        val second = steps[1] as MeaningLookup.Step.Ready
        assertTrue(first.stillLoading)
        assertEquals(MeaningSource.OFFLINE, first.meaning.source)
        assertFalse(second.stillLoading)
        assertEquals(MeaningSource.SIMPLE_WIKTIONARY, second.meaning.source)
    }

    @Test
    fun `an online answer that is already here is shown straight away, with no offline flash`() = runTest {
        val steps = lookup(FakeOnline(simple, known = simple)).lookUp(listOf("sample")).toList()
        val only = steps.single() as MeaningLookup.Step.Ready
        assertEquals(MeaningSource.SIMPLE_WIKTIONARY, only.meaning.source)
        assertFalse(only.stillLoading)
    }

    @Test
    fun `online is asked about the dictionary form of the word`() = runTest {
        val online = FakeOnline(simple)
        lookup(online).lookUp(listOf("samplez")).toList()
        assertEquals(listOf("sample"), online.looked)
    }

    @Test
    fun `when online is unreachable the offline meaning stays, marked as offline`() = runTest {
        val steps = lookup(FakeOnline(OnlineResult.Unavailable)).lookUp(listOf("sample")).toList()
        val last = steps.last() as MeaningLookup.Step.Ready
        assertEquals(MeaningSource.OFFLINE, last.meaning.source)
        assertTrue(last.offlineOnly)
        assertFalse(last.stillLoading)
    }

    @Test
    fun `meanings that could not be fetched are flagged as offline`() = runTest {
        val partial = OnlineResult.Found(
            OnlineEntry(null, null, emptyList(), listOf("piece"), emptyList(), meaningsUnavailable = true),
        )
        val last = lookup(FakeOnline(partial)).lookUp(listOf("sample")).toList().last() as MeaningLookup.Step.Ready
        assertTrue(last.offlineOnly)
        assertEquals(MeaningSource.OFFLINE, last.meaning.source)
        assertEquals(listOf("piece"), last.meaning.similar)
    }

    @Test
    fun `a prefetch followed by the lookup reads the dictionary once`() = runTest {
        val lookup = lookup(FakeOnline(simple))
        lookup.prefetch(listOf("sample"))
        val readsAfterPrefetch = lexicon.sensesCalls.size
        lookup.lookUp(listOf("sample")).toList()
        assertEquals(readsAfterPrefetch, lexicon.sensesCalls.size)
    }

    @Test
    fun `with online meanings switched off nothing is sent`() = runTest {
        val online = FakeOnline(simple)
        val steps = lookup(online, enabled = false).lookUp(listOf("sample")).toList()
        assertEquals(1, steps.size)
        assertFalse((steps.single() as MeaningLookup.Step.Ready).stillLoading)
        assertTrue(online.looked.isEmpty())
        lookup(online, enabled = false).prefetch(listOf("sample"))
        assertTrue(online.prefetched.isEmpty())
    }

    @Test
    fun `a word only known online is found online`() = runTest {
        val steps = lookup(FakeOnline(simple)).lookUp(listOf("newword")).toList()
        val only = steps.single() as MeaningLookup.Step.Ready
        assertEquals("newword", only.meaning.headword)
    }

    @Test
    fun `unknown everywhere is not found`() = runTest {
        val steps = lookup(FakeOnline(OnlineResult.NotFound)).lookUp(listOf("qwrtz")).toList()
        assertEquals(listOf(MeaningLookup.Step.NotFound), steps)
    }

    @Test
    fun `prefetch warms the dictionary form`() = runTest {
        val online = FakeOnline(simple)
        lookup(online).prefetch(listOf("samplez"))
        assertEquals(listOf("sample"), online.prefetched)
    }
}
