package com.pranvirsingh.wordlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pranvirsingh.wordlens.permission.CameraPermissionGate
import com.pranvirsingh.wordlens.scan.ScanScreen
import com.pranvirsingh.wordlens.scan.ScanViewModel
import com.pranvirsingh.wordlens.speech.Pronouncer
import com.pranvirsingh.wordlens.ui.theme.WordLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as WordLensApplication).container
        val scanFactory = viewModelFactory {
            initializer { ScanViewModel(container.dictionary, Pronouncer(applicationContext)) }
        }
        setContent {
            WordLensTheme {
                CameraPermissionGate {
                    ScanScreen(viewModel = viewModel(factory = scanFactory))
                }
            }
        }
    }
}
