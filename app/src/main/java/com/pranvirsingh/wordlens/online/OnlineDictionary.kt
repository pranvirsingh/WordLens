package com.pranvirsingh.wordlens.online

import com.pranvirsingh.wordlens.meaning.MeaningGroup
import com.pranvirsingh.wordlens.meaning.MeaningSource
import com.pranvirsingh.wordlens.meaning.OnlineSource
import com.pranvirsingh.wordlens.meaning.RelatedWords
import com.pranvirsingh.wordlens.util.getText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Fetches plain-English meanings, examples, synonyms and opposites for a word
 * from free services that need no account or key:
 *
 * - Simple English Wiktionary — definitions written in everyday words;
 * - the full English Wiktionary — when Simple English has no entry;
 * - Datamuse — common synonyms and opposites.
 *
 * All three are asked at once. Results are remembered, and asking for a word
 * that's already on its way joins that request instead of starting another —
 * so the scan screen can start fetching while the reader is still aiming.
 */
class OnlineDictionary(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val endpoints: Endpoints = Endpoints.Production,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OnlineSource {
    data class Endpoints(val simpleWiktionary: HttpUrl, val wiktionary: HttpUrl, val datamuse: HttpUrl) {
        companion object {
            val Production = Endpoints(
                simpleWiktionary = "https://simple.wiktionary.org/".toHttpUrl(),
                wiktionary = "https://en.wiktionary.org/".toHttpUrl(),
                datamuse = "https://api.datamuse.com/".toHttpUrl(),
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lock = Any()
    private val inFlight = HashMap<String, Deferred<OnlineResult>>()
    private val cache = object : LinkedHashMap<String, OnlineResult>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OnlineResult>) = size > CACHE_SIZE
    }

    /** Starts fetching [word] in the background if it isn't known or on its way already. */
    override fun prefetch(word: String) {
        request(word)
    }

    /** The online answer for [word]; never throws for network trouble — that's [OnlineResult.Unavailable]. */
    override suspend fun lookup(word: String): OnlineResult = request(word).await()

    private fun request(word: String): Deferred<OnlineResult> = synchronized(lock) {
        cache[word]?.let { return CompletableDeferred(it) }
        inFlight[word]?.let { return it }
        // Started only after it's registered, so it can never finish before it's in the map.
        val job = scope.async(start = CoroutineStart.LAZY) {
            try {
                val (result, complete) = fetch(word)
                // Remember only answers every source really gave; anything touched by a
                // network problem is worth asking again next time.
                if (complete) synchronized(lock) { cache[word] = result }
                result
            } finally {
                synchronized(lock) { inFlight.remove(word) }
            }
        }
        inFlight[word] = job
        job.start()
        job
    }

    /** An already-known answer for [word], without any network request. */
    override fun cached(word: String): OnlineResult? = synchronized(lock) { cache[word] }

    /** The answer, and whether every source answered (so it's safe to remember). */
    private suspend fun fetch(word: String): Pair<OnlineResult, Boolean> = coroutineScope {
        val meanings = async { attempt { fetchMeanings(word) } }
        val synonyms = async { attempt { fetchWords("rel_syn", word, MAX_SYNONYMS) } }
        val antonyms = async { attempt { fetchWords("rel_ant", word, MAX_ANTONYMS) } }
        val meaningResult = meanings.await()
        val synonymResult = synonyms.await()
        val antonymResult = antonyms.await()

        val wiki = meaningResult.getOrNull()
        val entry = OnlineEntry(
            source = wiki?.source,
            pronunciation = wiki?.pronunciation,
            groups = wiki?.groups.orEmpty(),
            synonyms = RelatedWords.merge(word, MAX_SYNONYMS, wiki?.synonyms.orEmpty(), synonymResult.getOrNull().orEmpty()),
            antonyms = RelatedWords.merge(word, MAX_ANTONYMS, wiki?.antonyms.orEmpty(), antonymResult.getOrNull().orEmpty()),
            meaningsUnavailable = wiki == null || (!wiki.complete && wiki.groups.isEmpty()),
        )
        val result = when {
            // Without an answer about the meaning itself, "nothing found" can't be trusted.
            entry.meaningsUnavailable && entry.isEmpty -> OnlineResult.Unavailable
            entry.isEmpty -> OnlineResult.NotFound
            else -> OnlineResult.Found(entry)
        }
        val complete = wiki?.complete == true && synonymResult.isSuccess && antonymResult.isSuccess
        result to complete
    }

    private class WikiMeanings(
        val source: MeaningSource?,
        val pronunciation: String?,
        val groups: List<MeaningGroup>,
        val synonyms: List<String>,
        val antonyms: List<String>,
        /** False when one of the two wikis didn't answer, so a better meaning may exist. */
        val complete: Boolean,
    )

    /**
     * Asks Simple English and full Wiktionary at the same time, preferring the
     * simple wording. Waiting for one before asking the other would double the
     * worst-case wait, and an error from one must not lose the other.
     */
    private suspend fun fetchMeanings(word: String): WikiMeanings = coroutineScope {
        val simple = async { attempt { fetchSimple(word) } }
        val full = async { attempt { fetchFull(word) } }

        val simpleResult = simple.await()
        val simplePage = simpleResult.getOrNull()
        if (simplePage != null && simplePage.groups.isNotEmpty()) {
            full.cancel()
            return@coroutineScope WikiMeanings(
                MeaningSource.SIMPLE_WIKTIONARY,
                simplePage.pronunciation,
                simplePage.groups,
                simplePage.synonyms,
                simplePage.antonyms,
                complete = true,
            )
        }
        val fullResult = full.await()
        if (simpleResult.isFailure && fullResult.isFailure) throw IOException("Neither Wiktionary answered")
        val groups = fullResult.getOrNull().orEmpty()
        WikiMeanings(
            source = if (groups.isNotEmpty()) MeaningSource.WIKTIONARY else null,
            pronunciation = simplePage?.pronunciation,
            groups = groups,
            synonyms = simplePage?.synonyms.orEmpty(),
            antonyms = simplePage?.antonyms.orEmpty(),
            complete = simpleResult.isSuccess && fullResult.isSuccess,
        )
    }

    private suspend fun fetchSimple(word: String): WikitextParser.Parsed? {
        val url = endpoints.simpleWiktionary.newBuilder()
            .addPathSegments("w/api.php")
            .addQueryParameter("action", "parse")
            .addQueryParameter("page", word)
            .addQueryParameter("prop", "wikitext")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()
        return get(url)?.let(SimpleWiktionaryResponse::wikitext)?.let(WikitextParser::parse)
    }

    private suspend fun fetchFull(word: String): List<MeaningGroup> {
        val url = endpoints.wiktionary.newBuilder()
            .addPathSegments("api/rest_v1/page/definition")
            .addPathSegment(word)
            .build()
        return get(url)?.let(WiktionaryResponse::groups).orEmpty()
    }

    private suspend fun fetchWords(relation: String, word: String, limit: Int): List<String> {
        val url = endpoints.datamuse.newBuilder()
            .addPathSegment("words")
            .addQueryParameter(relation, word)
            .addQueryParameter("md", "f")
            .addQueryParameter("max", "20")
            .build()
        return get(url)?.let { DatamuseResponse.commonWords(it, exclude = word, limit = limit) }.orEmpty()
    }

    private suspend fun get(url: HttpUrl): String? = client.getText(url, userAgent, accept = "application/json")

    /** Network trouble, odd responses, even a parsing slip: all just mean "no answer from this source". */
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val CACHE_SIZE = 200
        const val MAX_SYNONYMS = 6
        const val MAX_ANTONYMS = 4
    }
}
