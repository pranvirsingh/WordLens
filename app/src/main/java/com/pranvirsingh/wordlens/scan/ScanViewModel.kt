package com.pranvirsingh.wordlens.scan

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pranvirsingh.wordlens.meaning.MeaningLookup
import com.pranvirsingh.wordlens.meaning.WordMeaning
import com.pranvirsingh.wordlens.speech.Pronouncer
import com.pranvirsingh.wordlens.text.Box
import com.pranvirsingh.wordlens.text.DwellTracker
import com.pranvirsingh.wordlens.text.LookupQuery
import com.pranvirsingh.wordlens.text.LookupQueryBuilder
import com.pranvirsingh.wordlens.text.OcrFrame
import com.pranvirsingh.wordlens.text.OcrWord
import com.pranvirsingh.wordlens.text.SentenceExtractor
import com.pranvirsingh.wordlens.text.WordNormalizer
import com.pranvirsingh.wordlens.text.WordPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/** The word the lens is resting on, and how far along the "hold still" timer is. */
data class LensTarget(
    val box: Box,
    val startedAt: Long,
    val done: Boolean,
)

sealed interface MeaningCard {
    data object Hidden : MeaningCard
    data class Loading(val word: String, val canGoBack: Boolean = false) : MeaningCard
    data class Found(
        /** New for every word looked up (not for the online upgrade of the same word). */
        val id: Long,
        val candidates: List<String>,
        val word: String,
        val meaning: WordMeaning,
        val sentence: String?,
        /** The plainer online meaning is still on its way. */
        val enriching: Boolean,
        /** Online meanings are on, but couldn't be reached this time. */
        val offlineOnly: Boolean,
        val canGoBack: Boolean,
    ) : MeaningCard {
        val key: String get() = candidates.first()
    }
    data class NotFound(val key: String, val word: String, val canGoBack: Boolean = false) : MeaningCard
    data class Failed(val word: String, val canGoBack: Boolean = false) : MeaningCard
}

data class ScanUiState(
    val words: List<Box> = emptyList(),
    val target: LensTarget? = null,
    val card: MeaningCard = MeaningCard.Hidden,
    val noTextHint: Boolean = false,
    val canSpeak: Boolean = false,
)

