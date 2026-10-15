@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package net.bible.sharedcore.platform.cloud

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.bible.sharedcore.log.Log
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.*
import kotlin.time.TimeSource

/** Synchronous coordinators/accessors must be called off Main; accessors must never suspend. */
class IosFileCoordination : FileCoordination {
    override fun <T> read(path: Path, block: (Path) -> T): T = coordinate(path, null, block)
    override fun <T> write(path: Path, operation: FileCoordination.Mutation, block: (Path) -> T): T =
        coordinate(path, operation, block)

    private fun <T> coordinate(path: Path, operation: FileCoordination.Mutation?, block: (Path) -> T): T = memScoped {
        val url = NSURL.fileURLWithPath(path.toString())
        val error = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
        var outcome: Result<T>? = null
        // Never unwind a Kotlin exception across Foundation's Objective-C accessor boundary.
        val accessor: (NSURL?) -> Unit = { coordinated ->
            outcome = runCatching {
                val local = coordinated?.path ?: throw IOException("Coordinator returned no file path")
                block(local.toPath())
            }
        }
        val coordinator = NSFileCoordinator(filePresenter = null)
        if (operation != null) {
            val options = when (operation) {
                FileCoordination.Mutation.REPLACE -> NSFileCoordinatorWritingForReplacing
                FileCoordination.Mutation.DELETE -> NSFileCoordinatorWritingForDeleting
                FileCoordination.Mutation.CREATE, FileCoordination.Mutation.UPDATE -> 0uL
            }
            coordinator.coordinateWritingItemAtURL(url, options, error.ptr, accessor)
        } else {
            coordinator.coordinateReadingItemAtURL(url, 0u, error.ptr, accessor)
        }
        val completed = outcome
        // The accessor failure is primary, even if Foundation also reports an error.
        completed?.exceptionOrNull()?.let { failure ->
            error.value?.let { failure.addSuppressed(it.asFileException()) }
            throw failure
        }
        error.value?.let { throw it.asFileException() }
        if (completed == null) throw IOException("File coordinator did not invoke accessor")
        completed.getOrThrow()
    }

    override suspend fun ensureDownloaded(path: Path, timeoutMs: Long) = withContext(Dispatchers.IO) {
        val url = NSURL.fileURLWithPath(path.toString())
        val ubiquitous = memScoped {
            val value = alloc<ObjCObjectVar<Any?>>().apply { this.value = null }
            val error = alloc<ObjCObjectVar<NSError?>>().apply { this.value = null }
            val success = url.getResourceValue(value.ptr, NSURLIsUbiquitousItemKey, error.ptr)
            error.value?.let { throw it.asFileException() }
            if (!success || value.value == null) throw IOException("iCloud item status unavailable")
            (value.value as? NSNumber)?.boolValue
                ?: throw IOException("Invalid iCloud item status")
        }
        if (!ubiquitous) return@withContext
        fun isCurrent(): Boolean = memScoped {
            // NSURL caches resource values; every poll must observe the download's latest state.
            url.removeCachedResourceValueForKey(NSURLUbiquitousItemDownloadingStatusKey)
            val value = alloc<ObjCObjectVar<Any?>>().apply { this.value = null }
            val error = alloc<ObjCObjectVar<NSError?>>().apply { this.value = null }
            val success = url.getResourceValue(value.ptr, NSURLUbiquitousItemDownloadingStatusKey, error.ptr)
            error.value?.let { throw it.asFileException() }
            if (!success || value.value == null) throw IOException("iCloud download status unavailable")
            value.value == NSURLUbiquitousItemDownloadingStatusCurrent
        }
        if (isCurrent()) return@withContext
        val started = TimeSource.Monotonic.markNow()
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
            val success = NSFileManager.defaultManager.startDownloadingUbiquitousItemAtURL(url, error.ptr)
            error.value?.let { throw it.asFileException() }
            if (!success) throw IOException("iCloud download could not be started")
        }
        while (!isCurrent()) {
            val remaining = timeoutMs - started.elapsedNow().inWholeMilliseconds
            if (remaining <= 0) throw CloudTimeout("iCloud download timed out: $path")
            delay(minOf(250L, remaining))
        }
    }

    override fun hasConflicts(path: Path): Boolean =
        NSFileVersion.unresolvedConflictVersionsOfItemAtURL(NSURL.fileURLWithPath(path.toString())).orEmpty().isNotEmpty()

    override fun discardOtherVersions(path: Path) {
        val url = NSURL.fileURLWithPath(path.toString())
        val conflicts = NSFileVersion.unresolvedConflictVersionsOfItemAtURL(url).orEmpty()
        conflicts.forEach { (it as NSFileVersion).resolved = true }
        Log.w("IosFileCoordination", "Resolved ${conflicts.size} iCloud conflict versions; kept current version")
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
            val success = NSFileVersion.removeOtherVersionsOfItemAtURL(url, error.ptr)
            if (!success || error.value != null) {
                Log.w("IosFileCoordination", "Conflict version cleanup failed: ${error.value?.localizedDescription ?: "unknown error"}")
            }
        }
    }
}

/** Only Foundation/POSIX missing-file errors satisfy CloudFileStore's missing-item contract. */
internal fun NSError.asFileException(): IOException =
    if ((domain == NSCocoaErrorDomain && (code == NSFileNoSuchFileError || code == NSFileReadNoSuchFileError)) ||
        (domain == NSPOSIXErrorDomain && code == platform.posix.ENOENT.toLong())) {
        okio.FileNotFoundException(localizedDescription)
    } else IOException("$domain ($code): $localizedDescription")
