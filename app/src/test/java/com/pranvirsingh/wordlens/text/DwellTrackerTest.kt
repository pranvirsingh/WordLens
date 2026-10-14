package com.pranvirsingh.wordlens.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DwellTrackerTest {

    private val tracker = DwellTracker(dwellMillis = 600, graceMillis = 300)

    @Test
    fun `fires once the lens has rested long enough`() {
        assertFalse(tracker.update("word", 0).fire)
        assertFalse(tracker.update("word", 599).fire)
        val state = tracker.update("word", 600)
        assertTrue(state.fire)
        assertTrue(state.done)
    }

    @Test
    fun `fires only once while the lens stays on the word`() {
        tracker.update("word", 0)
        assertTrue(tracker.update("word", 700).fire)
        assertFalse(tracker.update("word", 1_400).fire)
        assertTrue(tracker.update("word", 5_000).done)
    }

    @Test
    fun `moving to another word restarts the timer`() {
        tracker.update("first", 0)
        tracker.update("second", 500)
        assertFalse(tracker.update("second", 900).fire)
        assertTrue(tracker.update("second", 1_100).fire)
    }

    @Test
    fun `a word missing for one frame does not reset the timer`() {
        tracker.update("word", 0)
        val gap = tracker.update(null, 250)
        assertEquals("word", gap.key)
        assertTrue(tracker.update("word", 600).fire)
    }

    @Test
    fun `a word missing for longer than the grace period resets`() {
        tracker.update("word", 0)
        assertNull(tracker.update(null, 400).key)
        assertFalse(tracker.update("word", 600).fire)
        assertTrue(tracker.update("word", 1_200).fire)
    }

    @Test
    fun `leaving the text re-arms a word that already fired`() {
        tracker.update("word", 0)
        tracker.update("word", 600)
        tracker.update(null, 1_000)
        tracker.update("word", 1_100)
        assertTrue(tracker.update("word", 1_700).fire)
    }

    @Test
    fun `brushing past a neighbouring word does not re-arm a word that fired`() {
        tracker.update("first", 0)
        assertTrue(tracker.update("first", 600).fire)
        tracker.update("second", 700)
        assertFalse(tracker.update("first", 800).fire)
        assertFalse(tracker.update("first", 2_000).fire)
        assertTrue(tracker.update("first", 2_000).done)
    }

    @Test
    fun `a different word firing re-arms the first one`() {
        tracker.update("first", 0)
        tracker.update("first", 600)
        tracker.update("second", 700)
        assertTrue(tracker.update("second", 1_300).fire)
        tracker.update("first", 1_400)
        assertTrue(tracker.update("first", 2_000).fire)
    }

    @Test
    fun `holding the lens word stops it replacing a tapped meaning`() {
        tracker.update("under-lens", 0)
        tracker.holdCurrent()
        assertFalse(tracker.update("under-lens", 700).fire)
        assertFalse(tracker.update("under-lens", 5_000).fire)
    }

    @Test
    fun `holding after a word already fired keeps it quiet too`() {
        tracker.update("first", 0)
        tracker.update("first", 600)
        tracker.update("second", 650)
        tracker.holdCurrent()
        assertFalse(tracker.update("second", 2_000).fire)
    }

    @Test
    fun `holding with nothing under the lens is harmless`() {
        tracker.holdCurrent()
        tracker.update("word", 0)
        assertTrue(tracker.update("word", 600).fire)
    }

    @Test
    fun `progress grows from zero to one`() {
        val start = tracker.update("word", 1_000)
        assertEquals(0f, start.progress(1_000, 600), 0.001f)
        assertEquals(0.5f, start.progress(1_300, 600), 0.001f)
        assertEquals(1f, start.progress(9_999, 600), 0.001f)
        assertEquals(0f, tracker.update(null, 99_999).progress(99_999, 600), 0.001f)
    }
}