class ScanViewModel(
    private val meanings: MeaningLookup,
    private val pronouncer: Pronouncer,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    // One event per word that lands, for the haptic tick. A one-shot event rather than
    // state, so screen rebuilds (rotation) and app restarts never replay or swallow it.
    private val _meaningLanded = Channel<Unit>(Channel.CONFLATED)
    val meaningLanded: Flow<Unit> = _meaningLanded.receiveAsFlow()

    private val dwell = DwellTracker(dwellMillis = ScanLayout.DWELL_MILLIS, graceMillis = GRACE_MILLIS)
    private val pendingFrame = AtomicReference<OcrFrame?>(null)
    private var lastFrame = OcrFrame.EMPTY
    private var viewport = Viewport(0, 0, 1f)
    private var lastTextSeenAt = SystemClock.uptimeMillis()
    private var lookupJob: Job? = null
    private var lookupCount = 0L
    private var lastPrefetched: String? = null

    /** Cards the reader came from by tapping a similar/opposite word, newest last. */
    private val history = ArrayDeque<MeaningCard.Found>()

    init {
        viewModelScope.launch {
            pronouncer.available.collect { available -> _state.update { it.copy(canSpeak = available) } }
        }
    }

    fun onViewportChanged(width: Int, height: Int, density: Float) {
        viewport = Viewport(width, height, density)
    }

    /**
     * Called for every recognised frame. Only the newest frame matters, so if
     * the previous one hasn't been handled yet, older frames are dropped
     * rather than queued up.
     */
    fun onFrame(frame: OcrFrame) {
        if (pendingFrame.getAndSet(frame) == null) {
            viewModelScope.launch { pendingFrame.getAndSet(null)?.let(::handleFrame) }
        }
    }

    private fun handleFrame(frame: OcrFrame) {
        lastFrame = frame
        val now = SystemClock.uptimeMillis()
        if (frame.words.isNotEmpty()) lastTextSeenAt = now

        val (lensX, lensY) = ScanLayout.lensPoint(viewport.width, viewport.height)
        val word = if (viewport.width > 0) {
            WordPicker.pick(frame, lensX, lensY, maxDistance = LENS_REACH_DP * viewport.density)
        } else {
            null
        }
        val query = word?.let { LookupQueryBuilder.build(frame, it) }
        val dwellState = dwell.update(query?.key, now)

        val target = when {
            dwellState.key == null -> null
            word != null && query?.key == dwellState.key ->
                LensTarget(word.box, dwellState.startedAt, dwellState.done)
            // The word blinked out for a frame; keep showing it where it was.
            else -> _state.value.target?.copy(done = dwellState.done)
        }
        _state.update {
            it.copy(
                words = frame.words.map(OcrWord::box),
                target = target,
                noTextHint = now - lastTextSeenAt > NO_TEXT_HINT_MILLIS,
            )
        }

        // The lens has settled on a word: start fetching its online meaning now, so it's
        // usually ready by the time the hold-still timer finishes.
        if (query != null && !dwellState.done && query.key == dwellState.key &&
            now - dwellState.startedAt >= PREFETCH_AFTER_MILLIS && query.key != lastPrefetched
        ) {
            lastPrefetched = query.key
            viewModelScope.launch {
                try {
                    meanings.prefetch(query.candidates)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Prefetch failed", e)
                }
            }
        }

        if (dwellState.fire && word != null && query != null) lookUpFromPage(frame, word, query, force = false)
    }

    fun onTap(x: Float, y: Float) {
        val frame = lastFrame
        val word = WordPicker.pick(frame, x, y, maxDistance = TAP_REACH_DP * viewport.density) ?: return
        val query = LookupQueryBuilder.build(frame, word) ?: return
        dwell.holdCurrent()
        lookUpFromPage(frame, word, query, force = true)
    }

    /** The reader tapped a similar/opposite word on the card. */
    fun lookUpRelated(word: String) {
        val key = WordNormalizer.normalizePhrase(word) ?: return
        (_state.value.card as? MeaningCard.Found)?.let { current ->
            history.addLast(current)
            if (history.size > MAX_HISTORY) history.removeFirst()
        }
        startLookup(LookupQuery(display = word, candidates = listOf(key)), sentence = null)
    }

    /** Back from a related word to the one before it; false when there's nothing to go back to. */
    fun goBack(): Boolean {
        val previous = history.removeLastOrNull()?.copy(canGoBack = history.isNotEmpty()) ?: return false
        if (previous.enriching) {
            // It was still waiting for its plainer online meaning when the reader moved on —
            // finish that now (usually instant: the answer has been cached meanwhile).
            startLookup(
                LookupQuery(display = previous.word, candidates = previous.candidates),
                previous.sentence,
                reuse = previous,
            )
        } else {
            lookupJob?.cancel()
            _state.update { it.copy(card = previous) }
        }
        return true
    }

    fun dismissCard() {
        lookupJob?.cancel()
        history.clear()
        _state.update { it.copy(card = MeaningCard.Hidden) }
    }

    fun speak(text: String) = pronouncer.speak(text)

    private fun lookUpFromPage(frame: OcrFrame, word: OcrWord, query: LookupQuery, force: Boolean) {
        val alreadyShowing = when (val current = _state.value.card) {
            is MeaningCard.Found -> current.key == query.key
            is MeaningCard.NotFound -> current.key == query.key
            else -> false
        }
        if (alreadyShowing && !force) return
        // While the reader is following similar/opposite words, only a deliberate tap on
        // the page takes over — not the lens drifting onto some word.
        if (history.isNotEmpty() && !force) return
        history.clear()
        startLookup(query, SentenceExtractor.sentenceAround(frame, word))
    }

    /** [reuse]: an earlier card for the same word, shown (and kept quiet) while it's refreshed. */
    private fun startLookup(query: LookupQuery, sentence: String?, reuse: MeaningCard.Found? = null) {
        lookupJob?.cancel()
        val canGoBack = history.isNotEmpty()
        val id = reuse?.id ?: ++lookupCount
        var announced = reuse != null
        _state.update { it.copy(card = reuse ?: MeaningCard.Loading(query.display, canGoBack)) }
        lookupJob = viewModelScope.launch {
            try {
                meanings.lookUp(query.candidates).collect { step ->
                    val card = when (step) {
                        is MeaningLookup.Step.Ready -> MeaningCard.Found(
                            id = id,
                            candidates = query.candidates,
                            // A line-broken compound ("well-" / "known") may only match with its
                            // hyphen; show the spelling that actually matched in that case.
                            word = if (step.matched == query.key) query.display else step.matched,
                            meaning = step.meaning,
                            sentence = sentence,
                            enriching = step.stillLoading,
                            offlineOnly = step.offlineOnly,
                            canGoBack = canGoBack,
                        )
                        MeaningLookup.Step.NotFound -> MeaningCard.NotFound(query.key, query.display, canGoBack)
                    }
                    _state.update { it.copy(card = card) }
                    if (card is MeaningCard.Found && !announced) {
                        announced = true
                        _meaningLanded.trySend(Unit)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Lookup failed", e)
                _state.update { it.copy(card = MeaningCard.Failed(query.display, canGoBack)) }
            }
        }
    }

    override fun onCleared() {
        pronouncer.shutdown()
    }

    private data class Viewport(val width: Int, val height: Int, val density: Float)

    private companion object {
        const val TAG = "ScanViewModel"
        const val GRACE_MILLIS = 400L
        const val NO_TEXT_HINT_MILLIS = 3_000L
        const val PREFETCH_AFTER_MILLIS = 200L
        const val LENS_REACH_DP = 36f
        const val TAP_REACH_DP = 24f
        const val MAX_HISTORY = 10
    }
}
