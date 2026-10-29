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

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Base type of the failures [InstanceLock.acquire] reports, so `main()` can tell the cases apart. */
sealed class InstanceLockException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Another desktop process (or another lock in this JVM) already holds `<data>/instance.lock`. */
class AlreadyRunningException(val lockFile: Path) :
    InstanceLockException("AndBible is already running (lock held on $lockFile)")

/** The data directory cannot be created or written to, so no lock can be taken there. */
class DataDirUnwritableException(val dataDir: Path, cause: Throwable? = null) :
    InstanceLockException("Data directory is not writable: $dataDir", cause)

/**
 * One running desktop instance per user. [acquire] creates [dataDir] if needed, checks that it is writable and takes
 * an exclusive lock on `<dataDir>/instance.lock`. The lock lasts until the returned [AutoCloseable] is closed or the
 * process exits.
 *
 * `FileLock` is per process, so a second acquire in the same JVM fails with [OverlappingFileLockException], which is
 * reported as [AlreadyRunningException] like a lock held by another process.
 */
object InstanceLock {
    /** Name of the lock file inside the data directory. */
    const val LOCK_FILE_NAME = "instance.lock"

    /**
     * Returns the held lock, or a failure that is an [AlreadyRunningException] or a [DataDirUnwritableException].
     * Other I/O failures on the lock file are reported as [DataDirUnwritableException] too, since the lock file is
     * the only thing this method writes.
     */
    fun acquire(dataDir: Path): Result<AutoCloseable> = runCatching {
        ensureWritableDirectory(dataDir)
        val lockFile = dataDir.resolve(LOCK_FILE_NAME)
        val channel = try {
            FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        } catch (e: IOException) {
            throw DataDirUnwritableException(dataDir, e)
        }
        val lock = try {
            channel.tryLock()
        } catch (e: OverlappingFileLockException) {
            null
        } catch (e: IOException) {
            channel.close()
            throw DataDirUnwritableException(dataDir, e)
        }
        if (lock == null) {
            channel.close()
            throw AlreadyRunningException(lockFile)
        }
        AutoCloseable {
            lock.close()
            channel.close()
        }
    }

    private fun ensureWritableDirectory(dataDir: Path) {
        try {
            Files.createDirectories(dataDir)
        } catch (e: IOException) {
            throw DataDirUnwritableException(dataDir, e)
        }
        if (!Files.isDirectory(dataDir) || !Files.isWritable(dataDir)) throw DataDirUnwritableException(dataDir)
    }
}
