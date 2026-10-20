@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CoroutineScope
import net.bible.android.control.document.DocumentChanges
import net.bible.sharedcore.platform.UserNotifier
import platform.Foundation.NSTemporaryDirectory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.ios.app.IosReadingAppInitPlatform
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import net.bible.android.control.backup.BackupControl
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.service.installzip.*
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedlogic.platform.AddonCatalog
import okio.Buffer
import okio.IOException
import okio.Path.Companion.toPath
import okio.Source
import okio.Timeout
import org.koin.mp.KoinPlatform
import platform.Foundation.inputStreamWithURL
import platform.Foundation.NSInputStream
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

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

/** One headless queue for the process; its scope is not owned/cancelled by a screen or a Koin test container. */
internal object IosInstallPipeline {
    val scope = AppCoroutineScope()
    private val initMutex = Mutex()

    suspend fun ensureInitialized() = initMutex.withLock {
        if (!ReadingAppInit.initialized) ReadingAppInit.initialise(IosReadingAppInitPlatform(IosAppStartup.stringTable))
    }

    val controller by lazy {
        val feedback = IosInstallTerminalFeedback()
        createIosInstallController(
            initialise = { ensureInitialized() },
            runnerFactory = {
                val koin = KoinPlatform.getKoin()
                InstallJobRunner(InstallInspector(::determineLocalFileType), DefaultInstallCommitter(
                    koin.get<BackupControl>(), koin.get<CoreStrings>(), koin.get<AppEnvironment>(),
                    clearAddonCaches = { koin.get<AddonCatalog>().clearCaches() },
                ))
            },
            newJobId = { NSUUID().UUIDString },
            onTerminal = { _, phase -> feedback.handle(phase) },
            scope = scope,
        )
    }

    fun deps(): InstallServiceController.JobDeps = InstallServiceController.JobDeps(
        // Pure platform temp lookup before enqueue; Koin/database resolution happens only inside the queued job.
        acquireDir = NSTemporaryDirectory().toPath() / "installzip",
        openStream = { source -> InstallStream(openSource(source.uri), 0L) },
        isStudyPadExport = { source -> isStudyPad(source.uri) },
        studyPadStats = { file ->
            val koin = KoinPlatform.getKoin()
            studyPadStatsFor(file, koin.get(), koin.get())
        },
        swordZipScan = { scanSwordZip(it) },
        epubUpgradeCheck = { checkEpubUpgrade(it) },
    )

    fun isStudyPad(uri: String): Boolean = try {
        AndBibleBackupManifest.fromZip(openSource(uri))?.backupType == BackupType.STUDYPAD_EXPORT
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    fun url(uri: String): NSURL = NSURL.URLWithString(uri)
        ?.takeIf { it.isFileURL() && it.path?.startsWith("/") == true }
        ?: throw IOException("Invalid file URL")

    /** Stream the exact URL, preserving literal backslashes and holding its security scope until close. */
    private fun openSource(uri: String): Source {
        val url = url(uri)
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            val stream = NSInputStream.inputStreamWithURL(url) ?: throw IOException("Cannot open file")
            stream.open()
            return object : Source {
                private var closed = false
                override fun read(sink: Buffer, byteCount: Long): Long {
                    check(!closed)
                    require(byteCount >= 0)
                    if (byteCount == 0L) return 0L
                    val bytes = ByteArray(minOf(byteCount, 8192L).toInt())
                    val count = bytes.usePinned { stream.read(it.addressOf(0).reinterpret(), bytes.size.toULong()) }
                    if (count < 0) throw IOException("Cannot read file")
                    if (count == 0L) return -1L
                    sink.write(bytes, 0, count.toInt())
                    return count
                }
                override fun timeout(): Timeout = Timeout.NONE
                override fun close() {
                    if (!closed) {
                        closed = true
                        try { stream.close() } finally { if (scoped) url.stopAccessingSecurityScopedResource() }
                    }
                }
            }
        } catch (e: Throwable) {
            if (scoped) url.stopAccessingSecurityScopedResource()
            throw e
        }
    }
}
