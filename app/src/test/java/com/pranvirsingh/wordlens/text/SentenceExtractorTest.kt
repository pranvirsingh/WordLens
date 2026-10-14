package com.pranvirsingh.wordlens.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceExtractorTest {

    @Test
    fun `finds the sentence in the middle of a paragraph`() {
        val frame = page("He ran. The night was ominous and still. She waited.")
        assertEquals(
            "The night was ominous and still.",
            SentenceExtractor.sentenceAround(frame, frame.word("ominous")),
        )
    }

    @Test
    fun `joins a sentence that spans several lines`() {
        val frame = page("It was late. The\nharbour lay\nquiet tonight. Done.")
        assertEquals(
            "The harbour lay quiet tonight.",
            SentenceExtractor.sentenceAround(frame, frame.word("lay")),
        )
    }

    @Test
    fun `rejoins words broken across lines`() {
        val frame = page("A truly won-\nderful day.")
        assertEquals("A truly wonderful day.", SentenceExtractor.sentenceAround(frame, frame.word("truly")))
    }

    @Test
    fun `keeps the dash or slash between words the recogniser split apart`() {
        val words = listOf(
            OcrWord("Hope", Box(0f, 0f, 40f, 20f), 0, 0, 0, joinedBy = '—'),
            OcrWord("despite", Box(50f, 0f, 120f, 20f), 0, 0, 1),
            OcrWord("and", Box(130f, 0f, 160f, 20f), 0, 0, 2, joinedBy = '/'),
            OcrWord("or.", Box(170f, 0f, 200f, 20f), 0, 0, 3),
        )
        val frame = OcrFrame(listOf(OcrBlock(listOf(OcrLine(words)))))
        assertEquals("Hope—despite and/or.", SentenceExtractor.sentenceAround(frame, words[1]))
    }

    @Test
    fun `keeps closing quotes with the sentence`() {
        val frame = page("“Leave now!” he said. Fine.")
        assertEquals("“Leave now!”", SentenceExtractor.sentenceAround(frame, frame.word("now!”")))
    }

    @Test
    fun `the word itself can end the sentence`() {
        val frame = page("First one. Then the end. After.")
        assertEquals("Then the end.", SentenceExtractor.sentenceAround(frame, frame.word("end.")))
    }

    @Test
    fun `text without punctuation returns the whole block`() {
        val frame = page("no punctuation here at all")
        assertEquals("no punctuation here at all", SentenceExtractor.sentenceAround(frame, frame.word("here")))
    }

    @Test
    fun `stays inside the word's own paragraph`() {
        val frame = page("Heading", "Body text starts here")
        assertEquals("Heading", SentenceExtractor.sentenceAround(frame, frame.word("Heading")))
    }

    @Test
    fun `very long sentences are trimmed around the word`() {
        val filler = (1..80).joinToString(" ") { "filler" }
        val frame = page("$filler target $filler.")
        val sentence = SentenceExtractor.sentenceAround(frame, frame.word("target"))!!
        assertTrue(sentence.length <= SentenceExtractor.MAX_LENGTH + 2)
        assertTrue("target" in sentence)
        assertTrue(sentence.startsWith("…"))
        assertTrue(sentence.endsWith("…"))
    }

    @Test
    fun `a word from another frame gives no sentence`() {
        val frame = page("one two")
        val stranger = OcrWord("x", Box(0f, 0f, 1f, 1f), blockIndex = 5, lineIndex = 0, wordIndex = 0)
        assertNull(SentenceExtractor.sentenceAround(frame, stranger))
    }
}
