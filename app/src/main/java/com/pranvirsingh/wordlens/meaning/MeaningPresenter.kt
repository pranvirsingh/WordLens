package com.pranvirsingh.wordlens.meaning

import com.pranvirsingh.wordlens.dictionary.LookupResult
import com.pranvirsingh.wordlens.dictionary.PartOfSpeech
import com.pranvirsingh.wordlens.online.OnlineEntry

/**
 * Builds what the card shows from the two sources:
 *
 * - the offline dictionary answers instantly and always works;
 * - the online entry, when it arrives, replaces the definitions with plainer,
 *   everyday wording and adds natural examples.
 *
 * Similar and opposite words are pooled from both, everyday words first.
 */
object MeaningPresenter {

    private const val MAX_GROUPS = 4
    private const val MAX_SENSES_PER_GROUP = 6
    private const val MAX_EXAMPLES = 2
    private const val MAX_SIMILAR = 6
    private const val MAX_OPPOSITE = 4

    /** Returns null only when neither source knows anything about the word. */
    fun present(offline: LookupResult?, online: OnlineEntry?, fallbackHeadword: String): WordMeaning? {
        val headword = offline?.entries?.firstOrNull()?.lemma ?: fallbackHeadword
        val onlineGroups = online?.groups.orEmpty()
        val offlineGroups = offline?.entries.orEmpty().map { entry ->
            MeaningGroup(
                partOfSpeech = entry.pos.label,
                senses = entry.senses.map { sense ->
                    MeaningSense(definition = asSentence(sense.definition), examples = sense.examples.map(::asSentence))
                },
                otherWord = entry.lemma.takeUnless { it.equals(headword, ignoreCase = true) },
            )
        }
        val onlineSource = online?.source?.takeIf { onlineGroups.isNotEmpty() }
        // Online meanings describe the headword only. Readings that belong to a
        // different word ("saw" the tool vs. "see") stay, from the offline dictionary.
        val groups = (if (onlineSource != null) onlineGroups + offlineGroups.filter { it.otherWord != null } else offlineGroups)
            .take(MAX_GROUPS)
            .map { group ->
                group.copy(
                    senses = group.senses.take(MAX_SENSES_PER_GROUP).map { it.copy(examples = it.examples.take(MAX_EXAMPLES)) },
                )
            }
        if (groups.isEmpty()) return null

        // Offline synonyms/opposites of the most likely reading only, so they match what the card says first.
        val firstReading = offline?.entries?.firstOrNull()?.senses?.take(2).orEmpty()
        return WordMeaning(
            headword = headword,
            pronunciation = online?.pronunciation,
            groups = groups,
            similar = RelatedWords.merge(headword, MAX_SIMILAR, online?.synonyms.orEmpty(), firstReading.flatMap { it.synonyms }),
            opposite = RelatedWords.merge(headword, MAX_OPPOSITE, online?.antonyms.orEmpty(), firstReading.flatMap { it.antonyms }),
            source = onlineSource ?: MeaningSource.OFFLINE,
        )
    }

    /** "a cutting tool" → "A cutting tool." */
    fun asSentence(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        val capitalised = trimmed.replaceFirstChar { it.uppercase() }
        return if (capitalised.last() in ".!?…\"”'’)") capitalised else "$capitalised."
    }

    private val PartOfSpeech.label: String
        get() = when (this) {
            PartOfSpeech.NOUN -> "noun"
            PartOfSpeech.VERB -> "verb"
            PartOfSpeech.ADJECTIVE -> "adjective"
            PartOfSpeech.ADVERB -> "adverb"
        }
}
