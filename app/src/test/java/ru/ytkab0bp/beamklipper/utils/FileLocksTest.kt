package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// The pid of the JVM running the tests (/proc/self is a link to /proc/<pid>).
private fun selfPid(): Int = File("/proc/self").canonicalFile.name.toInt()

class FileLocksTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `the action runs while the file lock is held`() {
        var ran = false
        FileLocks.withFileLock(File(tmp.root, "lock")) { ran = true }
        assertTrue(ran)
        assertTrue(File(tmp.root, "lock").exists())
    }

    @Test
    fun `the file lock can be taken again afterwards`() {
        val lock = File(tmp.root, "lock")
        var n = 0
        FileLocks.withFileLock(lock) { n++ }
        FileLocks.withFileLock(lock) { n++ }
        assertEquals(2, n)
    }

    @Test
    fun `an exception in the action still releases the lock`() {
        val lock = File(tmp.root, "lock")
        try {
            FileLocks.withFileLock(lock) { throw IllegalStateException("boom") }
        } catch (_: IllegalStateException) {}
        var ran = false
        FileLocks.withFileLock(lock) { ran = true }
        assertTrue(ran)
    }

    @Test
    fun `a lock file holding a live pid is not stale`() {
        assertFalse(FileLocks.isStale("123", ageMs = 100, staleAfterMs = 10_000, alive = { true }))
    }

    @Test
    fun `a lock file holding a dead pid is stale even when young`() {
        assertTrue(FileLocks.isStale("123", ageMs = 100, staleAfterMs = 10_000, alive = { false }))
    }

    @Test
    fun `an old lock file is stale even for a live pid`() {
        assertTrue(FileLocks.isStale("123", ageMs = 20_000, staleAfterMs = 10_000, alive = { true }))
    }

    @Test
    fun `a lock file with unreadable content is stale only when old`() {
        assertFalse(FileLocks.isStale("", ageMs = 100, staleAfterMs = 10_000))
        assertTrue(FileLocks.isStale("garbage", ageMs = 20_000, staleAfterMs = 10_000))
    }

    @Test
    fun `this process is alive and a huge pid is not`() {
        val self = selfPid()
        assertTrue(FileLocks.isProcessAlive(self))
        assertFalse(FileLocks.isProcessAlive(Int.MAX_VALUE))
    }

    @Test
    fun `the port lock runs the action and removes its file`() {
        val lock = File(tmp.root, "port_lock")
        var during = false
        FileLocks.withMoonrakerPortLock(lock, pid = 1) { during = lock.exists() }
        assertTrue(during)
        assertFalse(lock.exists())
    }

    @Test
    fun `the port lock records the pid that holds it`() {
        val lock = File(tmp.root, "port_lock")
        var content = ""
        FileLocks.withMoonrakerPortLock(lock, pid = 4242) { content = lock.readText() }
        assertEquals("4242", content)
    }

    @Test
    fun `a stale port lock is taken over`() {
        val lock = File(tmp.root, "port_lock").apply { writeText("999999999"); setLastModified(0) }
        var ran = false
        FileLocks.withMoonrakerPortLock(lock, pid = 1, timeoutMs = 2000) { ran = true }
        assertTrue(ran)
        assertFalse(lock.exists())
    }

    @Test
    fun `a port lock held by a live process makes the caller wait, then proceed`() {
        val self = selfPid()
        val lock = File(tmp.root, "port_lock").apply { writeText("$self") }
        var ran = false
        val start = System.currentTimeMillis()
        FileLocks.withMoonrakerPortLock(lock, pid = 1, timeoutMs = 300, staleAfterMs = 60_000) { ran = true }
        assertTrue(ran)
        assertTrue("waited for the deadline", System.currentTimeMillis() - start >= 250)
    }

    @Test
    fun `an exception in the action still removes the port lock`() {
        val lock = File(tmp.root, "port_lock")
        try {
            FileLocks.withMoonrakerPortLock(lock, pid = 1) { throw IllegalStateException("boom") }
        } catch (_: IllegalStateException) {}
        assertFalse(lock.exists())
    }
}
