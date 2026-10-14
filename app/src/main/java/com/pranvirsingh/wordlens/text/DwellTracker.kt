package com.pranvirsingh.wordlens.text

/**
 * Decides when the lens has rested on one word long enough to look it up.
 *
 * Text recognition sometimes misses a word for a single frame, so a word
 * briefly disappearing (within [graceMillis]) doesn't restart the timer.
 *
 * Once a word fires it stays "done" until a *different* word fires or the
 * lens leaves the text entirely. Merely brushing past a neighbouring word
 * doesn't re-arm it — otherwise a slightly shaky hand would keep popping a
 * card back open right after the reader closed it. Tapping the word always
 * looks it up again.
 */
class DwellTracker(
    private val dwellMillis: Long,
    private val graceMillis: Long,
) {
    /** Snapshot after each update. [startedAt] is when the lens settled on [key]. */
    data class State(
        val key: String?,
        val startedAt: Long,
        val done: Boolean,
        val fire: Boolean,
    ) {
        fun progress(now: Long, dwellMillis: Long): Float =
            if (key == null) 0f else progress(startedAt, done, now, dwellMillis)
    }

    companion object {
        /** How far along the hold-still timer is, from 0 to 1. */
        fun progress(startedAt: Long, done: Boolean, now: Long, dwellMillis: Long): Float = when {
            done -> 1f
            else -> ((now - startedAt).toFloat() / dwellMillis).coerceIn(0f, 1f)
        }
    }

    private var candidate: String? = null
    private var startedAt = 0L
    private var lastSeenAt = 0L
    private var fired: String? = null

    fun update(key: String?, now: Long): State {
        if (key == null) {
            val current = candidate
            if (current != null && now - lastSeenAt <= graceMillis) {
                return State(current, startedAt, done = current == fired, fire = false)
            }
            candidate = null
            fired = null
            return State(null, 0L, done = false, fire = false)
        }

        if (key != candidate) {
            candidate = key
            startedAt = now
        }
        lastSeenAt = now

        if (key == fired) return State(key, startedAt, done = true, fire = false)
        if (now - startedAt >= dwellMillis) {
            fired = key
            return State(key, startedAt, done = true, fire = true)
        }
        return State(key, startedAt, done = false, fire = false)
    }

    /**
     * Stops whatever word the lens is resting on from firing (until a
     * different word fires or the lens leaves the text). Used when the reader
     * taps a word, so the word under the lens doesn't replace the tapped
     * meaning a moment later.
     */
    fun holdCurrent() {
        fired = candidate
    }
}
