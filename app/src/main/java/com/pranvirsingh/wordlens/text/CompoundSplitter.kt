package com.pranvirsingh.wordlens.text

/**
 * Splits text the recogniser returned as one word but a reader sees as two,
 * such as "hope—despite" or "and/or", so each half can be aimed at on its own.
 * Each piece gets a slice of the original box in proportion to its length.
 */
object CompoundSplitter {

    /** One piece of the original text; [joinedBy] is the separator that followed it, if any. */
    data class Piece(val text: String, val box: Box, val joinedBy: Char? = null)

    private val separators = charArrayOf('—', '–', '/')

    fun split(raw: String, box: Box): List<Piece> {
        val cuts = raw.indices.filter { i ->
            raw[i] in separators && i > 0 && i < raw.lastIndex &&
                raw[i - 1].isLetter() && raw[i + 1].isLetter()
        }
        if (cuts.isEmpty()) return listOf(Piece(raw, box))

        val length = raw.length.toFloat()
        val pieces = mutableListOf<Piece>()
        var segmentStart = 0
        for (end in cuts + raw.length) {
            if (end > segmentStart) {
                val slice = box.copy(
                    left = box.left + box.width * (segmentStart / length),
                    right = box.left + box.width * (end / length),
                )
                pieces += Piece(raw.substring(segmentStart, end), slice, raw.getOrNull(end))
            }
            segmentStart = end + 1
        }
        return pieces
    }
}
