package com.pranvirsingh.wordlens.scan

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.TorchState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pranvirsingh.wordlens.text.Box
import com.pranvirsingh.wordlens.text.CompoundSplitter
import com.pranvirsingh.wordlens.text.OcrBlock
import com.pranvirsingh.wordlens.text.OcrFrame
import com.pranvirsingh.wordlens.text.OcrLine
import com.pranvirsingh.wordlens.text.OcrWord

/**
 * Live camera preview that reads text on every frame, entirely on the phone.
 * Word positions come back already mapped into this view's own pixels, so the
 * overlay drawn on top lines up with the page exactly.
 *
 * Pinch-to-zoom and tap-to-focus stay with the camera; a tap is also passed
 * to [onTap] so the reader can pick a word directly.
 */
@Composable
fun CameraPreview(
    torchOn: Boolean,
    onTorchChanged: (Boolean) -> Unit,
    onFrame: (OcrFrame) -> Unit,
    onTap: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnTorchChanged by rememberUpdatedState(onTorchChanged)
    val currentTorchOn by rememberUpdatedState(torchOn)

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            val sameShape = AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            previewResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(sameShape)
                .build()
            // Book text is small; analyse at 720p so letters stay legible to the recogniser.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(sameShape)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    ),
                )
                .build()
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    DisposableEffect(lifecycleOwner) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        // The analyzer only hands frames to ML Kit, which recognises text on its
        // own worker threads; results come back on the main thread. Nothing here
        // owns a thread pool, so tearing down can never race a frame in flight.
        val mainExecutor = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            mainExecutor,
            MlKitAnalyzer(
                listOf(recognizer),
                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                mainExecutor,
            ) { result ->
                val text = result.getValue(recognizer)
                if (text != null) {
                    currentOnFrame(text.toOcrFrame())
                } else {
                    result.getThrowable(recognizer)?.let { Log.w(TAG, "Text recognition failed", it) }
                }
            },
        )
        controller.bindToLifecycle(lifecycleOwner)
        // The camera turns its light off whenever it closes (e.g. the app goes to
        // the background), so report the real state back instead of assuming it
        // stayed on. One exception: if the light was on when this screen was
        // rebuilt (rotation, theme change), switch it back on as soon as the new
        // camera reports in, rather than reporting it as off.
        var restoreTorch = currentTorchOn
        controller.torchState.observe(lifecycleOwner) { state ->
            if (restoreTorch && state != TorchState.ON) {
                restoreTorch = false
                controller.enableTorch(true)
            } else {
                restoreTorch = false
                currentOnTorchChanged(state == TorchState.ON)
            }
        }
        onDispose {
            controller.torchState.removeObservers(lifecycleOwner)
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            recognizer.close()
        }
    }

    LaunchedEffect(torchOn) {
        controller.enableTorch(torchOn)
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
                setOnTouchListener(tapListener(viewContext) { x, y -> currentOnTap(x, y) })
            }
        },
    )
}

/**
 * Reports single taps while leaving every touch for PreviewView's own
 * pinch-to-zoom and tap-to-focus handling (the listener never consumes).
 */
@SuppressLint("ClickableViewAccessibility")
private fun tapListener(context: Context, onTap: (Float, Float) -> Unit) =
    GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onTap(e.x, e.y)
                return false
            }
        },
    ).let { detector ->
        View.OnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

/** True when the back camera has a flash that can be used as a reading light. */
fun backCameraHasTorch(context: Context): Boolean = try {
    val manager = ContextCompat.getSystemService(context, CameraManager::class.java)
    manager?.cameraIdList?.any { id ->
        val characteristics = manager.getCameraCharacteristics(id)
        characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    } == true
} catch (e: Exception) {
    Log.w(TAG, "Couldn't check for a flash", e)
    false
}

private fun Text.toOcrFrame(): OcrFrame = OcrFrame(
    textBlocks.mapIndexed { blockIndex, block ->
        OcrBlock(
            block.lines.mapIndexed { lineIndex, line ->
                val pieces = line.elements.flatMap { element ->
                    val rect = element.boundingBox ?: return@flatMap emptyList()
                    val box = Box(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
                    CompoundSplitter.split(element.text, box)
                }
                OcrLine(
                    pieces.mapIndexed { wordIndex, piece ->
                        OcrWord(piece.text, piece.box, blockIndex, lineIndex, wordIndex, piece.joinedBy)
                    },
                )
            },
        )
    },
)

private const val TAG = "CameraPreview"
