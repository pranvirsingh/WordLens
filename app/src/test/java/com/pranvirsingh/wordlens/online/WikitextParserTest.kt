package com.pranvirsingh.wordlens.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikitextParserTest {

    private val page = """
        === Pronunciation ===
        * {{IPA|/ˈsæmpəl/}}
        * {{audio|en-us-sample.ogg|Audio (US)}}

        == Verb ==
        {{verb}}
        # If you '''sample''' something, you [[try]] a [[small]] part of it. {{syn|try|taste}} {{ant|ignore}}
        #: ''She '''sampled''' the soup.''
        #: ''Can I [[sample|sample]] the cake?''
        #* 1900, An Old Book, ''quotation that should be skipped''
        # {{transitive}} If you '''sample''' music, you [[use]] part of it in a new song.<ref>A source</ref>

        === Related words ===
        * [[sampler]]

        == Noun ==
        {{noun}}
        # {{context|science}} A '''sample''' is a small [[amount]] of something that shows what it is like.
        #: ''The doctor took a blood '''sample'''.''
        # <!-- empty definition -->

        === Synonyms ===
        * [[example]], [[specimen]]

        === Antonyms ===
        * [[whole]]
    """.trimIndent()

    private val parsed = WikitextParser.parse(page)

    @Test
    fun `reads each part of speech in order`() {
        assertEquals(listOf("verb", "noun"), parsed.groups.map { it.partOfSpeech })
    }

    @Test
    fun `definitions become plain sentences`() {
        val verb = parsed.groups[0]
        assertEquals("If you sample something, you try a small part of it.", verb.senses[0].definition)
        assertEquals("If you sample music, you use part of it in a new song.", verb.senses[1].definition)
    }

    @Test
    fun `examples attach to their own definition and quotations are skipped`() {
        val verb = parsed.groups[0]
        assertEquals(listOf("She sampled the soup.", "Can I sample the cake?"), verb.senses[0].examples)
        assertTrue(verb.senses[1].examples.isEmpty())
    }

    @Test
    fun `topic labels are kept but grammar labels are not`() {
        assertEquals("science", parsed.groups[1].senses[0].label)
        assertNull(parsed.groups[0].senses[1].label)
    }

    @Test
    fun `empty definitions are dropped`() {
        assertEquals(1, parsed.groups[1].senses.size)
    }

    @Test
    fun `synonyms and antonyms come from templates and sections`() {
        assertEquals(listOf("try", "taste", "example", "specimen"), parsed.synonyms)
        assertEquals(listOf("ignore", "whole"), parsed.antonyms)
    }

    @Test
    fun `pronunciation is the IPA text`() {
        assertEquals("/ˈsæmpəl/", parsed.pronunciation)
    }

    @Test
    fun `a page with no recognised word class yields no meanings`() {
        val result = WikitextParser.parse("== Etymology ==\nFrom an old word.\n=== Related words ===\n* [[thing]]")
        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun `cleaning removes links, markup, entities and odd spacing`() {
        assertEquals(
            "A big dog & a cat (pets).",
            WikitextParser.cleanText("A [[large|big]] '''dog''' &amp; a [[cat]] ( [[Category:Pets]]pets ) ."),
        )
        assertEquals("Plain", WikitextParser.cleanText("{{lb|en|informal}} Plain {{unknown|x|{{nested}}}}"))
    }

    @Test
    fun `nested example markers are not left in the example`() {
        val result = WikitextParser.parse("== Verb ==\n# To go.\n#:: ''I went home.''\n#:* ''We go too.''")
        assertEquals(listOf("I went home.", "We go too."), result.groups.single().senses.single().examples)
    }

    @Test
    fun `broken markup does not throw`() {
        val result = WikitextParser.parse("== Noun ==\n# A {{broken template [[and link\n#: ''unclosed")
        assertEquals(1, result.groups.size)
    }
}
