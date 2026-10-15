@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedcore.platform.cloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import net.bible.sharedcore.log.Log
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.timeIntervalSince1970

/** Resolves the account's container once off Main; null from [root] may still mean initializing. */
class IosUbiquityContainer(
    containerId: String = NSBundle.mainBundle.objectForInfoDictionaryKey("AndBibleICloudContainer") as? String
        ?: "iCloud.org.andbible.ios",
) {
    private val resolution = CoroutineScope(SupervisorJob() + Dispatchers.IO).async(start = CoroutineStart.LAZY) {
        try {
            NSFileManager.defaultManager.URLForUbiquityContainerIdentifier(containerId)?.path?.let {
                (it.toPath() / "Documents" / "AndBibleSync").also(FileSystem.SYSTEM::createDirectories)
            }
        } catch (error: Exception) {
            Log.w("IosUbiquityContainer", "Container initialization failed", error)
            null
        }
    }

    fun prefetch() { resolution.start() }

    /** Nonblocking cached snapshot. Use [awaitReady] before treating null as unavailable. */
    fun root(): Path? {
        prefetch()
        return if (resolution.isCompleted) resolution.getCompleted() else null
    }

    /** Starts initialization and awaits its actual completion, without blocking Main. */
    suspend fun awaitReady(): Path? = resolution.await()
}

/** FileTreeCloudStore performs synchronous IO; consumers must call its operations off Main. */
fun iCloudDriveStore(container: IosUbiquityContainer = IosUbiquityContainer()): CloudFileStore =
    FileTreeCloudStore(FileSystem.SYSTEM, container::root, IosFileCoordination(),
        now = { (NSDate().timeIntervalSince1970 * 1000).toLong() })
