package com.pranvirsingh.wordlens.update

import com.pranvirsingh.wordlens.util.getText
import com.pranvirsingh.wordlens.util.isTrue
import com.pranvirsingh.wordlens.util.stringField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/** A newer published version of the app, ready to download. */
data class AvailableUpdate(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkBytes: Long,
)

/** Where the latest release comes from (GitHub in the app, a fake in tests). */
fun interface ReleaseSource {
    /** The latest release, or null when there isn't one with an APK attached. Throws on network trouble. */
    suspend fun latestRelease(): AvailableUpdate?
}

/**
 * Asks GitHub for the latest published WordLens release. It's an ordinary web
 * request (like opening a web page); nothing about the reader is included.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val latestReleaseUrl: HttpUrl = DEFAULT_URL.toHttpUrl(),
) : ReleaseSource {

    override suspend fun latestRelease(): AvailableUpdate? =
        client.getText(latestReleaseUrl, userAgent, accept = "application/vnd.github+json")?.let(::parse)

    companion object {
        const val DEFAULT_URL = "https://api.github.com/repos/pranvirsingh/WordLens/releases/latest"

        /** Where release pages and their downloads legitimately live. */
        private val trustedHosts = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )

        private val heading = Regex("""^#{1,6}\s+""")
        private val bullet = Regex("""^[-*+]\s+""")
        private val rule = Regex("""^([-*_]\s*){3,}$""")
        private val link = Regex("""\[([^\]]*)]\([^)]*\)""")
        private val extraBlankLines = Regex("""\n{3,}""")

        /** Only https links on GitHub are ever opened from an update notice. */
        fun isTrustedLink(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            return parsed.isHttps && parsed.host in trustedHosts
        }

        /** Reads GitHub's release JSON; null if it has no APK, isn't the expected shape, or links off GitHub. */
        fun parse(body: String): AvailableUpdate? {
            val root = try {
                Json.parseToJsonElement(body) as? JsonObject
            } catch (_: IllegalArgumentException) {
                null
            } ?: return null
            if (root.isTrue("draft") || root.isTrue("prerelease")) return null
            val tag = root.stringField("tag_name") ?: return null
            val apk = (root["assets"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.stringField("name")?.endsWith(".apk", ignoreCase = true) == true }
                ?: return null
            val pageUrl = root.stringField("html_url")?.takeIf(::isTrustedLink) ?: return null
            val apkUrl = apk.stringField("browser_download_url")?.takeIf(::isTrustedLink) ?: return null
            return AvailableUpdate(
                version = tag.removePrefix("v"),
                notes = cleanNotes(root.stringField("body").orEmpty()),
                pageUrl = pageUrl,
                apkUrl = apkUrl,
                apkBytes = (apk["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
            )
        }

        /** Release notes are written in Markdown; show them as plain lines. */
        fun cleanNotes(markdown: String): String =
            markdown.lines()
                .map { it.trim() }
                .filterNot { it.matches(rule) }
                .map { line ->
                    line
                        .replace(heading, "")
                        .replace(bullet, "• ")
                        .replace(link, "$1")
                        .replace("**", "")
                        .replace("`", "")
                }
                .filterNot { it.startsWith("Install:", ignoreCase = true) }
                .joinToString("\n")
                .replace(extraBlankLines, "\n\n")
                .trim()
    }
}

/** Semantic version comparison ("1.10.0" is newer than "1.9.2"). */
object VersionName {
    /** True when [candidate] is a strictly newer version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate) ?: return false
        val b = parts(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** "v1.2.0-debug" → [1, 2, 0]; null when it isn't a version at all. */
    private fun parts(version: String): List<Int>? {
        val core = version.trim().removePrefix("v").substringBefore('-').substringBefore('+')
        val numbers = core.split('.').map { it.toIntOrNull() ?: return null }
        return numbers.takeIf { it.isNotEmpty() }
    }
}
