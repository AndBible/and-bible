package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.service.installzip.*
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.files.BackupFileType
import net.bible.sharedui.assembly.hostStringTable
import net.bible.sharedui.strings.keyed.KeyedCoreStrings
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.*
import net.bible.sharedui.host.ports.HostDocumentInstallStarter
import net.bible.sharedui.host.ports.HostInstallTerminalFeedback
import net.bible.sharedui.host.ports.createIosInstallController

class IosInstallFailureTest {
    @Test fun queuedInitializationFailureReportsErrorAfterJobRemoval() = exercise(
        failInitialization = true, cancelInitialization = false,
    )

    @Test fun queuedInitializationCancellationDoesNotReportAnError() = exercise(
        failInitialization = true, cancelInitialization = true,
    )

    @Test fun completedJobReportsSuccessAndInstalledDocumentChange() = exercise(
        failInitialization = false, cancelInitialization = false,
    )

    @Test fun runnerFactoryFailureAlsoReportsErrorAfterJobRemoval() = exercise(
        failInitialization = false, cancelInitialization = false, failFactory = true,
    )

    private fun exercise(failInitialization: Boolean, cancelInitialization: Boolean, failFactory: Boolean = false) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val directory = (NSTemporaryDirectory() + "install-failure-" + NSUUID().UUIDString).toPath()
        FileSystem.SYSTEM.createDirectories(directory)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val terminal = CompletableDeferred<InstallPhase>()
        val strings = KeyedCoreStrings(hostStringTable("en"))
        val errors = mutableListOf<String>()
        val infos = mutableListOf<String>()
        var changed = 0
        var commits = 0
        var factoryCalls = 0
        val notifier = object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) { infos += message }
            override fun showError(message: String, cause: Throwable?) { errors += message }
        }
        val feedback = HostInstallTerminalFeedback(
            strings = { strings }, notifier = { notifier }, installedChanged = { changed++ },
        )
        lateinit var controller: InstallServiceController
        try {
            controller = createIosInstallController(
                scope = scope,
                initialise = {
                    entered.complete(Unit)
                    release.await()
                    if (cancelInitialization) throw CancellationException("initialization cancelled")
                    if (failInitialization) error("initialization failed")
                },
                runnerFactory = {
                    factoryCalls++
                    if (failFactory) error("dependency resolution failed")
                    InstallJobRunner(InstallInspector { BackupFileType.ZIP }, object : InstallCommitter {
                        override suspend fun commitTtf(localFile: Path, displayName: String) = error("unused")
                        override suspend fun commitSwordZip(localFile: Path, totalEntries: Int, onProgress: (Int) -> Unit) { commits++ }
                        override suspend fun commitEpub(localFile: Path, displayName: String): Boolean = error("unused")
                        override suspend fun commitSqlite(localFile: Path, type: SqliteBookType, displayName: String) = error("unused")
                        override suspend fun commitCsv(localFile: Path, displayName: String) = error("unused")
                        override suspend fun commitBackgroundImage(localFile: Path, fileName: String) = error("unused")
                        override suspend fun commitStudyPad(unzipFolder: Path) = error("unused")
                    })
                },
                newJobId = { "held-job" },
                onTerminal = { _, phase ->
                    assertTrue(controller.jobs.value.isEmpty(), "Feedback is durable, not a transient Error phase")
                    feedback.handle(phase)
                    terminal.complete(phase)
                },
            )
            val deps = InstallServiceController.JobDeps(
                acquireDir = directory,
                openStream = { InstallStream(Buffer().writeUtf8("font fixture"), 12) },
                isStudyPadExport = { false }, studyPadStats = { error("unused") },
                swordZipScan = { InstallInspector.SwordZipScan(emptyList(), 1, isEpub = false, invalid = false) },
                epubUpgradeCheck = { false },
            )
            HostDocumentInstallStarter(controller = { controller }, deps = { deps }, scope = scope)
                .start(listOf(net.bible.sharedui.app.ports.DocumentRef("file:///tmp/module.zip")), null)
            withTimeout(5_000) { entered.await() }
            assertEquals(InstallPhase.Queued, controller.jobs.value.single().phase)
            assertEquals(0, factoryCalls, "Dependency resolution must follow queued initialization")
            assertTrue(errors.isEmpty())
            release.complete(Unit)
            val phase = withTimeout(5_000) { terminal.await() }
            assertEquals(1, changed)
            if (cancelInitialization) {
                assertEquals(InstallPhase.Cancelled, phase)
                assertTrue(errors.isEmpty())
                assertEquals(listOf(strings.installZipCanceled), infos)
            } else if (failInitialization || failFactory) {
                assertEquals(InstallPhase.Error(InstallError.GENERIC, null), phase)
                assertEquals(listOf(strings.errorOccurred), errors)
                assertTrue(infos.isEmpty())
            } else {
                assertEquals(InstallPhase.Done, phase)
                assertEquals(1, commits)
                assertEquals(listOf(strings.installZipSuccessful), infos)
                assertTrue(errors.isEmpty())
            }
        } finally {
            scope.cancel()
            FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }
}
