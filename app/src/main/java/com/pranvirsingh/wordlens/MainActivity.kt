package com.pranvirsingh.wordlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pranvirsingh.wordlens.permission.CameraPermissionGate
import com.pranvirsingh.wordlens.scan.ScanScreen
import com.pranvirsingh.wordlens.scan.ScanViewModel
import com.pranvirsingh.wordlens.settings.ThemeMode
import com.pranvirsingh.wordlens.speech.Pronouncer
import com.pranvirsingh.wordlens.ui.theme.WordLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as WordLensApplication).container
        // Debug builds only: `adb shell am start -n <app>/.MainActivity --es lookup <word>`
        // opens a word's card directly, so the card can be checked without a book in view.
        val debugWord = if (BuildConfig.DEBUG) intent.getStringExtra(EXTRA_DEBUG_LOOKUP) else null
        val scanFactory = viewModelFactory {
            initializer { ScanViewModel(container.meanings, Pronouncer(applicationContext)) }
        }
        setContent {
            val themeMode by container.settings.themeMode.collectAsStateWithLifecycle()
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            WordLensTheme(darkTheme = dark) {
                CameraPermissionGate {
                    ScanScreen(
                        viewModel = viewModel(factory = scanFactory),
                        settings = container.settings,
                        updates = container.updates,
                        onUpdateChecksChanged = container::setUpdateChecks,
                        debugWord = debugWord,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Each time the app comes to the front; UpdateManager decides whether a check is due.
        (application as WordLensApplication).container.checkForUpdates()
    }

    private companion object {
        const val EXTRA_DEBUG_LOOKUP = "lookup"
    }
}
