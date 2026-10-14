package com.pranvirsingh.wordlens.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The reader's choices, saved on the phone and applied immediately. */
class AppSettings(context: Context) {

    // Getting the preferences starts loading the file on a background thread right away;
    // the values themselves are only read (by then, usually loaded) on first use.
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _themeMode by lazy {
        MutableStateFlow(
            prefs.getString(KEY_THEME, null)?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } }
                ?: ThemeMode.SYSTEM,
        )
    }
    val themeMode: StateFlow<ThemeMode> by lazy { _themeMode.asStateFlow() }

    private val _onlineMeanings by lazy { MutableStateFlow(prefs.getBoolean(KEY_ONLINE, true)) }

    /** When off, only the built-in offline dictionary is used and nothing is sent anywhere. */
    val onlineMeanings: StateFlow<Boolean> by lazy { _onlineMeanings.asStateFlow() }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }

    fun setOnlineMeanings(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ONLINE, enabled) }
        _onlineMeanings.value = enabled
    }

    private companion object {
        const val FILE = "settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_ONLINE = "online_meanings"
    }
}
