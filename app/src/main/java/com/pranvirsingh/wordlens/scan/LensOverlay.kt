package com.pranvirsingh.wordlens.scan

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.pranvirsingh.wordlens.text.Box
import com.pranvirsingh.wordlens.text.DwellTracker
import com.pranvirsingh.wordlens.ui.theme.Highlighter
import androidx.compose.ui.unit.dp

/**
 * Draws what the lens sees on top of the camera: a faint underline under
 * every word it can read, the lens ring itself, and a highlighter stroke
 * that sweeps across the targeted word as the lens holds still — when the
 * stroke reaches the end, the meaning appears.
 */
@Composable
fun LensOverlay(
    words: List<Box>,
    target: LensTarget?,
    modifier: Modifier = Modifier,
) {
    // Recognition runs a few times a second; glide the highlight between
    // frames instead of letting it jump.
    val highlight = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val targetBox = target?.box
    LaunchedEffect(targetBox) {
        if (targetBox == null) return@LaunchedEffect
        val rect = Rect(targetBox.left, targetBox.top, targetBox.right, targetBox.bottom)
        if (highlight.value == Rect.Zero || !highlight.value.overlaps(rect.inflate(rect.height * 2))) {
            highlight.snapTo(rect)
        } else {
            highlight.animateTo(rect, tween(durationMillis = 140))
        }
    }
    val highlightAlpha by animateFloatAsState(
        targetValue = if (target != null) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "highlightAlpha",
    )

    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(target?.startedAt, target?.done) {
        if (target == null || target.done) return@LaunchedEffect
        while (true) {
            withFrameMillis { now = SystemClock.uptimeMillis() }
        }
    }
    val progress = target?.let {
        DwellTracker.progress(it.startedAt, it.done, now, ScanLayout.DWELL_MILLIS)
    } ?: 0f

    Canvas(modifier) {
        drawTopScrim()
        words.forEach { drawWordUnderline(it) }
        if (highlightAlpha > 0f && highlight.value != Rect.Zero) {
            drawHighlighter(highlight.value, progress, highlightAlpha)
        }
        val (x, y) = ScanLayout.lensPoint(size.width.toInt(), size.height.toInt())
        drawLens(Offset(x, y), progress, active = target != null)
    }
}

private fun DrawScope.drawTopScrim() {
    val height = 140.dp.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.55f),
            1f to Color.Transparent,
            endY = height,
        ),
        size = Size(size.width, height),
    )
}

private fun DrawScope.drawWordUnderline(box: Box) {
    val y = box.bottom + 2.dp.toPx()
    drawLine(
        color = Color.White.copy(alpha = 0.45f),
        start = Offset(box.left, y),
        end = Offset(box.right, y),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawHighlighter(word: Rect, progress: Float, alpha: Float) {
    val padX = 4.dp.toPx()
    val padY = word.height * 0.12f
    val area = Rect(word.left - padX, word.top - padY, word.right + padX, word.bottom + padY)
    val corner = CornerRadius(4.dp.toPx())

    // The full word, faintly, so the reader sees which word is being aimed at...
    drawRoundRect(
        color = Highlighter.copy(alpha = 0.22f * alpha),
        topLeft = area.topLeft,
        size = area.size,
        cornerRadius = corner,
    )
    // ...and the stroke sweeping across it as the lens holds still.
    clipRect(right = area.left + area.width * progress) {
        drawRoundRect(
            color = Highlighter.copy(alpha = 0.5f * alpha),
            topLeft = area.topLeft,
            size = area.size,
            cornerRadius = corner,
        )
    }
}

private fun DrawScope.drawLens(center: Offset, progress: Float, active: Boolean) {
    val radius = 22.dp.toPx()
    // A soft dark halo keeps the white ring visible on a bright white page.
    drawCircle(Color.Black.copy(alpha = 0.28f), radius, center, style = Stroke(width = 5.dp.toPx()))
    drawCircle(Color.White.copy(alpha = if (active) 1f else 0.85f), radius, center, style = Stroke(width = 2.dp.toPx()))
    if (progress > 0f) {
        drawArc(
            color = Highlighter,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
    drawCircle(Color.Black.copy(alpha = 0.3f), 3.5.dp.toPx(), center)
    drawCircle(if (active) Highlighter else Color.White, 2.5.dp.toPx(), center)
}
