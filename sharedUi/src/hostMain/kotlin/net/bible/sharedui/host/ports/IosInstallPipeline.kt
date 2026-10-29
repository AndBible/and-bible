@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.host.ports

import kotlinx.coroutines.CoroutineScope
import net.bible.android.control.document.DocumentChanges
import net.bible.sharedcore.platform.UserNotifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.bible.service.startup.ReadingAppInit
import net.bible.service.installzip.InstallCommitter
import net.bible.sharedui.assembly.HostDebugArgs
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.host.app.IosReadingAppInitPlatform
import net.bible.android.control.backup.BackupControl
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.service.installzip.*
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedlogic.platform.AddonCatalog
import okio.IOException
import okio.Source
import org.koin.mp.KoinPlatform

/** Wrap initialization inside the real job: startup/dependency failures use the controller's terminal path. */
internal fun createIosInstallController(
    scope: CoroutineScope,
    initialise: suspend () -> Unit,
    runnerFactory: () -> InstallJobRunner,
    newJobId: () -> JobId,
    onTerminal: (JobId, InstallPhase) -> Unit,
): InstallServiceController {
    val controller = InstallServiceController(runnerFactory, newJobId, onTerminal, scope)
    val run = controller.runOne
    controller.runOne = { id, source, deps, onPhase, awaitDecision ->
        initialise()
        run(id, source, deps, onPhase, awaitDecision)
    }
    return controller
}

/** Terminal feedback survives removal of the job from StateFlow, including exceptions before any phase emission. */
internal class IosInstallTerminalFeedback(
    private val strings: () -> CoreStrings = { KoinPlatform.getKoin().get() },
    private val notifier: () -> UserNotifier = { KoinPlatform.getKoin().get() },
    private val installedChanged: () -> Unit = { DocumentChanges.notifyInstalledChanged() },
) {
    fun handle(phase: InstallPhase) {
        installedChanged()
        val message = terminalEventMessage(phase, strings())
        if (message.isNotEmpty()) {
            if (phase is InstallPhase.Error) notifier().showError(message)
            else notifier().showInfo(message)
        }
    }
}

/**
 * Debug seam (`-installCommitDelayMs`): sleeps after each extracted archive entry of a SWORD zip commit, so a UI test can
 * kill the app mid-extraction. Only constructed when the launch argument is set in a debug binary.
 */
private class EntryDelayingCommitter(
    private val inner: InstallCommitter,
    private val delayMs: Long,
    private val sleepMs: (Long) -> Unit,
) : InstallCommitter by inner {
    override suspend fun commitSwordZip(localFile: okio.Path, totalEntries: Int, onProgress: (Int) -> Unit) =
        inner.commitSwordZip(localFile, totalEntries) { percent ->
            onProgress(percent)
            sleepMs(delayMs)
        }
}

/** One headless queue for the process; its scope is not owned/cancelled by a screen or a Koin test container. */
internal object IosInstallPipeline {
    private val io: InstallPipelineIo by lazy { installPipelineIo() }

    /** The debug launch arguments of this host. */
    val debugArgs: HostDebugArgs get() = io.debugArgs

    val scope = AppCoroutineScope()
    private val initMutex = Mutex()

    suspend fun ensureInitialized() = initMutex.withLock {
        if (!ReadingAppInit.initialized) ReadingAppInit.initialise(IosReadingAppInitPlatform(HostStartup.stringTable))
    }

    val controller by lazy {
        val feedback = IosInstallTerminalFeedback()
        createIosInstallController(
            initialise = { ensureInitialized() },
            runnerFactory = {
                val koin = KoinPlatform.getKoin()
                val committer = DefaultInstallCommitter(
                    koin.get<BackupControl>(), koin.get<CoreStrings>(), koin.get<AppEnvironment>(),
                    clearAddonCaches = { koin.get<AddonCatalog>().clearCaches() },
                )
                val delayMs = debugArgs.installCommitDelayMs()
                InstallJobRunner(InstallInspector(::determineLocalFileType),
                    if (delayMs > 0) EntryDelayingCommitter(committer, delayMs, io::sleepMs) else committer)
            },
            newJobId = { io.newJobId() },
            onTerminal = { _, phase -> feedback.handle(phase) },
            scope = scope,
        )
    }

    fun deps(): InstallServiceController.JobDeps = InstallServiceController.JobDeps(
        // Pure platform temp lookup before enqueue; Koin/database resolution happens only inside the queued job.
        acquireDir = io.tempDir() / "installzip",
        openStream = { source -> InstallStream(io.openSource(source.uri), 0L) },
        isStudyPadExport = { source -> isStudyPad(source.uri) },
        studyPadStats = { file ->
            val koin = KoinPlatform.getKoin()
            studyPadStatsFor(file, koin.get(), koin.get())
        },
        swordZipScan = { scanSwordZip(it) },
        epubUpgradeCheck = { checkEpubUpgrade(it) },
    )

    fun isStudyPad(uri: String): Boolean = try {
        AndBibleBackupManifest.fromZip(io.openSource(uri))?.backupType == BackupType.STUDYPAD_EXPORT
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /** The decoded path of the file [uri]; throws [IOException] for anything but an absolute file URL. */
    fun filePath(uri: String): String = io.filePath(uri)
}
