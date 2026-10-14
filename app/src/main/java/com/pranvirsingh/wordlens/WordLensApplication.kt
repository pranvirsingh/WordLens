package com.pranvirsingh.wordlens

import android.app.Application
import android.util.Log
import com.pranvirsingh.wordlens.dictionary.DictionaryDatabase
import com.pranvirsingh.wordlens.dictionary.DictionaryRepository
import com.pranvirsingh.wordlens.meaning.MeaningLookup
import com.pranvirsingh.wordlens.online.OnlineDictionary
import com.pranvirsingh.wordlens.settings.AppSettings
import com.pranvirsingh.wordlens.update.SharedPrefsUpdateStore
import com.pranvirsingh.wordlens.update.UpdateChecker
import com.pranvirsingh.wordlens.update.UpdateManager
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

    private val userAgent = "WordLens/${BuildConfig.VERSION_NAME} (https://github.com/pranvirsingh/WordLens)"

    private val onlineDictionary = OnlineDictionary(client = httpClient, userAgent = userAgent)

    // Update checks run in the background with no reader waiting, so they can be patient.
    private val updateClient = httpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    val updates = UpdateManager(
        store = SharedPrefsUpdateStore(application),
        checker = UpdateChecker(client = updateClient, userAgent = userAgent),
        currentVersion = BuildConfig.VERSION_NAME,
        // Development builds are a separate app that release APKs can't update.
        enabled = { !BuildConfig.DEBUG && settings.updateChecks.value },
    )

    /** See whether a newer version has been released (rate-limited inside UpdateManager). */
    fun checkForUpdates() {
        appScope.launch { updates.refresh() }
    }

    fun setUpdateChecks(enabled: Boolean) {
        settings.setUpdateChecks(enabled)
        updates.onEnabledChanged()
        if (enabled) checkForUpdates()
    }

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
