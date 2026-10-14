package com.pranvirsingh.wordlens.meaning

import org.junit.Assert.assertEquals
import org.junit.Test

class RelatedWordsTest {

    @Test
    fun `keeps order, drops the word itself and repeats in any case`() {
        assertEquals(
            listOf("piece", "bit", "part"),
            RelatedWords.merge("sample", 5, listOf("piece", "Sample", "bit"), listOf("PIECE", "part")),
        )
    }

    @Test
    fun `spelling variants one letter apart count as the same word`() {
        assertEquals(listOf("hesitant", "loath"), RelatedWords.merge("x", 5, listOf("hesitant", "loath", "loth")))
        assertEquals(listOf("color"), RelatedWords.merge("x", 5, listOf("color", "colour")))
    }

    @Test
    fun `short or genuinely different words are kept`() {
        assertEquals(listOf("hot", "hit"), RelatedWords.merge("x", 5, listOf("hot", "hit")))
        assertEquals(listOf("calm", "clam", "quiet"), RelatedWords.merge("x", 5, listOf("calm", "clam", "quiet")))
    }

    @Test
    fun `stops at the limit`() {
        assertEquals(listOf("a1", "b2"), RelatedWords.merge("x", 2, listOf("a1", "b2", "c3")))
    }
}
