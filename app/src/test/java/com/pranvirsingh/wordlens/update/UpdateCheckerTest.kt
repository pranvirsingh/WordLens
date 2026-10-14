package com.pranvirsingh.wordlens.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class UpdateCheckerTest {

    private val release = """
        {"tag_name":"v1.2.0","draft":false,"prerelease":false,
         "html_url":"https://github.com/pranvirsingh/WordLens/releases/tag/v1.2.0",
         "body":"## v1.2.0 — Updates\n\nThe app tells you when a new version is out.\n\n- **New** banner\n- Menu item\n\n**Install:** download the APK below.",
         "assets":[{"name":"notes.txt","browser_download_url":"https://github.com/pranvirsingh/WordLens/releases/download/v1.2.0/notes.txt","size":10},
                   {"name":"WordLens-v1.2.0.apk","browser_download_url":"https://github.com/pranvirsingh/WordLens/releases/download/v1.2.0/WordLens-v1.2.0.apk","size":32143490}]}
    """.trimIndent()

    @Test
    fun `reads version, notes and the apk link`() {
        val update = UpdateChecker.parse(release)!!
        assertEquals("1.2.0", update.version)
        assertEquals("https://github.com/pranvirsingh/WordLens/releases/download/v1.2.0/WordLens-v1.2.0.apk", update.apkUrl)
        assertEquals(32143490L, update.apkBytes)
        assertEquals("https://github.com/pranvirsingh/WordLens/releases/tag/v1.2.0", update.pageUrl)
        assertEquals("v1.2.0 — Updates\n\nThe app tells you when a new version is out.\n\n• New banner\n• Menu item", update.notes)
    }

    @Test
    fun `a release without an apk, a draft, a pre-release or junk is ignored`() {
        assertNull(UpdateChecker.parse(release.replace("WordLens-v1.2.0.apk", "WordLens-v1.2.0.zip")))
        assertNull(UpdateChecker.parse(release.replace("\"draft\":false", "\"draft\":true")))
        assertNull(UpdateChecker.parse(release.replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(UpdateChecker.parse("<html>rate limited</html>"))
        assertNull(UpdateChecker.parse("[]"))
    }

    @Test
    fun `links that are not https on GitHub are refused`() {
        assertNull(UpdateChecker.parse(release.replace("https://github.com/pranvirsingh/WordLens/releases/download", "http://github.com/pranvirsingh/WordLens/releases/download")))
        assertNull(UpdateChecker.parse(release.replace("https://github.com/pranvirsingh/WordLens/releases/download", "https://evil.example/download")))
        assertTrue(UpdateChecker.isTrustedLink("https://objects.githubusercontent.com/x.apk"))
        assertFalse(UpdateChecker.isTrustedLink("intent://anything"))
        assertFalse(UpdateChecker.isTrustedLink("file:///sdcard/x.apk"))
    }

    @Test
    fun `notes lose markdown links and horizontal rules`() {
        assertEquals("See the guide.\nDone", UpdateChecker.cleanNotes("See [the guide](https://x).\n---\nDone"))
    }

    @Test
    fun `dividers in any style are dropped and numbers keep their hash`() {
        assertEquals("Fixed #12\nDone", UpdateChecker.cleanNotes("Fixed #12\n***\n* * *\n___\nDone"))
        assertEquals("Title", UpdateChecker.cleanNotes("## Title"))
    }

    @Test
    fun `newer versions are recognised numerically`() {
        assertTrue(VersionName.isNewer("1.2.0", "1.1.0"))
        assertTrue(VersionName.isNewer("v1.10.0", "1.9.9"))
        assertTrue(VersionName.isNewer("2.0", "1.9.9"))
        assertTrue(VersionName.isNewer("1.2.0", "1.1.0-debug"))
        assertFalse(VersionName.isNewer("1.1.0", "1.1.0"))
        assertFalse(VersionName.isNewer("1.1.0", "1.2.0"))
        assertFalse(VersionName.isNewer("1.2.0", "1.2.0-debug"))
        assertFalse(VersionName.isNewer("latest", "1.0.0"))
    }

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun checker() = UpdateChecker(OkHttpClient(), "WordLensTest/1.0", server.url("/latest"))

    @Test
    fun `fetches the latest release and identifies the app`() = runBlocking {
        server.enqueue(MockResponse().setBody(release))
        assertEquals("1.2.0", checker().latestRelease()!!.version)
        val request = server.takeRequest()
        assertEquals("WordLensTest/1.0", request.getHeader("User-Agent"))
    }

    @Test
    fun `no releases yet is simply nothing`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(checker().latestRelease())
    }

    @Test(expected = IOException::class)
    fun `a server error is reported so the check is retried later`() {
        server.enqueue(MockResponse().setResponseCode(403))
        runBlocking { checker().latestRelease() }
    }
}
