package com.pranvirsingh.wordlens

import android.app.Application
import android.util.Log
import com.pranvirsingh.wordlens.dictionary.DictionaryDatabase
import com.pranvirsingh.wordlens.dictionary.DictionaryRepository
import com.pranvirsingh.wordlens.meaning.MeaningLookup
import com.pranvirsingh.wordlens.online.OnlineDictionary
import com.pranvirsingh.wordlens.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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

    val settings = AppSettings(application)

    private val dictionary = DictionaryRepository(openLexicon = dictionaryDatabase::lexicon)

    // Tight timeouts: a slow answer is no use to someone mid-sentence — the
    // offline meaning is already on screen, so give up quickly and keep it.
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private val onlineDictionary = OnlineDictionary(
        client = httpClient,
        userAgent = "WordLens/${BuildConfig.VERSION_NAME} (https://github.com/pranvirsingh/WordLens)",
    )

    val meanings = MeaningLookup(
        dictionary = dictionary,
        online = onlineDictionary,
        onlineEnabled = { settings.onlineMeanings.value },
    )

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
