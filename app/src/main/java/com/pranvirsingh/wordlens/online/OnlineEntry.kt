package com.pranvirsingh.wordlens.online

import com.pranvirsingh.wordlens.meaning.MeaningGroup
import com.pranvirsingh.wordlens.meaning.MeaningSource

/** What the online sources know about one word. Any part may be empty. */
data class OnlineEntry(
    val source: MeaningSource?,
    val pronunciation: String?,
    val groups: List<MeaningGroup>,
    val synonyms: List<String>,
    val antonyms: List<String>,
    /** The meaning services couldn't be reached (synonyms may still have come through). */
    val meaningsUnavailable: Boolean = false,
) {
    val isEmpty: Boolean
        get() = groups.isEmpty() && synonyms.isEmpty() && antonyms.isEmpty()
}

/** The outcome of asking the online sources about a word. */
sealed interface OnlineResult {
    data class Found(val entry: OnlineEntry) : OnlineResult

    /** The sources answered, but none of them knows this word. */
    data object NotFound : OnlineResult

    /** No answer at all — offline, timed out, or the services are down. */
    data object Unavailable : OnlineResult
}
