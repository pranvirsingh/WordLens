package com.pranvirsingh.wordlens.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseParsersTest {

    @Test
    fun `simple wiktionary page text is extracted`() {
        val body = """{"parse":{"title":"sample","pageid":1,"wikitext":"== Noun ==\n# A part."}}"""
        assertEquals("== Noun ==\n# A part.", SimpleWiktionaryResponse.wikitext(body))
    }

    @Test
    fun `a missing simple wiktionary page is null, not an error`() {
        val body = """{"error":{"code":"missingtitle","info":"The page you specified doesn't exist."}}"""
        assertNull(SimpleWiktionaryResponse.wikitext(body))
    }

    @Test(expected = MalformedResponseException::class)
    fun `other wiki errors are reported`() {
        SimpleWiktionaryResponse.wikitext("""{"error":{"code":"ratelimited"}}""")
    }

    @Test(expected = MalformedResponseException::class)
    fun `non-json is reported`() {
        SimpleWiktionaryResponse.wikitext("<html>Service unavailable</html>")
    }

    @Test(expected = MalformedResponseException::class)
    fun `a parse section without text is reported`() {
        SimpleWiktionaryResponse.wikitext("""{"parse":{"title":"x"}}""")
    }

    @Test
    fun `wiktionary definitions become groups with plain text`() {
        val body = """
            {"en":[
              {"partOfSpeech":"Adjective","language":"English","definitions":[
                {"definition":"Giving a <a href=\"/wiki/sign\">sign</a> of something bad.","examples":["<i>The sky looked</i> grim."]},
                {"definition":"","examples":[]},
                {"definition":"Main sense.\n Sub-sense that should be dropped."}
              ]},
              {"partOfSpeech":"Symbol","language":"English","definitions":[{"definition":"A code."}]}
            ],
             "fr":[{"partOfSpeech":"Noun","definitions":[{"definition":"Un mot."}]}]}
        """.trimIndent()
        val groups = WiktionaryResponse.groups(body)
        assertEquals(1, groups.size)
        assertEquals("adjective", groups[0].partOfSpeech)
        assertEquals(listOf("Giving a sign of something bad.", "Main sense."), groups[0].senses.map { it.definition })
        assertEquals(listOf("The sky looked grim."), groups[0].senses[0].examples)
    }

    @Test
    fun `wiktionary with no english entry gives nothing`() {
        assertTrue(WiktionaryResponse.groups("""{"fr":[]}""").isEmpty())
    }

    @Test
    fun `datamuse keeps everyday words only`() {
        val body = """
            [{"word":"sinister","score":9,"tags":["f:2.86"]},
             {"word":"minacious","score":8,"tags":["f:0.0004"]},
             {"word":"Sample","score":7,"tags":["f:50"]},
             {"word":"menacing","score":6,"tags":["f:1.47"]},
             {"word":"untagged","score":5}]
        """.trimIndent()
        assertEquals(listOf("sinister", "menacing"), DatamuseResponse.commonWords(body, exclude = "sample", limit = 5))
        assertEquals(listOf("sinister"), DatamuseResponse.commonWords(body, exclude = "sample", limit = 1))
    }

    @Test(expected = MalformedResponseException::class)
    fun `datamuse must be a list`() {
        DatamuseResponse.commonWords("""{"oops":true}""", exclude = "x", limit = 3)
    }
}
