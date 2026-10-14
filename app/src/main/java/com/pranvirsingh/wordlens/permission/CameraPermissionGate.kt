package com.pranvirsingh.wordlens.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pranvirsingh.wordlens.R
import com.pranvirsingh.wordlens.ui.theme.Highlighter
import com.pranvirsingh.wordlens.ui.theme.SystemBarIcons

/** Shows [content] once the camera is allowed; until then, a short welcome that asks for it. */
@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var deniedForGood by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        // Once Android stops offering the system prompt, only Settings can grant it.
        deniedForGood = !ok && context.findActivity()?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
    }

    // The reader may have allowed it in Settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = context.hasCameraPermission()
    }

    if (granted) {
        content()
    } else {
        WelcomeScreen(
            deniedForGood = deniedForGood,
            onAllow = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { context.openAppSettings() },
        )
    }
}

@Composable
private fun WelcomeScreen(
    deniedForGood: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SystemBarIcons(dark = colors.background.luminance() > 0.5f)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PageDemo(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(1.25f),
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(if (deniedForGood) R.string.welcome_denied else R.string.welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = if (deniedForGood) onOpenSettings else onAllow,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(stringResource(if (deniedForGood) R.string.action_open_settings else R.string.action_allow_camera))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.welcome_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A little animated page: the lens rests on a word and the highlighter sweeps
 * across it — the whole app explained in one loop.
 */
@Composable
private fun PageDemo(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val sweep by rememberInfiniteTransition(label = "demo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val page = colors.surface
    val ink = colors.onSurfaceVariant.copy(alpha = 0.35f)
    val border = colors.outlineVariant

    Canvas(modifier) {
        val pad = size.width * 0.08f
        drawRoundRect(page, cornerRadius = CornerRadius(20.dp.toPx()))
        drawRoundRect(border, cornerRadius = CornerRadius(20.dp.toPx()), style = Stroke(1.dp.toPx()))

        // Rows of "words" as rounded bars; row 2, word 2 is the one being read.
        val rows = listOf(
            listOf(0.22f, 0.3f, 0.18f, 0.2f),
            listOf(0.16f, 0.34f, 0.2f, 0.18f),
            listOf(0.28f, 0.14f, 0.26f, 0.22f),
            listOf(0.2f, 0.24f, 0.3f),
        )
        val lineHeight = (size.height - pad * 2) / (rows.size + 0.5f)
        val barHeight = lineHeight * 0.28f
        val gap = size.width * 0.03f
        var target = Offset.Zero
        var targetSize = Size.Zero
        rows.forEachIndexed { row, widths ->
            var x = pad
            val y = pad + lineHeight * (row + 0.75f)
            widths.forEachIndexed { index, fraction ->
                val w = (size.width - pad * 2 - gap * (widths.size - 1)) * fraction / widths.sum() *
                    (if (row == rows.lastIndex) 0.8f else 1f)
                val topLeft = Offset(x, y - barHeight / 2)
                if (row == 1 && index == 1) {
                    target = topLeft
                    targetSize = Size(w, barHeight)
                }
                drawRoundRect(ink, topLeft, Size(w, barHeight), CornerRadius(barHeight / 2))
                x += w + gap
            }
        }

        // Highlighter sweeping across the target word.
        val progress = (sweep / 0.75f).coerceAtMost(1f)
        val glow = Size(targetSize.width + 12.dp.toPx(), targetSize.height * 2.6f)
        val glowTopLeft = Offset(target.x - 6.dp.toPx(), target.y - targetSize.height * 0.8f)
        clipRect(right = glowTopLeft.x + glow.width * progress) {
            drawRoundRect(Highlighter.copy(alpha = 0.6f), glowTopLeft, glow, CornerRadius(6.dp.toPx()))
        }
        drawRoundRect(colors.onSurface.copy(alpha = 0.7f), target, targetSize, CornerRadius(barHeight / 2))

        // The lens ring with its progress arc.
        val center = Offset(target.x + targetSize.width / 2, target.y + targetSize.height / 2)
        val radius = lineHeight * 0.62f
        drawCircle(colors.primary, radius, center, style = Stroke(2.dp.toPx()))
        drawArc(
            color = Highlighter,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(4.dp.toPx()),
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
