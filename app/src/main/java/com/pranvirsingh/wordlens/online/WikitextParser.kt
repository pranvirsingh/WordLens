package com.pranvirsingh.wordlens.online

import com.pranvirsingh.wordlens.meaning.MeaningGroup
import com.pranvirsingh.wordlens.meaning.MeaningSense

/**
 * Reads a Simple English Wiktionary page (raw wikitext) into plain meanings.
 *
 * The page is a list of headings ("== Verb ==") followed by numbered
 * definitions ("# If you run, you go quickly…"), their examples ("#: I like to
 * run."), and optional synonym/antonym lists — either as their own
 * "=== Synonyms ===" sections or inline as {{syn|…}} / {{ant|…}} templates.
 */
object WikitextParser {

    data class Parsed(
        val pronunciation: String?,
        val groups: List<MeaningGroup>,
        val synonyms: List<String>,
        val antonyms: List<String>,
    )

    private const val MAX_EXAMPLES = 3

    private val heading = Regex("""^(=+)\s*(.+?)\s*\1\s*$""")
    private val partsOfSpeech = setOf(
        "noun", "proper noun", "verb", "phrasal verb", "adjective", "adverb", "pronoun",
        "preposition", "conjunction", "interjection", "determiner", "article",
        "phrase", "idiom", "contraction", "abbreviation", "numeral", "prefix", "suffix",
    )

    // Templates that only describe grammar; nothing a reader needs to see.
    private val grammarTemplates = setOf(
        "transitive", "intransitive", "countable", "uncountable", "plural", "singular",
        "auxiliary", "ergative", "comparable", "uncomparable", "notcomparable",
    )
    private val labelTemplates = setOf("context", "label", "lb", "lbl", "term-context", "qualifier", "q", "i")

    private enum class Section { NONE, MEANINGS, SYNONYMS, ANTONYMS, PRONUNCIATION, OTHER }

    fun parse(wikitext: String): Parsed {
        val groups = mutableListOf<MeaningGroup>()
        val synonyms = LinkedHashSet<String>()
        val antonyms = LinkedHashSet<String>()
        var pronunciation: String? = null

        var section = Section.NONE
        var currentPos: String? = null
        var senses = mutableListOf<MeaningSense>()

        fun closeGroup() {
            val pos = currentPos
            if (pos != null && senses.isNotEmpty()) groups += MeaningGroup(pos, senses.toList())
            currentPos = null
            senses = mutableListOf()
        }

        for (rawLine in stripComments(wikitext).lines()) {
            val line = rawLine.trim()
            val headingMatch = heading.matchEntire(line)
            if (headingMatch != null) {
                val name = headingMatch.groupValues[2].lowercase()
                section = when {
                    name in partsOfSpeech -> {
                        closeGroup()
                        currentPos = name
                        Section.MEANINGS
                    }
                    name == "synonyms" -> Section.SYNONYMS
                    name == "antonyms" -> Section.ANTONYMS
                    name.startsWith("pronunciation") -> Section.PRONUNCIATION
                    else -> {
                        // "Related words", "Etymology", … end the meanings for this part of speech.
                        if (section == Section.MEANINGS) closeGroup()
                        Section.OTHER
                    }
                }
                continue
            }

            if (pronunciation == null) pronunciation = findIpa(line)

            when (section) {
                Section.MEANINGS -> when {
                    line.startsWith("#:") -> {
                        // "#:", "#::" and "#:*" all mark an example line.
                        val example = cleanText(line.trimStart('#', ':', '*'))
                        val last = senses.lastOrNull()
                        if (example.isNotEmpty() && last != null && last.examples.size < MAX_EXAMPLES) {
                            senses[senses.lastIndex] = last.copy(examples = last.examples + example)
                        }
                    }
                    line.startsWith("#*") || line.startsWith("##") -> Unit // quotations / sub-senses
                    line.startsWith("#") -> {
                        val body = line.removePrefix("#")
                        templateArgs(body, "syn", "synonyms").forEach { synonyms += it }
                        templateArgs(body, "ant", "antonyms").forEach { antonyms += it }
                        val label = firstLabel(body)
                        val definition = cleanText(body)
                        if (definition.isNotEmpty()) senses += MeaningSense(definition, label = label)
                    }
                }
                Section.SYNONYMS -> if (line.startsWith("*")) wordsInList(line).forEach { synonyms += it }
                Section.ANTONYMS -> if (line.startsWith("*")) wordsInList(line).forEach { antonyms += it }
                else -> Unit
            }
        }
        closeGroup()
        return Parsed(pronunciation, groups, synonyms.toList(), antonyms.toList())
    }

