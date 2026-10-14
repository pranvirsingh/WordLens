package com.pranvirsingh.wordlens.online

import com.pranvirsingh.wordlens.meaning.MeaningGroup
import com.pranvirsingh.wordlens.meaning.MeaningSense
import com.pranvirsingh.wordlens.util.stringField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Thrown when a service answers with something that isn't the shape we expect. */
class MalformedResponseException(message: String) : Exception(message)

private fun parseJson(body: String) = try {
    Json.parseToJsonElement(body)
} catch (e: IllegalArgumentException) {
    throw MalformedResponseException("Not JSON: ${e.message}")
}

/** The MediaWiki "parse" API: either the page's wikitext, or "no such page". */
object SimpleWiktionaryResponse {
    /** Returns the wikitext, or null when the page doesn't exist. */
    fun wikitext(body: String): String? {
        val root = parseJson(body) as? JsonObject ?: throw MalformedResponseException("Expected an object")
        (root["error"] as? JsonObject)?.let { error ->
            if (error.stringField("code") == "missingtitle") return null
            throw MalformedResponseException("Wiki error: ${error.stringField("code")}")
        }
        val parse = root["parse"] as? JsonObject ?: throw MalformedResponseException("No 'parse' section")
        return parse.stringField("wikitext") ?: throw MalformedResponseException("No wikitext")
    }
}

/** The English Wiktionary REST "definition" endpoint (definitions as small HTML snippets). */
object WiktionaryResponse {
    private const val MAX_EXAMPLE_LENGTH = 160
    private const val MAX_EXAMPLES = 2
    private val wordClasses = setOf(
        "noun", "proper noun", "verb", "adjective", "adverb", "pronoun", "preposition",
        "conjunction", "interjection", "determiner", "article", "phrase", "idiom", "numeral",
    )

    fun groups(body: String): List<MeaningGroup> {
        val root = parseJson(body) as? JsonObject ?: throw MalformedResponseException("Expected an object")
        val english = root["en"] as? JsonArray ?: return emptyList()
        return english.mapNotNull { element ->
            val usage = element as? JsonObject ?: return@mapNotNull null
            val pos = usage.stringField("partOfSpeech")?.lowercase()?.takeIf { it in wordClasses } ?: return@mapNotNull null
            val senses = (usage["definitions"] as? JsonArray).orEmpty().mapNotNull { item ->
                val definition = item as? JsonObject ?: return@mapNotNull null
                // A definition can carry nested sub-senses after a line break; the first line is the sense.
                val text = definition.stringField("definition")?.let(::htmlToText)?.lines()?.firstOrNull()?.trim()
                if (text.isNullOrEmpty()) return@mapNotNull null
                val examples = (definition["examples"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::htmlToText)?.trim() }
                    .filter { it.isNotEmpty() && it.length <= MAX_EXAMPLE_LENGTH }
                    .take(MAX_EXAMPLES)
                MeaningSense(text, examples)
            }
            senses.takeIf { it.isNotEmpty() }?.let { MeaningGroup(pos, it) }
        }
    }

    private fun htmlToText(html: String): String =
        HtmlText.decode(html.replace(Regex("""<[^>]+>"""), ""))
            .replace(Regex("""[ \t]+"""), " ")
}

/** Datamuse word lists (synonyms / antonyms), keeping only words people actually use. */
object DatamuseResponse {
    /** Uses per million words of English; below this a "synonym" is too rare to help a reader. */
    private const val MIN_FREQUENCY = 1.0

    fun commonWords(body: String, exclude: String, limit: Int): List<String> {
        val root = parseJson(body) as? JsonArray ?: throw MalformedResponseException("Expected a list")
        return root.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val word = item.stringField("word")?.trim() ?: return@mapNotNull null
            val frequency = (item["tags"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .firstOrNull { it.startsWith("f:") }
                ?.removePrefix("f:")?.toDoubleOrNull() ?: 0.0
            word.takeIf { frequency >= MIN_FREQUENCY && !it.equals(exclude, ignoreCase = true) }
        }.distinct().take(limit)
    }
}
