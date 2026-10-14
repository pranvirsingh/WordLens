package com.pranvirsingh.wordlens.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Says a word out loud using the phone's built-in text-to-speech voice. */
class Pronouncer(context: Context) : TextToSpeech.OnInitListener {

    private val _available = MutableStateFlow(false)

    /** False until the voice is ready, and stays false on phones without an English voice. */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private var shutDown = false
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (shutDown || status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.US)
        _available.value = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun speak(text: String) {
        if (!_available.value || shutDown) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun shutdown() {
        shutDown = true
        _available.value = false
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val UTTERANCE_ID = "wordlens-word"
    }
}
