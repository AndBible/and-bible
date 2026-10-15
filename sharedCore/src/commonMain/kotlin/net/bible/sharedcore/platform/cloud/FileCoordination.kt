package net.bible.sharedcore.platform.cloud

import okio.Path

/** Platform file coordination (iOS: NSFileCoordinator + on-demand download). Pass-through elsewhere. */
interface FileCoordination {
    fun <T> read(path: Path, block: (Path) -> T): T
    fun <T> write(path: Path, block: (Path) -> T): T
    /** Makes a cloud-only file local; throws [CloudTimeout] when it does not arrive in [timeoutMs]. */
    suspend fun ensureDownloaded(path: Path, timeoutMs: Long)

    object PassThrough : FileCoordination {
        override fun <T> read(path: Path, block: (Path) -> T) = block(path)
        override fun <T> write(path: Path, block: (Path) -> T) = block(path)
        override suspend fun ensureDownloaded(path: Path, timeoutMs: Long) = Unit
    }
}

class CloudTimeout(message: String) : okio.IOException(message)
