package com.pranvirsingh.wordlens.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordPickerTest {

    // "The quick brown" → The: x 0-30, quick: 40-90, brown: 100-150; y 0-20
    // "fox jumped over" → fox: 0-30, jumped: 40-100, over: 110-150; y 30-50
    private val frame = page("The quick brown\nfox jumped over")

    @Test
    fun `picks the word under the point`() {
        assertEquals("quick", WordPicker.pick(frame, 60f, 10f, maxDistance = 20f)?.raw)
        assertEquals("jumped", WordPicker.pick(frame, 70f, 40f, maxDistance = 20f)?.raw)
    }

    @Test
    fun `forgives a point just outside the word`() {
        // 2px left of "quick" and 3px above the line.
        assertEquals("quick", WordPicker.pick(frame, 38f, -3f, maxDistance = 0f)?.raw)
    }

    @Test
    fun `falls back to the nearest word within reach`() {
        assertEquals("brown", WordPicker.pick(frame, 165f, 10f, maxDistance = 20f)?.raw)
    }

    @Test
    fun `prefers a word on the same line over one on the next line`() {
        // Halfway between the lines horizontally inside "quick"/"jumped" columns,
        // slightly nearer the top line.
        assertEquals("quick", WordPicker.pick(frame, 60f, 24f, maxDistance = 30f)?.raw)
    }

    @Test
    fun `returns null when nothing is within reach`() {
        assertNull(WordPicker.pick(frame, 400f, 400f, maxDistance = 20f))
    }

    @Test
    fun `returns null for an empty frame`() {
        assertNull(WordPicker.pick(OcrFrame.EMPTY, 0f, 0f, maxDistance = 100f))
    }

    @Test
    fun `skips numbers and symbols`() {
        val numbers = page("Chapter 12 —")
        // Pointing right at "12" lands on the nearest real word instead.
        assertEquals("Chapter", WordPicker.pick(numbers, 85f, 10f, maxDistance = 30f)?.raw)
        assertNull(WordPicker.pick(page("12 —"), 10f, 10f, maxDistance = 30f))
    }
}
