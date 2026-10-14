package com.pranvirsingh.wordlens.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pranvirsingh.wordlens.R
import com.pranvirsingh.wordlens.dictionary.Entry
import com.pranvirsingh.wordlens.dictionary.PartOfSpeech
import com.pranvirsingh.wordlens.dictionary.Sense
import com.pranvirsingh.wordlens.ui.theme.Highlighter
import com.pranvirsingh.wordlens.ui.theme.PartOfSpeechStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val COLLAPSED_SENSES = 3
private const val MAX_SENSES_PER_ENTRY = 8

/**
 * The meaning card. Rises from the bottom when a word is looked up and stays
 * out of the lens's way; drag it down or tap close to put it away.
 */
@Composable
fun MeaningSheet(
    card: MeaningCard,
    canSpeak: Boolean,
    maxHeight: Dp,
    onSpeak: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the last real card around so it can animate out after being hidden.
    // A plain holder, not Compose state: it only changes when `card` does,
    // which already recomposes this function.
    val lastShown = remember { LastShown() }
    if (card != MeaningCard.Hidden) lastShown.card = card

    AnimatedVisibility(
        visible = card != MeaningCard.Hidden,
        modifier = modifier,
        enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
    ) {
        lastShown.card?.let { SheetSurface(it, canSpeak, maxHeight, onSpeak, onDismiss) }
    }
}

private class LastShown {
    var card: MeaningCard? = null
}

@Composable
private fun SheetSurface(
    card: MeaningCard,
    canSpeak: Boolean,
    maxHeight: Dp,
    onSpeak: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val dismissDistance = with(LocalDensity.current) { 96.dp.toPx() }

    Surface(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .offset { IntOffset(0, dragOffset.value.roundToInt()) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            // The handle and header are the drag area; the meanings below scroll.
            Column(
                Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (dragOffset.value > dismissDistance) {
                                    // Leave the offset as is: the sheet slides out from where the
                                    // finger let go, and comes back fresh (offset 0) next time.
                                    onDismiss()
                                } else {
                                    dragOffset.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = { scope.launch { dragOffset.animateTo(0f) } },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { dragOffset.snapTo((dragOffset.value + amount).coerceAtLeast(0f)) }
                    }
                },
            ) {
                DragHandle()
                SheetHeader(card, canSpeak, onSpeak, onDismiss)
            }
            when (card) {
                is MeaningCard.Found -> FoundBody(card)
                is MeaningCard.Loading -> LoadingBody()
                is MeaningCard.NotFound -> MessageBody(stringResource(R.string.meaning_not_found_hint))
                is MeaningCard.Failed -> MessageBody(stringResource(R.string.meaning_failed))
                MeaningCard.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun DragHandle() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline),
        )
    }
}

@Composable
private fun SheetHeader(
    card: MeaningCard,
    canSpeak: Boolean,
    onSpeak: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val headword = when (card) {
        is MeaningCard.Found -> card.result.entries.first().lemma
        is MeaningCard.Loading -> card.word
        is MeaningCard.NotFound -> card.word
        is MeaningCard.Failed -> card.word
        MeaningCard.Hidden -> ""
    }
    Row(
        Modifier.padding(start = 24.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = headword,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (card is MeaningCard.Found && !card.word.equals(headword, ignoreCase = true)) {
                Text(
                    text = stringResource(R.string.meaning_form_of, card.word, headword),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (card is MeaningCard.Found && canSpeak) {
            IconButton(onClick = { onSpeak(headword) }) {
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = stringResource(R.string.action_pronounce, headword),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FoundBody(card: MeaningCard.Found) {
    var expanded by remember(card.id) { mutableStateOf(false) }
    val entries = card.result.entries
    val primary = entries.first()
    val hiddenCount = (primary.senses.size.coerceAtMost(MAX_SENSES_PER_ENTRY) - COLLAPSED_SENSES).coerceAtLeast(0) +
        entries.drop(1).sumOf { it.senses.size.coerceAtMost(MAX_SENSES_PER_ENTRY) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .animateContentSize()
            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        card.sentence?.let { PageQuote(it) }
        EntryBlock(
            entry = primary,
            limit = if (expanded) MAX_SENSES_PER_ENTRY else COLLAPSED_SENSES,
            showLemma = false,
        )
        if (expanded) {
            entries.drop(1).forEach { entry ->
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                EntryBlock(entry = entry, limit = MAX_SENSES_PER_ENTRY, showLemma = entry.lemma != primary.lemma)
            }
        }
        if (hiddenCount > 0) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    if (expanded) {
                        stringResource(R.string.meaning_show_fewer)
                    } else {
                        pluralStringResource(R.plurals.meaning_show_more, hiddenCount, hiddenCount)
                    },
                )
                Spacer(Modifier.width(4.dp))
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }
        }
    }
}

/** The sentence from the page, so the reader can see which word was read. */
@Composable
private fun PageQuote(sentence: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(40.dp)
                .clip(CircleShape)
                .background(Highlighter),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = sentence,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EntryBlock(entry: Entry, limit: Int, showLemma: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showLemma) {
            Text(
                text = entry.lemma,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = stringResource(entry.pos.labelRes()).uppercase(),
            style = PartOfSpeechStyle,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entry.senses.take(limit).forEachIndexed { index, sense -> SenseRow(index + 1, sense) }
    }
}

@Composable
private fun SenseRow(number: Int, sense: Sense) {
    Row {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = sense.definition.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            sense.examples.firstOrNull()?.let { example ->
                Text(
                    text = "“$example”",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (sense.synonyms.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.meaning_similar, sense.synonyms.joinToString(", ")),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    val pulse by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        Modifier
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
            .alpha(pulse),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(0.35f, 0.95f, 0.8f).forEach { fraction ->
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun MessageBody(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
    )
}

private fun PartOfSpeech.labelRes(): Int = when (this) {
    PartOfSpeech.NOUN -> R.string.pos_noun
    PartOfSpeech.VERB -> R.string.pos_verb
    PartOfSpeech.ADJECTIVE -> R.string.pos_adjective
    PartOfSpeech.ADVERB -> R.string.pos_adverb
}
