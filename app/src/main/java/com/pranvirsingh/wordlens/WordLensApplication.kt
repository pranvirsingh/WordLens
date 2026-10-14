package com.pranvirsingh.wordlens

import android.app.Application
import android.util.Log
import com.pranvirsingh.wordlens.dictionary.DictionaryDatabase
import com.pranvirsingh.wordlens.dictionary.DictionaryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WordLensApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.prepareDictionary()
    }
}

/** App-wide singletons, created once and shared by every screen. */
class AppContainer(application: Application) {
    private val dictionaryDatabase = DictionaryDatabase(application)
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val dictionary = DictionaryRepository(openLexicon = dictionaryDatabase::lexicon)

    /**
     * Unpacks the dictionary in the background as soon as the app starts, so
     * the very first word looked up after installing (or updating) doesn't
     * wait for it. A failure here is retried by the first real lookup.
     */
    fun prepareDictionary() {
        appScope.launch {
            try {
                dictionaryDatabase.lexicon()
            } catch (e: Exception) {
                Log.w("AppContainer", "Dictionary warm-up failed; will retry on first lookup", e)
            }
        }
    }
}
