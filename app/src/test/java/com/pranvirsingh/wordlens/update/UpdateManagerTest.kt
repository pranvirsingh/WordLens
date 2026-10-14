package com.pranvirsingh.wordlens.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UpdateManagerTest {

    private class MemoryStore : UpdateStore {
        val values = HashMap<String, Any>()
        override fun getString(key: String, default: String?) = values[key] as? String ?: default
        override fun getLong(key: String, default: Long) = values[key] as? Long ?: default
        override fun edit(block: UpdateStore.Editor.() -> Unit) {
            object : UpdateStore.Editor {
                override fun putString(key: String, value: String) { values[key] = value }
                override fun putLong(key: String, value: Long) { values[key] = value }
                override fun remove(key: String) { values.remove(key) }
            }.block()
        }
    }

    private fun release(version: String) = AvailableUpdate(
        version = version,
        notes = "Notes",
        pageUrl = "https://github.com/pranvirsingh/WordLens/releases/tag/v$version",
        apkUrl = "https://github.com/pranvirsingh/WordLens/releases/download/v$version/WordLens-v$version.apk",
        apkBytes = 1,
    )

    private val store = MemoryStore()
    private var now = 1_000_000_000L
    private var enabled = true
    private var calls = 0
    private var answer: () -> AvailableUpdate? = { release("1.3.0") }

    private val manager = UpdateManager(
        store = store,
        checker = { calls++; answer() },
        currentVersion = "1.2.0",
        enabled = { enabled },
        clock = { now },
    )

    private val hour = 60 * 60 * 1000L

    @Test
    fun `a newer release shows the banner and the menu item`() = runTest {
        manager.refresh()
        assertEquals("1.3.0", manager.available.value?.version)
        assertTrue(manager.bannerVisible.value)
    }

    @Test
    fun `the same or an older release shows nothing`() = runTest {
        answer = { release("1.2.0") }
        manager.refresh()
        assertNull(manager.available.value)
        assertFalse(manager.bannerVisible.value)
    }

    @Test
    fun `checks at most every three hours`() = runTest {
        manager.refresh()
        now += 2 * hour
        manager.refresh()
        assertEquals(1, calls)
        now += 2 * hour
        manager.refresh()
        assertEquals(2, calls)
    }

    @Test
    fun `a failed check is retried after a short wait, keeping what was known`() = runTest {
        manager.refresh()
        now += 4 * hour
        answer = { throw IOException("offline") }
        manager.refresh()
        assertEquals("1.3.0", manager.available.value?.version)
        now += 10 * 60 * 1000L
        manager.refresh()
        assertEquals(2, calls)
        now += 10 * 60 * 1000L
        manager.refresh()
        assertEquals(3, calls)
    }

    @Test
    fun `a next-check time saved under a wrong clock does not stop checks`() = runTest {
        store.values["next_check"] = now + 1_000 * hour
        manager.refresh()
        assertEquals(1, calls)
    }

    @Test
    fun `a release that disappears is no longer offered`() = runTest {
        manager.refresh()
        now += 4 * hour
        answer = { null }
        manager.refresh()
        assertNull(manager.available.value)
    }

    @Test
    fun `what was found is shown straight away next time, even before checking`() = runTest {
        manager.refresh()
        val later = UpdateManager(store, { throw IOException("offline") }, "1.2.0", { true }, { now + hour })
        later.refresh()
        assertEquals("1.3.0", later.available.value?.version)
    }

    @Test
    fun `dismissing hides the banner for that version only`() = runTest {
        manager.refresh()
        manager.dismissBanner()
        assertFalse(manager.bannerVisible.value)
        assertEquals("1.3.0", manager.available.value?.version)

        now += 4 * hour
        answer = { release("1.4.0") }
        manager.refresh()
        assertTrue(manager.bannerVisible.value)
    }

    @Test
    fun `switched off means no checks and nothing shown, even from a check already running`() = runTest {
        val gate = CompletableDeferred<Unit>()
        answer = { release("1.3.0") }
        val slow = UpdateManager(store, { gate.await(); release("1.3.0") }, "1.2.0", { enabled }, { now })
        val running = async { slow.refresh() }
        yield()
        enabled = false
        slow.onEnabledChanged()
        gate.complete(Unit)
        running.await()
        assertNull(slow.available.value)

        manager.refresh()
        assertEquals(0, calls)
    }

    @Test
    fun `only one check runs at a time`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var slowCalls = 0
        val slow = UpdateManager(store, { slowCalls++; gate.await(); release("1.3.0") }, "1.2.0", { true }, { now })
        val first = async { slow.refresh() }
        yield()
        slow.refresh()
        gate.complete(Unit)
        first.await()
        assertEquals(1, slowCalls)
    }
}
