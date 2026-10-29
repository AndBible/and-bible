@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import net.bible.service.installzip.JobId
import net.bible.sharedui.assembly.HostDebugArgs
import okio.IOException
import okio.Source

/**
 * The platform-specific I/O of [IosInstallPipeline]: file URLs, temp directory, streaming a (security-scoped) file,
 * job ids and the debug sleep. iOS answers with Foundation (`IosInstallPipelineIo`).
 */
@kotlin.native.HiddenFromObjC
internal interface InstallPipelineIo {
    /** The debug launch arguments the pipeline reads. */
    val debugArgs: HostDebugArgs

    /** The platform temp directory (the pipeline appends its own sub-directory). */
    fun tempDir(): okio.Path

    fun newJobId(): JobId

    /** The decoded path of the file [uri]; throws [IOException] for anything but an absolute file URL. */
    fun filePath(uri: String): String

    /** Streams the exact file [uri], holding any platform access scope until the source is closed. */
    fun openSource(uri: String): Source

    /** Blocks the calling thread for [ms] milliseconds (debug seam only). */
    fun sleepMs(ms: Long)
}

/** The host's [InstallPipelineIo]. */
internal expect fun installPipelineIo(): InstallPipelineIo
