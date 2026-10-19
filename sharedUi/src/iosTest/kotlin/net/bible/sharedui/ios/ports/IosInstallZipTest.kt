@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.service.cloudsync.ios.IosBackupHost
import net.bible.service.common.AndBibleBackupManifest
import net.bible.service.common.BackupType
import net.bible.service.installzip.DecisionRequest
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.InstallPhase
import net.bible.service.installzip.InstallServiceController
import net.bible.service.installzip.InstallSource
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.FilePicker
import net.bible.sharedcore.platform.files.PickedSource
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.installzip.InstallUiState
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.use
import org.crosswire.common.compress.ZipWriter
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosInstallZipTest {
    @Test fun startEnqueuesOneJobPerRef() = withInstallScope { scope ->
        withTempFolder { dir ->
            val sources = mutableListOf<InstallSource>()
            val release = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            val drained = CompletableDeferred<Unit>()
            var completed = 0
            var nextId = 0
            val controller = InstallServiceController(
                runnerFactory = { error("runOne replaces only the runner, not the enqueue controller") },
                newJobId = { "job-${nextId++}" },
                onTerminal = { _, _ -> if (++completed == 2) drained.complete(Unit) },
                scope = scope,
            )
            controller.runOne = { _, source, _, _, _ ->
                sources += source
                entered.complete(Unit)
                release.await()
                InstallPhase.Done
            }
            val deps = InstallServiceController.JobDeps(
                acquireDir = dir,
                openStream = { error("Runner is paused through the controller's runOne seam") },
                isStudyPadExport = { false },
                studyPadStats = { error("unused") },
                swordZipScan = { error("unused") },
                epubUpgradeCheck = { false },
            )
            val starter = IosDocumentInstallStarter(
                controller = { controller },
                deps = { deps },
                scope = scope,
            )
            val platform = IosInstallZipHostPlatform(controller = { controller })
            val refs = listOf(ref(dir / "My module.zip"), ref(dir / "Pilgrim.epub"))
            starter.start(refs, "android.intent.action.SEND_MULTIPLE")
            withTimeout(5_000) { entered.await() }

            // Observe real controller jobs while the first worker is paused: a local enqueue counter cannot pass.
            assertSame(controller.jobs, platform.jobs)
            assertEquals(listOf("job-0", "job-1"), platform.jobs.value.map { it.jobId })
            assertEquals(listOf("My module.zip", "Pilgrim.epub"), platform.jobs.value.map { it.displayName })
            assertEquals(InstallPhase.Queued, platform.jobs.value[1].phase)
            assertEquals(1, sources.size, "Controller runs sources sequentially")

            release.complete(Unit)
            withTimeout(5_000) { drained.await() }
            assertEquals(listOf(
                InstallSource(refs[0].uri, "android.intent.action.SEND_MULTIPLE", "My module.zip", "application/zip", "My module.zip"),
                InstallSource(refs[1].uri, "android.intent.action.SEND_MULTIPLE", "Pilgrim.epub", "application/epub+zip", "Pilgrim.epub"),
            ), sources)
            assertTrue(platform.jobs.value.isEmpty(), "Terminal jobs drain from the actual controller")
        }
    }

    @Test fun resolveDecisionResumesTheActualControllerJob() = withInstallScope { scope ->
        withTempFolder { dir ->
            val entered = CompletableDeferred<Unit>()
            val terminal = CompletableDeferred<InstallPhase>()
            val controller = InstallServiceController(
                runnerFactory = { error("runOne replaces only the runner") },
                newJobId = { "decision-job" },
                onTerminal = { _, phase -> terminal.complete(phase) },
                scope = scope,
            )
            controller.runOne = { _, _, _, onPhase, awaitDecision ->
                val request = DecisionRequest.Overwrite(listOf("mods.d/kjv.conf"))
                onPhase(InstallPhase.AwaitingDecision(request))
                entered.complete(Unit)
                if (awaitDecision(request)) InstallPhase.Done else InstallPhase.Cancelled
            }
            val deps = InstallServiceController.JobDeps(
                acquireDir = dir, openStream = { error("unused") }, isStudyPadExport = { false },
                studyPadStats = { error("unused") }, swordZipScan = { error("unused") }, epubUpgradeCheck = { false },
            )
            val platform = IosInstallZipHostPlatform(controller = { controller })
            IosDocumentInstallStarter(controller = { controller }, deps = { deps }, scope = scope)
                .start(listOf(ref(dir / "module.zip")), null)
            withTimeout(5_000) { entered.await() }
            assertEquals(InstallPhase.AwaitingDecision(DecisionRequest.Overwrite(listOf("mods.d/kjv.conf"))),
                platform.jobs.value.single().phase)
            platform.resolveDecision("decision-job", false)
            assertEquals(InstallPhase.Cancelled, withTimeout(5_000) { terminal.await() })
            assertTrue(platform.jobs.value.isEmpty())
        }
    }

    @Test fun studyPadExportIsDetected() = runBlocking {
        withTempFolder { dir ->
            // Extension is intentionally misleading: detection must inspect the shared manifest, not the filename.
            val studyPad = dir / "export.zip"
            archive(studyPad, BackupType.STUDYPAD_EXPORT)
            val modules = dir / "not-a-studypad.abmd.zip"
            archive(modules, BackupType.MODULE_BACKUP)
            val ordinary = dir / "ordinary.zip"
            ZipWriter(FileSystem.SYSTEM.sink(ordinary)).use { zip ->
                zip.putEntry("notes.txt") { it.writeUtf8("not an AndBible export") }
            }
            val platform = IosInstallZipHostPlatform()
            assertTrue(platform.isStudyPadExport(ref(studyPad)))
            assertFalse(platform.isStudyPadExport(ref(modules)))
            assertFalse(platform.isStudyPadExport(ref(ordinary)))
        }
    }

    @Test fun displayNameDecodesTheLastFileUrlComponent() {
        val platform = IosInstallZipHostPlatform()
        assertEquals("My module.zip", platform.displayName(DocumentRef("file:///tmp/nested/My%20module.zip")))
    }

    @Test fun mapPhasePreservesDecisionPayloadsAndProgress() {
        val platform = IosInstallZipHostPlatform()
        fun map(phase: InstallPhase) = platform.mapPhase(InstallJobState("job", "module.zip", phase))
        assertEquals(InstallUiState.Overwrite(listOf("mods.d/kjv.conf")),
            map(InstallPhase.AwaitingDecision(DecisionRequest.Overwrite(listOf("mods.d/kjv.conf")))))
        assertEquals(InstallUiState.StudyPadImport("2 notes"),
            map(InstallPhase.AwaitingDecision(DecisionRequest.StudyPadImport("2 notes"))))
        assertEquals(InstallUiState.EpubUpgrade, map(InstallPhase.AwaitingDecision(DecisionRequest.EpubUpgrade)))
        val progress = map(InstallPhase.Committing(37)) as InstallUiState.Progress
        assertEquals("module.zip", progress.displayName)
        assertEquals(37, progress.percent)
        assertFalse(progress.indeterminate)
        assertTrue(progress.statusText.isNotBlank())
    }

    @Test fun initializationRunsOffMainAndReturnsWithoutWaitingForIt() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val entered = CompletableDeferred<Boolean>()
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        try {
            val platform = IosInstallZipHostPlatform(
                scope = scope,
                initialise = {
                    entered.complete(NSThread.isMainThread)
                    release.await()
                    finished.complete(Unit)
                },
            )
            platform.initialiseIfStartedUninitialised()
            assertFalse(withTimeout(5_000) { entered.await() }, "ReadingAppInit must not execute on UIKit's main thread")
            assertFalse(finished.isCompleted, "Host call returns while initialization is still suspended")
            release.complete(Unit)
            withTimeout(5_000) { finished.await() }
        } finally {
            scope.cancel()
        }
    }

    @Test fun restoreModulesOpensInstallZip() = runBlocking {
        val picker = object : FilePicker {
            override suspend fun pickBackupFile(mime: String): PickedSource? = error("Restore route must not pick directly")
            override suspend fun openInput(source: PickedSource): Source = error("unused")
        }
        val delivery = object : FileDelivery {
            override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String,
                saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult = error("unused")
        }
        val host = IosBackupHost(picker, delivery, { AppDialogController() }, { "OK" })
        var opens = 0
        host.onRestoreModules = { opens++ }
        host.restoreModules()
        assertEquals(1, opens)
        // IosApp must clear this hook on disposal, so a stale appHosts instance cannot be retained or invoked.
        host.onRestoreModules = {}
        host.restoreModules()
        assertEquals(1, opens)
    }

    private fun withInstallScope(body: suspend (CoroutineScope) -> Unit) = runBlocking {
        // Unconfined keeps queue assertions single-threaded; deferred barriers replace test-dispatcher APIs.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try { body(scope) } finally { scope.cancel() }
    }

    private fun archive(path: Path, type: BackupType) {
        ZipWriter(FileSystem.SYSTEM.sink(path)).use { zip ->
            AndBibleBackupManifest(type, andBibleVersion = 1226).saveToZip(zip)
            zip.putEntry("notes.txt") { it.writeUtf8("fixture payload") }
        }
    }

    private fun ref(path: Path) = DocumentRef(NSURL.fileURLWithPath(path.toString()).absoluteString!!)

    private inline fun withTempFolder(body: (Path) -> Unit) {
        val dir = (NSTemporaryDirectory() + "installzip-test-" + NSUUID().UUIDString).toPath()
        FileSystem.SYSTEM.createDirectories(dir)
        try { body(dir) } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }
}
