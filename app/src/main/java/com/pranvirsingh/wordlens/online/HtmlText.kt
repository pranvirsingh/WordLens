package com.pranvirsingh.wordlens.online

/** Turns the HTML entities wiki text uses (&amp;, &mdash;, &#8212;, …) into real characters. */
internal object HtmlText {
    private val named = mapOf(
        "nbsp" to " ", "amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’",
        "ldquo" to "“", "rdquo" to "”",
    )
    private val entity = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    fun decode(text: String): String = entity.replace(text) { match ->
        val name = match.groupValues[1]
        when {
            name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePoint)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
            else -> named[name]
        } ?: match.value
    }

    private fun codePoint(value: Int): String? =
        if (Character.isValidCodePoint(value)) String(Character.toChars(value)) else null
}
