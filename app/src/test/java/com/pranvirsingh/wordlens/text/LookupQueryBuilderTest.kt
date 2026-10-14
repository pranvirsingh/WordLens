package com.pranvirsingh.wordlens.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LookupQueryBuilderTest {

    @Test
    fun `a plain word looks itself up`() {
        val frame = page("It was a curious sight.")
        val query = LookupQueryBuilder.build(frame, frame.word("curious"))!!
        assertEquals(listOf("curious"), query.candidates)
        assertEquals("curious", query.display)
        assertEquals("curious", query.key)
    }

    @Test
    fun `punctuation and possessives are removed but case is kept for display`() {
        val frame = page("The Captain's log.")
        val query = LookupQueryBuilder.build(frame, frame.word("Captain's"))!!
        assertEquals(listOf("captain"), query.candidates)
        assertEquals("Captain", query.display)
    }

    @Test
    fun `rejoins a word hyphenated across lines, from either half`() {
        val frame = page("a truly won-\nderful day")
        val fromFirst = LookupQueryBuilder.build(frame, frame.word("won-"))!!
        val fromSecond = LookupQueryBuilder.build(frame, frame.word("derful"))!!
        assertEquals(listOf("wonderful", "won-derful", "won"), fromFirst.candidates)
        assertEquals(listOf("wonderful", "won-derful", "derful"), fromSecond.candidates)
        assertEquals(fromFirst.key, fromSecond.key)
        assertEquals("wonderful", fromFirst.display)
    }

    @Test
    fun `a real compound split across lines is also tried with its hyphen`() {
        val frame = page("a well-\nknown fact")
        assertEquals(
            listOf("wellknown", "well-known", "well"),
            LookupQueryBuilder.build(frame, frame.word("well-"))!!.candidates,
        )
    }

    @Test
    fun `a dash at the end of a line still lets each word be found alone`() {
        val frame = page("He paused–\nthen left")
        assertEquals("paused", LookupQueryBuilder.build(frame, frame.word("paused–"))!!.candidates.last())
        assertEquals("then", LookupQueryBuilder.build(frame, frame.word("then"))!!.candidates.last())
    }

    @Test
    fun `does not join across paragraphs`() {
        val frame = page("the end-", "derful start")
        assertEquals(listOf("end"), LookupQueryBuilder.build(frame, frame.word("end-"))!!.candidates)
        assertEquals(listOf("derful"), LookupQueryBuilder.build(frame, frame.word("derful"))!!.candidates)
    }

    @Test
    fun `a hyphenated word mid-line is tried with and without the hyphen`() {
        val frame = page("an e-mail arrived")
        assertEquals(listOf("e-mail", "email"), LookupQueryBuilder.build(frame, frame.word("e-mail"))!!.candidates)
    }

    @Test
    fun `numbers give no query`() {
        val frame = page("page 42")
        assertNull(LookupQueryBuilder.build(frame, frame.word("42")))
    }
}
