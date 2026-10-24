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

package net.bible.service.installzip

import android.net.Uri
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.backup.BackupControl
import net.bible.sharedcore.platform.files.BackupFileType
import net.bible.android.control.document.DocumentChanges
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import okio.Path.Companion.toOkioPath
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Smoke/wiring test for [DocumentInstallService]: proves the whole Task-A5 shell -- content uri
 * (with a Robolectric-registered read grant, forwarded exactly as [DocumentInstallService
 * .enqueueIntent] does for a real caller) -> `onStartCommand` -> the shared, real (not faked)
 * [DocumentInstallService.controller] -> a real [InstallJobRunner]/[InstallInspector]/
 * [DefaultInstallCommitter] stack -- reaches a terminal and fires the classic post-install
 * side effects, without re-testing the state-machine/classification logic itself (that's
 * [InstallJobRunnerTest]/[InstallInspectorTest]/[InstallServiceControllerTest]'s job).
 *
 * The fixture is the exact minimal, hand-fabricated RawLD dictionary zip [DefaultInstallCommitterTest]
 * already proves round-trips through [net.bible.android.control.backup.BackupControl
 * .extractAndRegisterModuleArchive] -- reused here so a genuinely real module registers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentInstallServiceTest {

    @Test fun missingMetadataKeepsProgressTitleNull() = runBlocking {
        val service = Robolectric.buildService(DocumentInstallService::class.java).get()
        val uri = Uri.parse("content://x/document/primary%3ADownload%2Ffoo.zip")
        val method = DocumentInstallService::class.java.getDeclaredMethod("buildSource", Uri::class.java, String::class.java)
        method.isAccessible = true
        val source = method.invoke(service, uri, null) as InstallSource
        assertEquals(null, source.displayName)
        assertEquals("primary:Download/foo.zip", source.installationName)
        val controller = InstallServiceController(runnerFactory = { error("unused") }, newJobId = { "metadata" }, onTerminal = { _, _ -> }, scope = this)
        controller.runOne = { _, _, _, onPhase, _ ->
            assertEquals(null, controller.jobs.value.single().displayName)
            onPhase(InstallPhase.Inspecting)
            assertEquals(null, controller.jobs.value.single().displayName)
            InstallPhase.Done
        }
        val deps = InstallServiceController.JobDeps(
            acquireDir = File(System.getProperty("java.io.tmpdir"), "metadata-test").toOkioPath(),
            openStream = { error("unused") }, isStudyPadExport = { false },
            studyPadStats = { error("unused") }, swordZipScan = { error("unused") },
            epubUpgradeCheck = { false },
        )
        assertEquals(InstallOutcome.Ok, controller.enqueue(listOf(source), deps))
    }

    private lateinit var originalDownloadDir: File

    @Before
    fun setUp() {
        DocumentInstallService.resetControllerForTest()
        originalDownloadDir = SwordBookPathJvm.getSwordDownloadDir()
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
    }

    @After
    fun tearDown() {
        DocumentInstallService.resetControllerForTest()
        Books.installed().getBook("TestDict")?.let { Books.installed().removeBook(it) }
        File(SharedConstants.modulesDir, "mods.d/testdict.conf").delete()
        File(SharedConstants.modulesDir, "modules/lexdict/rawld/testdict").deleteRecursively()
        SwordBookPathJvm.setDownloadDir(originalDownloadDir)
    }

    /**
     * Regression for the order-dependent leak: the companion caches its controller, while tests swap
     * Koin's `AppCoroutineScope` per test. A controller created under a cancelled scope never runs
     * its workers; [DocumentInstallService.resetControllerForTest] must rebuild it on the live scope.
     */
    @Test
    fun `reset rebuilds the controller on the current AppCoroutineScope`() {
        val koin = org.koin.core.context.GlobalContext.get()
        val snapshot = net.bible.test.KoinFactorySnapshot.of<net.bible.sharedcore.platform.AppCoroutineScope>()
        val deadScope = net.bible.sharedcore.platform.AppCoroutineScope().also { it.cancel() }
        val source = InstallSource(uri = "content://x/stale.zip", action = null, displayName = "stale.zip", mimeType = null)
        val deps = InstallServiceController.JobDeps(
            acquireDir = File(System.getProperty("java.io.tmpdir"), "stale-scope-test").toOkioPath(),
            openStream = { error("unused") }, isStudyPadExport = { false },
            studyPadStats = { error("unused") }, swordZipScan = { error("unused") },
            epubUpgradeCheck = { false },
        )
        fun runBounded(): InstallOutcome? = runBlocking {
            DocumentInstallService.controller.runOne = { _, _, _, _, _ -> InstallPhase.Done }
            kotlinx.coroutines.withTimeoutOrNull(3_000) { DocumentInstallService.controller.enqueue(listOf(source), deps) }
        }
        try {
            koin.loadModules(listOf(org.koin.dsl.module {
                single<net.bible.sharedcore.platform.AppCoroutineScope> { deadScope }
            }))
            DocumentInstallService.resetControllerForTest()
            assertEquals("a controller built on a cancelled scope never runs its job", null, runBounded())
            assertEquals(InstallPhase.Queued, DocumentInstallService.controller.jobs.value.single().phase)

            snapshot.restore()
            DocumentInstallService.resetControllerForTest()
            assertEquals("after reset the controller uses the live scope", InstallOutcome.Ok, runBounded())
        } finally {
            snapshot.restore()
            DocumentInstallService.resetControllerForTest()
        }
    }

    /** Same fixture shape as `DefaultInstallCommitterTest.commitSwordZipExtractsAndRegistersRealModule`
     *  (a minimal genuine RawLD dictionary): a `.conf` + 2 data files, zipped. */
    private fun validSwordZipBytes(): ByteArray {
        val conf = """
            [TestDict]
            DataPath=./modules/lexdict/rawld/testdict/test
            ModDrv=RawLD
            SourceType=Plaintext
            Encoding=UTF-8
            Lang=en
            Description=Test Dictionary
            DistributionLicense=Public Domain
        """.trimIndent()
        val datBytes = "strong\nThe test definition body.".toByteArray(Charsets.UTF_8)
        val idxBytes = java.nio.ByteBuffer.allocate(6).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(0).putShort(datBytes.size.toShort()).array()

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in listOf(
                "mods.d/testdict.conf" to conf.toByteArray(Charsets.UTF_8),
                "modules/lexdict/rawld/testdict/test.dat" to datBytes,
                "modules/lexdict/rawld/testdict/test.idx" to idxBytes,
            )) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `enqueued content uri drives a job to Done and notifies installedChanged`() {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://net.bible.installzip.test/testdict.zip")
        val zipBytes = validSwordZipBytes()
        // Fresh InputStream per call (not a single shared instance) -- DocumentInstallService's
        // JobDeps opens this uri more than once (acquire, then the StudyPad-export sniff), exactly
        // as a real content:// uri supports.
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { zipBytes.inputStream() }

        val streamLatch = CountDownLatch(1)
        val streamSub = DocumentChanges.installedChanged.subscribe { streamLatch.countDown() }

        val intent = DocumentInstallService.enqueueIntent(context, listOf(uri))
        val service = Robolectric.buildService(DocumentInstallService::class.java, intent).create().get()
        service.onStartCommand(intent, 0, 1)

        assertNotNull(
            "startForeground() must be called synchronously from onStartCommand",
            shadowOf(service).lastForegroundNotification
        )

        assertTrue("installedChanged must fire once the job reaches a terminal phase (off-main)", streamLatch.await(5, TimeUnit.SECONDS))
        streamSub.cancel()

        assertNotNull(
            "the real DefaultInstallCommitter/BackupControl stack must have registered the module",
            Books.installed().getBook("TestDict")
        )
    }

    /**
     * Regression lock for the reviewed CRITICAL finding on [determineLocalFileType]: it must
     * detect a real SQLite3 header from the acquired local file, not fall
     * through to [BackupFileType.UNKNOWN] (which would route every
     * `.sqlite3/.mybible/.bblx/.bbli` install to `InstallPlan.Invalid`/Error). The genuine
     * SQLite3 magic is the 16-byte ASCII string "SQLite format 3" followed by a single NUL
     * byte -- a hand-typed literal ending in a visible trailing SPACE character instead looks
     * identical in most editors/diff viewers but never matches.
     */
    @Test
    fun `determineLocalFileType detects a real SQLite3 header, not UNKNOWN`() = runBlocking {
        val sqliteHeader = "SQLite format 3".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
        val file = File.createTempFile("regression", ".sqlite3")
        try {
            file.writeBytes(sqliteHeader)

            val type = determineLocalFileType(file.toOkioPath())

            assertNotEquals(
                "a real SQLite3 header must not be classified as UNKNOWN",
                BackupFileType.UNKNOWN,
                type
            )
            assertEquals(BackupFileType.SQLITE3, type)
        } finally {
            file.delete()
        }
    }
}
