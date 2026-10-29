@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import net.bible.service.installzip.InstallError
import net.bible.sharedui.app.ports.CompletingDocumentInstallStarter
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.service.cloudsync.host.HostBackupHost
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
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.Foundation.stringWithContentsOfURL
import net.bible.service.installzip.determineLocalFileType
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.bible.sharedui.host.ports.HostDocumentInstallStarter
import net.bible.sharedui.host.ports.HostInstallZipHostPlatform
import net.bible.sharedui.host.ports.lastPathComponent

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
            val starter = HostDocumentInstallStarter(
                controller = { controller },
                deps = { deps },
                scope = scope,
            )
            val platform = HostInstallZipHostPlatform(controller = { controller }, notificationPermission = IosNotificationPermission())
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

    @Test fun ownedWaiterReturnsOnlyAfterActualInvalidTerminalAndDrain() = withInstallScope { scope ->
        withTempFolder { dir ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val terminal = CompletableDeferred<InstallPhase>()
            val controller = InstallServiceController(
                runnerFactory = { error("Actual controller runs through runOne seam") },
                newJobId = { "owned-invalid" },
                onTerminal = { _, phase -> terminal.complete(phase) },
                scope = scope,
            )
            controller.runOne = { _, _, _, _, _ ->
                entered.complete(Unit)
                release.await()
                InstallPhase.Error(InstallError.GENERIC, null)
            }
            val starter: CompletingDocumentInstallStarter = HostDocumentInstallStarter(
                controller = { controller }, deps = { waiterDeps(dir) }, scope = scope,
            )
            val waiter = scope.async { starter.startAndAwait(listOf(ref(dir / "invalid.zip")), "android.intent.action.VIEW") }
            try {
                withTimeout(5_000) { entered.await() }
                assertFalse(waiter.isCompleted, "Submitting to the controller is not terminal completion")
                assertEquals(listOf("owned-invalid"), controller.jobs.value.map { it.jobId })
                release.complete(Unit)
                withTimeout(5_000) { waiter.await() }
                assertEquals(InstallPhase.Error(InstallError.GENERIC, null), terminal.await())
                assertTrue(controller.jobs.value.isEmpty())
            } finally { waiter.cancelAndJoin() }
        }
    }

    @Test fun cancellingOwnedWaiterDoesNotCancelActualInstallWorker() = withInstallScope { scope ->
        withTempFolder { dir ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val terminal = CompletableDeferred<InstallPhase>()
            val controller = InstallServiceController(
                runnerFactory = { error("Actual controller runs through runOne seam") },
                newJobId = { "surviving-worker" },
                onTerminal = { _, phase -> terminal.complete(phase) },
                scope = scope,
            )
            controller.runOne = { _, _, _, _, _ ->
                entered.complete(Unit)
                release.await()
                InstallPhase.Done
            }
            val starter: CompletingDocumentInstallStarter = HostDocumentInstallStarter(
                controller = { controller }, deps = { waiterDeps(dir) }, scope = scope,
            )
            val waiter = scope.launch { starter.startAndAwait(listOf(ref(dir / "module.zip")), null) }
            try {
                withTimeout(5_000) { entered.await() }
                waiter.cancelAndJoin()
                assertFalse(terminal.isCompleted, "Cancelling a screen waiter must not terminate the worker")
                assertEquals(listOf("surviving-worker"), controller.jobs.value.map { it.jobId })
                release.complete(Unit)
                assertEquals(InstallPhase.Done, withTimeout(5_000) { terminal.await() })
                assertTrue(controller.jobs.value.isEmpty())
            } finally { waiter.cancelAndJoin() }
        }
    }

    private fun waiterDeps(dir: Path) = InstallServiceController.JobDeps(
        acquireDir = dir, openStream = { error("runOne replaces only the runner") },
        isStudyPadExport = { false }, studyPadStats = { error("unused") },
        swordZipScan = { error("unused") }, epubUpgradeCheck = { false },
    )

    @Test fun externalBackslashBasenamesInstallInsidePromptDirectoryWithoutChangingSourceIdentity() = withInstallScope { scope ->
        val root = net.bible.sharedui.assembly.IosAssemblyTest.sharedRoot
        net.bible.sharedui.assembly.awaitStartupOnMain { net.bible.sharedui.assembly.IosAppStartup.resetForTest() }
        try {
            net.bible.sharedui.assembly.awaitStartupOnMain {
                net.bible.sharedui.assembly.IosAppStartup.ensureStarted(net.bible.sharedui.assembly.IosAppConfig(
                    root = root, uiLanguage = "en", repositories = org.crosswire.common.util.PropertyMap(),
                    secretStore = net.bible.sharedui.assembly.InMemorySecretStore(),
                ))
            }
            val koin = org.koin.mp.KoinPlatform.getKoin()
            val modules = koin.get<net.bible.sharedcore.platform.AppDirectories>().modulesDir
            val token = NSUUID().UUIDString
            val sentinel = modules / "target-$token.csv"
            FileSystem.SYSTEM.createDirectories(modules)
            FileSystem.SYSTEM.write(sentinel) { writeUtf8("outside sentinel") }
            val prompts = modules / "prompts"
            FileSystem.SYSTEM.createDirectories(prompts)
            val before = FileSystem.SYSTEM.list(prompts).toSet()
            val sourceDir = NSURL.fileURLWithPath(NSTemporaryDirectory())
                .URLByAppendingPathComponent("install-basename-$token", true)!!
            assertTrue(platform.Foundation.NSFileManager.defaultManager.createDirectoryAtURL(sourceDir, true, null, null))
            val csv = "name,prompt\nStudy,Exact source payload $token\n"
            val captured = mutableListOf<InstallSource>()
            val phases = mutableListOf<InstallPhase>()
            val controller = InstallServiceController(
                runnerFactory = { net.bible.service.installzip.InstallJobRunner(
                    net.bible.service.installzip.InstallInspector(::determineLocalFileType),
                    net.bible.service.installzip.DefaultInstallCommitter(koin.get(), koin.get(), koin.get(), clearAddonCaches = {}),
                ) },
                newJobId = { NSUUID().UUIDString }, onTerminal = { _, phase -> phases += phase }, scope = scope,
            )
            fun assertSafeDestination(source: InstallSource) {
                for (name in listOf(source.displayName, source.installationName)) {
                    assertEquals(prompts, (prompts / checkNotNull(name)).parent,
                        "Installation metadata must resolve to a direct child, including drive-prefixed basenames")
                }
            }
            val actualRun = controller.runOne
            controller.runOne = { id, source, deps, phase, decision ->
                captured += source
                // Fail before an unsafe commit can touch the process working directory.
                assertSafeDestination(source)
                actualRun(id, source, deps, phase, decision)
            }
            val deps = InstallServiceController.JobDeps(
                acquireDir = root / "basename-acquire-$token",
                openStream = { source -> net.bible.service.installzip.InstallStream(
                    okio.Buffer().writeUtf8(IosPromptFiles().read(DocumentRef(source.uri))), 0L,
                ) },
                isStudyPadExport = { false }, studyPadStats = { error("CSV has no StudyPad statistics") },
                swordZipScan = { error("CSV is not an archive") }, epubUpgradeCheck = { false },
            )
            val starter = HostDocumentInstallStarter(controller = { controller }, deps = { deps }, scope = scope)
            val registry = org.crosswire.jsword.book.Books.installed()
            val booksBefore = registry.getBooks().toSet()
            val decisions = scope.launch {
                controller.jobs.collect { jobs ->
                    jobs.filter { it.phase is InstallPhase.AwaitingDecision }.forEach {
                        // Exercise the real overwrite path too; the outside sentinel must remain protected.
                        controller.resolveDecision(it.jobId, true)
                    }
                }
            }
            try {
                for (name in listOf("..\\target-$token.csv", "benign\\name-$token.csv", "C:report-$token.csv")) {
                    val external = sourceDir.URLByAppendingPathComponent(name)!!
                    assertTrue(platform.Foundation.NSString.create(string = csv).writeToURL(
                        external, true, platform.Foundation.NSUTF8StringEncoding, null))
                    assertEquals(name, external.lastPathComponent, "Foundation must retain the literal external basename")
                    withTimeout(5_000) { starter.startAndAwait(listOf(DocumentRef(external.absoluteString!!)), null) }
                    assertEquals(external.absoluteString, captured.last().uri, "Destination sanitizing must not rewrite the source URL")
                    // Assert outside runOne too: the controller intentionally catches worker failures.
                    assertSafeDestination(captured.last())
                    assertEquals("outside sentinel", FileSystem.SYSTEM.read(sentinel) { readUtf8() })
                    assertEquals(csv, platform.Foundation.NSString.stringWithContentsOfURL(
                        external, platform.Foundation.NSUTF8StringEncoding, null))
                }
                assertEquals(listOf<InstallPhase>(InstallPhase.Done, InstallPhase.Done, InstallPhase.Done), phases)
                val installed = FileSystem.SYSTEM.list(prompts).filter { it !in before }
                assertEquals(3, installed.size, "All real commits must create direct children of the prompt directory")
                assertTrue(installed.all { FileSystem.SYSTEM.metadata(it).isRegularFile })
                assertTrue(installed.all { FileSystem.SYSTEM.read(it) { readUtf8() } == csv })
            } finally {
                decisions.cancelAndJoin()
                registry.getBooks().filter { it !in booksBefore }.forEach { registry.removeBook(it) }
                FileSystem.SYSTEM.list(prompts).filter { it !in before }.forEach { FileSystem.SYSTEM.deleteRecursively(it) }
                FileSystem.SYSTEM.delete(sentinel, mustExist = false)
                FileSystem.SYSTEM.deleteRecursively(root / "basename-acquire-$token", mustExist = false)
                platform.Foundation.NSFileManager.defaultManager.removeItemAtURL(sourceDir, null)
            }
        } finally { net.bible.sharedui.assembly.awaitStartupOnMain { net.bible.sharedui.assembly.IosAppStartup.resetForTest() } }
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
            val platform = HostInstallZipHostPlatform(controller = { controller }, notificationPermission = IosNotificationPermission())
            HostDocumentInstallStarter(controller = { controller }, deps = { deps }, scope = scope)
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
            val platform = HostInstallZipHostPlatform(notificationPermission = IosNotificationPermission())
            assertTrue(platform.isStudyPadExport(ref(studyPad)))
            assertFalse(platform.isStudyPadExport(ref(modules)))
            assertFalse(platform.isStudyPadExport(ref(ordinary)))
        }
    }

    @Test fun displayNameDecodesTheLastFileUrlComponent() {
        val platform = HostInstallZipHostPlatform(notificationPermission = IosNotificationPermission())
        assertEquals("My module.zip", platform.displayName(DocumentRef("file:///tmp/nested/My%20module.zip")))
    }

    @Test fun mapPhasePreservesDecisionPayloadsAndProgress() {
        val platform = HostInstallZipHostPlatform(notificationPermission = IosNotificationPermission())
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
            val platform = HostInstallZipHostPlatform(
                notificationPermission = IosNotificationPermission(),
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
        val host = HostBackupHost(picker, delivery, { AppDialogController() }, { "OK" })
        var opens = 0
        host.onRestoreModules = { opens++ }
        host.restoreModules()
        assertEquals(1, opens)
        // HostApp must clear this hook on disposal, so a stale appHosts instance cannot be retained or invoked.
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
