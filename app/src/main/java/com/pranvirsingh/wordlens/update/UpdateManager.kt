package com.pranvirsingh.wordlens.update

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps track of whether a newer WordLens is out, so the app can say so.
 *
 * Checks GitHub at most every few hours (sooner after a failed check),
 * remembers what it found so the notice shows instantly next launch even
 * offline, and remembers which version the reader dismissed so it doesn't nag.
 */
class UpdateManager(
    private val store: UpdateStore,
    private val checker: ReleaseSource,
    private val currentVersion: String,
    /** Whether the reader allows update checks at all (they can switch it off). */
    private val enabled: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val running = Mutex()

    private val _available = MutableStateFlow<AvailableUpdate?>(null)

    /** A newer version, if one is known (and checks are switched on). */
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    private val _bannerVisible = MutableStateFlow(false)

    /** Whether to show the "update available" banner (hidden once dismissed for that version). */
    val bannerVisible: StateFlow<Boolean> = _bannerVisible.asStateFlow()

    /**
     * Shows what's already known, then asks GitHub if it's due. Called whenever
     * the app comes to the foreground; does nothing if a check is already running.
     */
    suspend fun refresh() {
        if (!running.tryLock()) return
        try {
            refreshLocked()
        } finally {
            running.unlock()
        }
    }

    private suspend fun refreshLocked() {
        if (!enabled()) {
            publish(null)
            return
        }
        publish(loadSaved())
        val now = clock()
        val next = store.getLong(KEY_NEXT_CHECK, 0L)
        // A "next check" far in the future means the phone's clock was wrong when it was saved.
        if (now < next && next - now <= CHECK_EVERY_MILLIS) return

        try {
            val latest = checker.latestRelease()
            store.edit {
                if (latest != null) {
                    putString(KEY_VERSION, latest.version)
                    putString(KEY_NOTES, latest.notes)
                    putString(KEY_PAGE, latest.pageUrl)
                    putString(KEY_APK, latest.apkUrl)
                    putLong(KEY_BYTES, latest.apkBytes)
                    putLong(KEY_NEXT_CHECK, now + CHECK_EVERY_MILLIS)
                } else {
                    // No release with an APK: either none exists any more (don't keep advertising
                    // an old one) or the newest is still having its APK built — look again soon.
                    remove(KEY_VERSION)
                    putLong(KEY_NEXT_CHECK, now + RETRY_AFTER_MILLIS)
                }
            }
            publish(latest)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No internet, or GitHub busy: keep what we knew, try again a bit later.
            store.edit { putLong(KEY_NEXT_CHECK, now + RETRY_AFTER_MILLIS) }
            Log.i(TAG, "Update check skipped: ${e.message}")
        }
    }

    /** The reader switched update checks on or off. */
    fun onEnabledChanged() {
        if (!enabled()) publish(null) else publish(loadSaved())
    }

    /** The reader closed the banner; don't show it again for this version. */
    fun dismissBanner() {
        _available.value?.let { store.edit { putString(KEY_DISMISSED, it.version) } }
        _bannerVisible.value = false
    }

    private fun publish(update: AvailableUpdate?) {
        // Re-checked here: the reader may have switched alerts off while a check was running.
        val newer = update?.takeIf { enabled() && VersionName.isNewer(it.version, currentVersion) }
        _available.value = newer
        _bannerVisible.value = newer != null && store.getString(KEY_DISMISSED, null) != newer.version
    }

    private fun loadSaved(): AvailableUpdate? {
        val version = store.getString(KEY_VERSION, null) ?: return null
        val page = store.getString(KEY_PAGE, null)?.takeIf(UpdateChecker::isTrustedLink) ?: return null
        val apk = store.getString(KEY_APK, null)?.takeIf(UpdateChecker::isTrustedLink) ?: return null
        return AvailableUpdate(
            version = version,
            notes = store.getString(KEY_NOTES, null).orEmpty(),
            pageUrl = page,
            apkUrl = apk,
            apkBytes = store.getLong(KEY_BYTES, 0L),
        )
    }

    private companion object {
        const val TAG = "UpdateManager"
        const val CHECK_EVERY_MILLIS = 3L * 60 * 60 * 1000
        const val RETRY_AFTER_MILLIS = 15L * 60 * 1000
        const val KEY_NEXT_CHECK = "next_check"
        const val KEY_VERSION = "version"
        const val KEY_NOTES = "notes"
        const val KEY_PAGE = "page"
        const val KEY_APK = "apk"
        const val KEY_BYTES = "bytes"
        const val KEY_DISMISSED = "dismissed"
    }
}

/** Small key-value storage for what the update check remembers. */
interface UpdateStore {
    fun getString(key: String, default: String?): String?
    fun getLong(key: String, default: Long): Long
    fun edit(block: Editor.() -> Unit)

    interface Editor {
        fun putString(key: String, value: String)
        fun putLong(key: String, value: Long)
        fun remove(key: String)
    }
}
