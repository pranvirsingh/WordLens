package com.pranvirsingh.wordlens.text

/** Chooses which recognised word a point on screen (the lens, or a tap) is aiming at. */
object WordPicker {

    /**
     * Returns the lookup-worthy word at ([x], [y]). A word whose box contains
     * the point wins (with a little forgiveness around the edges, since a hand
     * holding a phone is never perfectly still); otherwise the nearest word
     * within [maxDistance] pixels; otherwise null.
     */
    fun pick(frame: OcrFrame, x: Float, y: Float, maxDistance: Float): OcrWord? {
        val candidates = frame.words.filter { WordNormalizer.normalize(it.raw) != null }
        if (candidates.isEmpty()) return null

        val containing = candidates.filter {
            it.box.contains(x, y, padX = it.box.height * 0.15f, padY = it.box.height * 0.25f)
        }
        if (containing.isNotEmpty()) {
            return containing.minBy { it.box.distanceTo(x, y) + centerDistance(it.box, x, y) * 0.01f }
        }

        val nearest = candidates.minBy { it.box.distanceTo(x, y) }
        return nearest.takeIf { it.box.distanceTo(x, y) <= maxDistance }
    }

    private fun centerDistance(box: Box, x: Float, y: Float): Float {
        val dx = box.centerX - x
        val dy = box.centerY - y
        return dx * dx + dy * dy
    }
}
