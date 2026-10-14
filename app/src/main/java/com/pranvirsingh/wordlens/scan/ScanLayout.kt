package com.pranvirsingh.wordlens.scan

/** Where the lens sits on screen, and how long it takes to "lock on" to a word. */
object ScanLayout {
    /**
     * The lens sits a little above centre so the meaning card, which rises from
     * the bottom, never covers the word being read.
     */
    const val LENS_Y_FRACTION = 0.36f

    /** How long the lens must rest on a word before its meaning appears. */
    const val DWELL_MILLIS = 650L

    fun lensPoint(width: Int, height: Int): Pair<Float, Float> =
        width / 2f to height * LENS_Y_FRACTION
}
