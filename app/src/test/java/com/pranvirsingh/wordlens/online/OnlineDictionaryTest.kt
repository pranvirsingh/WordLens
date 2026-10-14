package com.pranvirsingh.wordlens.online

import com.pranvirsingh.wordlens.meaning.MeaningSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class OnlineDictionaryTest {

    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var routes: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(404) }

    private lateinit var dictionary: OnlineDictionary

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                return routes(request)
            }
        }
        server.start()
        val base = server.url("/")
        dictionary = OnlineDictionary(
            client = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build(),
            userAgent = "WordLensTest/1.0",
            endpoints = OnlineDictionary.Endpoints(
                simpleWiktionary = base.newBuilder().addPathSegment("simple").build(),
                wiktionary = base.newBuilder().addPathSegment("full").build(),
                datamuse = base.newBuilder().addPathSegment("muse").build(),
            ),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private val simplePage = json(
        """{"parse":{"title":"sample","wikitext":"== Noun ==\n# A '''sample''' is a small part. {{syn|bit}}\n#: ''Take a sample.''"}}""",
    )
    private val synonyms = json("""[{"word":"piece","tags":["f:30"]},{"word":"bit","tags":["f:80"]}]""")
    private val antonyms = json("""[{"word":"whole","tags":["f:60"]}]""")

    private fun everythingAnswers(request: RecordedRequest): MockResponse = when {
        request.path!!.startsWith("/simple") -> simplePage
        request.path!!.contains("rel_syn") -> synonyms
        request.path!!.contains("rel_ant") -> antonyms
        else -> MockResponse().setResponseCode(404)
    }

    @Test
    fun `combines simple meanings with everyday synonyms and opposites`() = runBlocking {
        routes = ::everythingAnswers
        val entry = (dictionary.lookup("sample") as OnlineResult.Found).entry
        assertEquals(MeaningSource.SIMPLE_WIKTIONARY, entry.source)
        assertEquals("A sample is a small part.", entry.groups.single().senses.single().definition)
        assertEquals(listOf("Take a sample."), entry.groups.single().senses.single().examples)
        assertEquals(listOf("bit", "piece"), entry.synonyms)
        assertEquals(listOf("whole"), entry.antonyms)
    }

    @Test
    fun `falls back to full wiktionary when simple english has no page`() = runBlocking {
        routes = { request ->
            when {
                request.path!!.startsWith("/simple") -> json("""{"error":{"code":"missingtitle"}}""")
                request.path!!.startsWith("/full") -> json(
                    """{"en":[{"partOfSpeech":"Noun","definitions":[{"definition":"A formal sense."}]}]}""",
                )
                else -> json("[]")
            }
        }
        val entry = (dictionary.lookup("rareword") as OnlineResult.Found).entry
        assertEquals(MeaningSource.WIKTIONARY, entry.source)
        assertEquals("A formal sense.", entry.groups.single().senses.single().definition)
        assertTrue(requests.any { it.path == "/full/api/rest_v1/page/definition/rareword" })
    }

    @Test
    fun `nothing known anywhere is not found`() = runBlocking {
        routes = { request -> if (request.path!!.startsWith("/muse")) json("[]") else MockResponse().setResponseCode(404) }
        assertEquals(OnlineResult.NotFound, dictionary.lookup("qwrtz"))
    }

    @Test
    fun `every source failing is unavailable, and is retried next time`() = runBlocking {
        routes = { MockResponse().setResponseCode(503) }
        assertEquals(OnlineResult.Unavailable, dictionary.lookup("sample"))
        routes = ::everythingAnswers
        assertTrue(dictionary.lookup("sample") is OnlineResult.Found)
    }

    @Test
    fun `garbage from one source does not spoil the others`() = runBlocking {
        routes = { request ->
            when {
                request.path!!.startsWith("/simple") -> json("not json at all")
                request.path!!.startsWith("/full") -> MockResponse().setResponseCode(500)
                else -> everythingAnswers(request)
            }
        }
        val entry = (dictionary.lookup("sample") as OnlineResult.Found).entry
        assertTrue(entry.groups.isEmpty())
        assertTrue(entry.meaningsUnavailable)
        // Without the wiki's own synonyms first, the everyday list keeps its order.
        assertEquals(listOf("piece", "bit"), entry.synonyms)
    }

    @Test
    fun `an answer missing its meanings because of a network problem is asked again next time`() = runBlocking {
        routes = { request -> if (request.path!!.startsWith("/muse")) everythingAnswers(request) else MockResponse().setResponseCode(503) }
        val first = (dictionary.lookup("sample") as OnlineResult.Found).entry
        assertTrue(first.meaningsUnavailable)
        routes = ::everythingAnswers
        val second = (dictionary.lookup("sample") as OnlineResult.Found).entry
        assertEquals(MeaningSource.SIMPLE_WIKTIONARY, second.source)
    }

    @Test
    fun `no simple page plus a failed full wiktionary is not remembered as the final answer`() = runBlocking {
        routes = { request ->
            when {
                request.path!!.startsWith("/simple") -> json("""{"error":{"code":"missingtitle"}}""")
                request.path!!.startsWith("/full") -> MockResponse().setResponseCode(503)
                else -> everythingAnswers(request)
            }
        }
        val first = (dictionary.lookup("sample") as OnlineResult.Found).entry
        assertTrue(first.meaningsUnavailable)
        val before = requests.size
        dictionary.lookup("sample")
        assertTrue(requests.size > before)
    }

    @Test
    fun `a failed synonym request is not remembered either`() = runBlocking {
        routes = { request -> if (request.path!!.contains("rel_syn")) MockResponse().setResponseCode(500) else everythingAnswers(request) }
        dictionary.lookup("sample")
        routes = ::everythingAnswers
        val entry = (dictionary.lookup("sample") as OnlineResult.Found).entry
        assertEquals(listOf("bit", "piece"), entry.synonyms)
    }

    @Test
    fun `a complete answer is available instantly from the cache`() = runBlocking {
        routes = ::everythingAnswers
        assertEquals(null, dictionary.cached("sample"))
        dictionary.lookup("sample")
        assertTrue(dictionary.cached("sample") is OnlineResult.Found)
    }

    @Test
    fun `meanings failing with nothing else found is unavailable, not 'unknown word'`() = runBlocking {
        routes = { request -> if (request.path!!.startsWith("/muse")) json("[]") else MockResponse().setResponseCode(503) }
        assertEquals(OnlineResult.Unavailable, dictionary.lookup("sample"))
    }

    @Test
    fun `full wiktionary is still used when simple english errors`() = runBlocking {
        routes = { request ->
            when {
                request.path!!.startsWith("/simple") -> MockResponse().setResponseCode(500)
                request.path!!.startsWith("/full") -> json(
                    """{"en":[{"partOfSpeech":"Noun","definitions":[{"definition":"A formal sense."}]}]}""",
                )
                else -> json("[]")
            }
        }
        val entry = (dictionary.lookup("sample") as OnlineResult.Found).entry
        assertEquals(MeaningSource.WIKTIONARY, entry.source)
        assertTrue(!entry.meaningsUnavailable)
    }

    @Test
    fun `both wiktionaries are asked together, so a slow one does not delay the other`() = runBlocking {
        routes = { request ->
            when {
                request.path!!.startsWith("/simple") ->
                    json("""{"error":{"code":"missingtitle"}}""").setBodyDelay(600, TimeUnit.MILLISECONDS)
                request.path!!.startsWith("/full") ->
                    json("""{"en":[{"partOfSpeech":"Noun","definitions":[{"definition":"A sense."}]}]}""")
                        .setBodyDelay(600, TimeUnit.MILLISECONDS)
                else -> json("[]")
            }
        }
        val started = System.nanoTime()
        dictionary.lookup("sample")
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue("took ${millis}ms", millis < 1_100)
    }

    @Test
    fun `a slow service times out instead of hanging`() = runBlocking {
        routes = { MockResponse().setBody("[]").setHeadersDelay(5, TimeUnit.SECONDS) }
        assertEquals(OnlineResult.Unavailable, dictionary.lookup("sample"))
    }

    @Test
    fun `answers are remembered`() = runBlocking {
        routes = ::everythingAnswers
        dictionary.lookup("sample")
        val before = requests.size
        dictionary.lookup("sample")
        assertEquals(before, requests.size)
    }

    @Test
    fun `a prefetch and a lookup at the same time share one request`() = runBlocking {
        routes = { request -> everythingAnswers(request).setBodyDelay(200, TimeUnit.MILLISECONDS) }
        dictionary.prefetch("sample")
        val results = (1..3).map { async { dictionary.lookup("sample") } }.awaitAll()
        assertTrue(results.all { it is OnlineResult.Found })
        assertEquals(1, requests.count { it.path!!.startsWith("/simple") })
    }

    @Test
    fun `requests identify the app and ask for json`() = runBlocking {
        routes = ::everythingAnswers
        dictionary.lookup("sample")
        assertTrue(requests.all { it.getHeader("User-Agent") == "WordLensTest/1.0" })
        val simple = requests.first { it.path!!.startsWith("/simple") }
        assertEquals("sample", simple.requestUrl!!.queryParameter("page"))
        assertEquals("wikitext", simple.requestUrl!!.queryParameter("prop"))
    }
}
