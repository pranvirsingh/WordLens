package com.pranvirsingh.wordlens.text

import org.junit.Assert.assertEquals
import org.junit.Test

class CompoundSplitterTest {

    private val box = Box(0f, 0f, 120f, 20f)

    @Test
    fun `leaves ordinary words alone`() {
        assertEquals(listOf(CompoundSplitter.Piece("word", box)), CompoundSplitter.split("word", box))
        assertEquals(listOf(CompoundSplitter.Piece("well-known", box)), CompoundSplitter.split("well-known", box))
    }

    @Test
    fun `splits a dash-joined pair with proportional boxes`() {
        // "hope—spring" is 11 characters: "hope" gets 4/11 of the width, "spring" the last 6/11.
        val pieces = CompoundSplitter.split("hope—spring", box)
        assertEquals(listOf("hope", "spring"), pieces.map { it.text })
        assertEquals(0f, pieces[0].box.left, 0.01f)
        assertEquals(120f * 4 / 11, pieces[0].box.right, 0.01f)
        assertEquals(120f * 5 / 11, pieces[1].box.left, 0.01f)
        assertEquals(120f, pieces[1].box.right, 0.01f)
        assertEquals(listOf('—', null), pieces.map { it.joinedBy })
    }

    @Test
    fun `splits slashes and en dashes`() {
        assertEquals(listOf("and", "or"), CompoundSplitter.split("and/or", box).map { it.text })
        assertEquals(listOf("north", "south"), CompoundSplitter.split("north–south", box).map { it.text })
    }

    @Test
    fun `leaves a trailing or leading dash attached`() {
        assertEquals(listOf("won–"), CompoundSplitter.split("won–", box).map { it.text })
        assertEquals(listOf("—and"), CompoundSplitter.split("—and", box).map { it.text })
    }
}
