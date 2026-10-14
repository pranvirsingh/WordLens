package com.pranvirsingh.wordlens.scan

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pranvirsingh.wordlens.R
import com.pranvirsingh.wordlens.ui.theme.Highlighter
import com.pranvirsingh.wordlens.ui.theme.SystemBarIcons

@Composable
fun ScanScreen(viewModel: ScanViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current.density
    val hasTorch = remember { backCameraHasTorch(context) }
    var torchOn by rememberSaveable { mutableStateOf(false) }

    SystemBarIcons(dark = false)

    // Readers hold the phone still for long stretches; don't let the screen sleep mid-page.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Saved across rotation/theme changes so the tick plays once per lookup, not per screen rebuild.
    val foundId = (state.card as? MeaningCard.Found)?.id
    var lastTickedId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(foundId) {
        if (foundId != null && foundId != lastTickedId) {
            lastTickedId = foundId
            val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.CONTEXT_CLICK
            }
            view.performHapticFeedback(feedback)
        }
    }

    BackHandler(enabled = state.card != MeaningCard.Hidden) { viewModel.dismissCard() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewModel.onViewportChanged(it.width, it.height, density) },
    ) {
        CameraPreview(
            torchOn = torchOn,
            onTorchChanged = { torchOn = it },
            onFrame = viewModel::onFrame,
            onTap = viewModel::onTap,
            modifier = Modifier.fillMaxSize(),
        )
        LensOverlay(
            words = state.words,
            target = state.target,
            modifier = Modifier.fillMaxSize(),
        )
        TopBar(
            hasTorch = hasTorch,
            torchOn = torchOn,
            onToggleTorch = { torchOn = !torchOn },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        AnimatedVisibility(
            visible = state.card == MeaningCard.Hidden,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
        ) {
            Hint(
                text = when {
                    state.noTextHint && hasTorch && !torchOn -> stringResource(R.string.hint_no_text_light)
                    state.noTextHint -> stringResource(R.string.hint_no_text)
                    state.target?.done == true -> stringResource(R.string.hint_tap_again)
                    state.target != null -> stringResource(R.string.hint_hold_still)
                    else -> stringResource(R.string.hint_aim)
                },
            )
        }
        MeaningSheet(
            card = state.card,
            canSpeak = state.canSpeak,
            // Never cover the lens: the card stops just below it.
            maxHeight = maxHeight * (1f - ScanLayout.LENS_Y_FRACTION) - 56.dp,
            onSpeak = viewModel::speak,
            onDismiss = viewModel::dismissCard,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopBar(
    hasTorch: Boolean,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Wordmark()
        if (hasTorch) {
            IconButton(
                onClick = onToggleTorch,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (torchOn) Highlighter else Color.Black.copy(alpha = 0.45f),
                    contentColor = if (torchOn) Color.Black else Color.White,
                ),
            ) {
                Icon(
                    if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    contentDescription = stringResource(
                        if (torchOn) R.string.action_light_off else R.string.action_light_on,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Wordmark() {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Highlighter),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun Hint(text: String) {
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "hint",
    ) { message ->
        Text(
            text = message,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
