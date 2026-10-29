/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedcore.platform.desktop

import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstanceLockTest {
    private fun tempDir(): Path = Files.createTempDirectory("instance-lock-test")

    @Test fun firstLockSucceeds() {
        val dir = tempDir()
        val lock = InstanceLock.acquire(dir).getOrThrow()
        try {
            assertTrue(Files.exists(dir.resolve(InstanceLock.LOCK_FILE_NAME)))
        } finally {
            lock.close()
        }
    }

    @Test fun secondLockAttemptFails() {
        val dir = tempDir()
        val lock = InstanceLock.acquire(dir).getOrThrow()
        try {
            // FileLock is per process, so the second attempt has to come from a child JVM.
            val javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString()
            val process = ProcessBuilder(
                javaBin,
                "-cp", System.getProperty("java.class.path"),
                "net.bible.sharedcore.platform.desktop.InstanceLockProbeKt",
                dir.toString(),
            ).redirectErrorStream(true).start()
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(finished, "probe JVM did not finish within 30 s")
            assertEquals(PROBE_EXIT_ALREADY_RUNNING, process.exitValue(), "probe output: $output")
            assertContains(output, "AlreadyRunningException")
        } finally {
            lock.close()
        }
    }

    @Test fun lockReleasedOnClose() {
        val dir = tempDir()
        InstanceLock.acquire(dir).getOrThrow().close()
        // Same JVM: a lock still held would surface as OverlappingFileLockException, i.e. AlreadyRunningException.
        InstanceLock.acquire(dir).getOrThrow().close()
    }

    @Test fun sameJvmSecondAcquireIsAlreadyRunning() {
        val dir = tempDir()
        val lock = InstanceLock.acquire(dir).getOrThrow()
        try {
            val failure = InstanceLock.acquire(dir).exceptionOrNull()
            assertTrue(failure is AlreadyRunningException, "expected AlreadyRunningException, got $failure")
        } finally {
            lock.close()
        }
    }

    @Test fun unwritableDirFailsNamingPath() {
        val dir = tempDir()
        val dirFile = dir.toFile()
        assertTrue(dirFile.setWritable(false, false), "could not make $dir read-only")
        try {
            // Root ignores permission bits; the directory is then still writable and the test cannot say anything.
            assumeTrue("directory still writable (running as root?)", !Files.isWritable(dir))
            val target = dir.resolve("data")
            val failure = InstanceLock.acquire(target).exceptionOrNull()
            assertTrue(failure is DataDirUnwritableException, "expected DataDirUnwritableException, got $failure")
            assertContains(failure.message.orEmpty(), target.toString())
        } finally {
            dirFile.setWritable(true, false)
        }
    }
}
