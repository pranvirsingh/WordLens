package com.pranvirsingh.wordlens.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordNormalizerTest {

    @Test
    fun `lowercases and strips surrounding punctuation`() {
        assertEquals("serendipity", WordNormalizer.normalize("Serendipity,"))
        assertEquals("ominous", WordNormalizer.normalize("“ominous”"))
        assertEquals("end", WordNormalizer.normalize("(end)."))
    }

    @Test
    fun `removes possessive endings`() {
        assertEquals("captain", WordNormalizer.normalize("captain's"))
        assertEquals("captain", WordNormalizer.normalize("Captain’s"))
        assertEquals("sailors", WordNormalizer.normalize("sailors'"))
    }

    @Test
    fun `contractions are not mistaken for possessives`() {
        assertNull(WordNormalizer.normalize("it's"))
        assertNull(WordNormalizer.normalize("That’s"))
        assertNull(WordNormalizer.normalize("there's,"))
        assertEquals("ship", WordNormalizer.normalize("ship's"))
    }

    @Test
    fun `keeps inner hyphens and apostrophes`() {
        assertEquals("well-known", WordNormalizer.normalize("well-known"))
        assertEquals("o'clock", WordNormalizer.normalize("o’clock"))
    }

    @Test
    fun `unifies unusual hyphen characters`() {
        assertEquals("well-known", WordNormalizer.normalize("well‐known"))
    }

    @Test
    fun `rejects things that are not words`() {
        assertNull(WordNormalizer.normalize("1984"))
        assertNull(WordNormalizer.normalize("—"))
        assertNull(WordNormalizer.normalize("a"))
        assertNull(WordNormalizer.normalize(""))
        assertNull(WordNormalizer.normalize("   "))
        assertNull(WordNormalizer.normalize("a1b2c"))
        assertNull(WordNormalizer.normalize("x's"))
    }

    @Test
    fun `drops footnote numbers stuck to a word`() {
        assertEquals("ominous", WordNormalizer.normalize("ominous12"))
        assertEquals("ominous", WordNormalizer.normalize("ominous,³"))
    }

    @Test
    fun `phrases chosen on the card keep their spaces`() {
        assertEquals("ice cream", WordNormalizer.normalizePhrase("  Ice   cream "))
        assertEquals("look after", WordNormalizer.normalizePhrase("look after"))
        assertEquals("well-known", WordNormalizer.normalizePhrase("well‐known"))
        assertNull(WordNormalizer.normalizePhrase("12 34"))
        assertNull(WordNormalizer.normalizePhrase("a"))
        assertNull(WordNormalizer.normalizePhrase("odd/one"))
    }

    @Test
    fun `clean keeps original case`() {
        assertEquals("Paris", WordNormalizer.clean("Paris!"))
    }

    @Test
    fun `detects a word broken at the end of a line`() {
        assertTrue(WordNormalizer.endsWithHyphen("won-"))
        assertTrue(WordNormalizer.endsWithHyphen("won‐"))
        assertTrue(WordNormalizer.endsWithHyphen("won–"))
        assertFalse(WordNormalizer.endsWithHyphen("won"))
        assertFalse(WordNormalizer.endsWithHyphen("-"))
        assertFalse(WordNormalizer.endsWithHyphen("1-"))
    }
}
