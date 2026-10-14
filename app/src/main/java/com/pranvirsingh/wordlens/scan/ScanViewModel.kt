package com.pranvirsingh.wordlens.scan

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pranvirsingh.wordlens.dictionary.DictionaryRepository
import com.pranvirsingh.wordlens.dictionary.LookupResult
import com.pranvirsingh.wordlens.speech.Pronouncer
import com.pranvirsingh.wordlens.text.Box
import com.pranvirsingh.wordlens.text.DwellTracker
import com.pranvirsingh.wordlens.text.LookupQuery
import com.pranvirsingh.wordlens.text.LookupQueryBuilder
import com.pranvirsingh.wordlens.text.OcrFrame
import com.pranvirsingh.wordlens.text.OcrWord
import com.pranvirsingh.wordlens.text.SentenceExtractor
import com.pranvirsingh.wordlens.text.WordPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    data class Loading(val word: String) : MeaningCard
    data class Found(
        val id: Long,
        val key: String,
        val word: String,
        val result: LookupResult,
        val sentence: String?,
    ) : MeaningCard
    data class NotFound(val key: String, val word: String) : MeaningCard
    data class Failed(val word: String) : MeaningCard
}

data class ScanUiState(
    val words: List<Box> = emptyList(),
    val target: LensTarget? = null,
    val card: MeaningCard = MeaningCard.Hidden,
    val noTextHint: Boolean = false,
    val canSpeak: Boolean = false,
)

class ScanViewModel(
    private val dictionary: DictionaryRepository,
    private val pronouncer: Pronouncer,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private val dwell = DwellTracker(dwellMillis = ScanLayout.DWELL_MILLIS, graceMillis = GRACE_MILLIS)
    private val pendingFrame = AtomicReference<OcrFrame?>(null)
    private var lastFrame = OcrFrame.EMPTY
    private var viewport = Viewport(0, 0, 1f)
    private var lastTextSeenAt = SystemClock.uptimeMillis()
    private var lookupJob: Job? = null
    private var lookupCount = 0L

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

        if (dwellState.fire && word != null && query != null) lookUp(frame, word, query)
    }

    fun onTap(x: Float, y: Float) {
        val frame = lastFrame
        val word = WordPicker.pick(frame, x, y, maxDistance = TAP_REACH_DP * viewport.density) ?: return
        val query = LookupQueryBuilder.build(frame, word) ?: return
        dwell.holdCurrent()
        lookUp(frame, word, query, force = true)
    }

    fun dismissCard() {
        lookupJob?.cancel()
        _state.update { it.copy(card = MeaningCard.Hidden) }
    }

    fun speak(text: String) = pronouncer.speak(text)

    private fun lookUp(frame: OcrFrame, word: OcrWord, query: LookupQuery, force: Boolean = false) {
        val alreadyShowing = when (val current = _state.value.card) {
            is MeaningCard.Found -> current.key == query.key
            is MeaningCard.NotFound -> current.key == query.key
            else -> false
        }
        if (alreadyShowing && !force) return

        val sentence = SentenceExtractor.sentenceAround(frame, word)
        lookupJob?.cancel()
        _state.update { it.copy(card = MeaningCard.Loading(query.display)) }
        lookupJob = viewModelScope.launch {
            val card = try {
                val result = dictionary.lookup(query.candidates)
                if (result == null) {
                    MeaningCard.NotFound(query.key, query.display)
                } else {
                    // A line-broken compound ("well-" / "known") may only match with its
                    // hyphen; show the spelling that actually matched in that case.
                    val word = if (result.query == query.key) query.display else result.query
                    MeaningCard.Found(++lookupCount, query.key, word, result, sentence)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Dictionary lookup failed", e)
                MeaningCard.Failed(query.display)
            }
            _state.update { it.copy(card = card) }
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
        const val LENS_REACH_DP = 36f
        const val TAP_REACH_DP = 24f
    }
}