    /** Turns wiki markup into the plain sentence a reader sees. */
    fun cleanText(markup: String): String {
        var text = markup
        text = text.replace(Regex("""<ref[^>]*/>"""), "")
        text = text.replace(Regex("""<ref[^>]*>.*?</ref>"""), "")
        text = removeTemplates(text)
        text = text.replace(Regex("""\[\[(?:[^\]|]*\|)?([^\]]*)]]""")) { match ->
            val target = match.value.removePrefix("[[").substringBefore('|')
            if (target.contains(':')) "" else match.groupValues[1]
        }
        text = text.replace(Regex("""\[https?://\S+\s*([^\]]*)]"""), "$1")
        text = text.replace("'''", "").replace("''", "")
        text = text.replace(Regex("""<[^>]+>"""), "")
        text = HtmlText.decode(text)
        text = text.replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s+([,.;:!?])"""), "$1")
            .replace("( ", "(").replace(" )", ")")
            .replace("()", "")
        return text.trim().trim(';', ',').trim()
    }

    private fun stripComments(text: String) = text.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")

    /** Drops every {{template}}, keeping nothing — labels and syn/ant are read separately. */
    private fun removeTemplates(text: String): String {
        val out = StringBuilder()
        var depth = 0
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("{{", i) -> {
                    depth++
                    i += 2
                }
                text.startsWith("}}", i) && depth > 0 -> {
                    depth--
                    i += 2
                }
                else -> {
                    if (depth == 0) out.append(text[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    /** Arguments of every top-level {{name|a|b}} template in [text] whose name matches. */
    private fun templates(text: String): List<List<String>> {
        val found = mutableListOf<List<String>>()
        var start = text.indexOf("{{")
        while (start >= 0) {
            var depth = 0
            var end = start
            while (end < text.length) {
                if (text.startsWith("{{", end)) {
                    depth++
                    end += 2
                } else if (text.startsWith("}}", end)) {
                    depth--
                    end += 2
                    if (depth == 0) break
                } else {
                    end++
                }
            }
            if (depth != 0) break
            found += text.substring(start + 2, end - 2).split('|').map { it.trim() }
            start = text.indexOf("{{", end)
        }
        return found
    }

    private fun templateArgs(text: String, vararg names: String): List<String> =
        templates(text)
            .filter { it.firstOrNull()?.lowercase() in names }
            .flatMap { args -> args.drop(1) }
            .filterNot { it.contains('=') }
            .map { cleanText(it) }
            .filter { it.isNotEmpty() && it.length <= MAX_WORD_LENGTH }

    private fun firstLabel(text: String): String? =
        templates(text).firstNotNullOfOrNull { args ->
            val name = args.firstOrNull()?.lowercase() ?: return@firstNotNullOfOrNull null
            when {
                name in grammarTemplates -> null
                name in labelTemplates -> args.drop(1)
                    .filterNot { it.contains('=') || it == "en" || it.lowercase() in grammarTemplates }
                    .firstOrNull()?.let { cleanText(it) }?.ifEmpty { null }
                else -> null
            }
        }

    /** Words in a "* [[calm]], [[quiet]]" style list line. */
    private fun wordsInList(line: String): List<String> {
        val links = Regex("""\[\[(?:[^\]|]*\|)?([^\]]*)]]""").findAll(line).map { it.groupValues[1].trim() }.toList()
        val words = links.ifEmpty { cleanText(line.trimStart('*')).split(',') }
        return words.map { it.trim() }.filter { it.isNotEmpty() && it.length <= MAX_WORD_LENGTH && !it.contains(':') }
    }

    private fun findIpa(line: String): String? =
        templates(line)
            .filter { it.firstOrNull()?.lowercase() == "ipa" }
            .flatMap { it.drop(1) }
            .firstOrNull { it.startsWith("/") || it.startsWith("[") }

    private const val MAX_WORD_LENGTH = 40
}
