package com.pranvirsingh.wordlens.meaning

import com.pranvirsingh.wordlens.dictionary.DictionaryRepository
import com.pranvirsingh.wordlens.dictionary.LookupResult
import com.pranvirsingh.wordlens.online.OnlineResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** The online half of a lookup, kept behind an interface so it can be faked in tests. */
interface OnlineSource {
    fun prefetch(word: String)
    suspend fun lookup(word: String): OnlineResult

    /** The answer if it's already known (e.g. prefetched), without waiting. */
    fun cached(word: String): OnlineResult?
}

/**
 * Looks a word up the fast way: the offline meaning is shown the moment it's
 * found (milliseconds), then quietly upgraded when the plainer online meaning
 * arrives. If the phone is offline or online meanings are switched off, the
 * offline meaning simply stays.
 */
class MeaningLookup(
    private val dictionary: DictionaryRepository,
    private val online: OnlineSource,
    private val onlineEnabled: () -> Boolean,
) {
    sealed interface Step {
        /**
         * Something to show. [stillLoading] is true while the online upgrade is on its way;
         * [offlineOnly] is true when the online sources couldn't be reached.
         */
        data class Ready(
            val meaning: WordMeaning,
            val matched: String,
            val stillLoading: Boolean,
            val offlineOnly: Boolean = false,
        ) : Step

        data object NotFound : Step
    }

    /** Offline first, online second. A broken offline dictionary throws; online trouble never does. */
    fun lookUp(candidates: List<String>): Flow<Step> = flow {
        val offline = offline(candidates)
        val word = offline?.entries?.firstOrNull()?.lemma ?: candidates.firstOrNull() ?: run {
            emit(Step.NotFound)
            return@flow
        }
        val matched = offline?.query ?: word
        val offlineMeaning = MeaningPresenter.present(offline, null, word)

        if (!onlineEnabled()) {
            emit(offlineMeaning?.let { Step.Ready(it, matched, stillLoading = false) } ?: Step.NotFound)
            return@flow
        }
        // Usually the prefetch has already brought the online answer: then go straight to it,
        // rather than flashing the offline wording for a split second first.
        val ready = online.cached(word)
        if (ready == null && offlineMeaning != null) emit(Step.Ready(offlineMeaning, matched, stillLoading = true))

        val result = ready ?: online.lookup(word)
        val entry = (result as? OnlineResult.Found)?.entry
        val meaning = MeaningPresenter.present(offline, entry, word)
        val unreachable = result == OnlineResult.Unavailable || entry?.meaningsUnavailable == true
        emit(
            if (meaning == null) {
                Step.NotFound
            } else {
                Step.Ready(meaning, matched, stillLoading = false, offlineOnly = unreachable)
            },
        )
    }

    /**
     * Warms the online answer for a word the reader is probably about to look
     * up, so it's usually already here when the meaning card opens.
     */
    suspend fun prefetch(candidates: List<String>) {
        if (!onlineEnabled()) return
        val offline = offline(candidates)
        val word = offline?.entries?.firstOrNull()?.lemma ?: candidates.firstOrNull() ?: return
        online.prefetch(word)
    }

    // The prefetch and the real lookup a moment later ask about the same word;
    // remember the last few offline answers so the dictionary is read once.
    private val recentOffline = object : LinkedHashMap<List<String>, CachedOffline>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<String>, CachedOffline>) = size > RECENT_SIZE
    }

    private class CachedOffline(val result: LookupResult?)

    private suspend fun offline(candidates: List<String>): LookupResult? {
        synchronized(recentOffline) { recentOffline[candidates] }?.let { return it.result }
        val result = dictionary.lookup(candidates)
        synchronized(recentOffline) { recentOffline[candidates] = CachedOffline(result) }
        return result
    }

    private companion object {
        const val RECENT_SIZE = 8
    }
}
