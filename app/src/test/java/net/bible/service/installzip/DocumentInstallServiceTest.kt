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
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.on
import net.bible.android.view.activity.page.MainBibleActivity
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPath
import org.junit.After
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Smoke/wiring test for [DocumentInstallService]: proves the whole Task-A5 shell -- content uri
 * (with a Robolectric-registered read grant, forwarded exactly as [DocumentInstallService
 * .enqueueIntent] does for a real caller) -> `onStartCommand` -> the shared, real (not faked)
 * [DocumentInstallService.controller] -> a real [InstallJobRunner]/[InstallInspector]/
 * [AndroidInstallCommitter] stack -- reaches a terminal and fires the classic post-install
 * side effects, without re-testing the state-machine/classification logic itself (that's
 * [InstallJobRunnerTest]/[InstallInspectorTest]/[InstallServiceControllerTest]'s job).
 *
 * The fixture is the exact minimal, hand-fabricated RawLD dictionary zip [AndroidInstallCommitterTest]
 * already proves round-trips through [net.bible.android.control.backup.BackupControl
 * .extractAndRegisterModuleArchive] -- reused here so a genuinely real module registers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentInstallServiceTest {

    @Before
    fun setUp() {
        SwordBookPath.setDownloadDir(SharedConstants.modulesDir)
    }

    @After
    fun tearDown() {
        ABEventBus.unregisterAll()
        Books.installed().getBook("TestDict")?.let { Books.installed().removeBook(it) }
        File(SharedConstants.modulesDir, "mods.d/testdict.conf").delete()
        File(SharedConstants.modulesDir, "modules/lexdict/rawld/testdict").deleteRecursively()
    }

    /** Same fixture shape as `AndroidInstallCommitterTest.commitSwordZipExtractsAndRegistersRealModule`
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
    fun `enqueued content uri drives a job to Done and posts UpdateMainBibleActivityDocuments`() {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://net.bible.installzip.test/testdict.zip")
        val zipBytes = validSwordZipBytes()
        // Fresh InputStream per call (not a single shared instance) -- DocumentInstallService's
        // JobDeps opens this uri more than once (acquire, then the StudyPad-export sniff), exactly
        // as a real content:// uri supports.
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { zipBytes.inputStream() }

        val latch = CountDownLatch(1)
        ABEventBus.register(this) {
            on<MainBibleActivity.UpdateMainBibleActivityDocuments> { latch.countDown() }
        }

        val intent = DocumentInstallService.enqueueIntent(context, listOf(uri))
        val service = Robolectric.buildService(DocumentInstallService::class.java, intent).create().get()
        service.onStartCommand(intent, 0, 1)

        assertNotNull(
            "startForeground() must be called synchronously from onStartCommand",
            shadowOf(service).lastForegroundNotification
        )

        val fired = latch.await(5, TimeUnit.SECONDS)
        assertTrue("UpdateMainBibleActivityDocuments must be posted once the job reaches a terminal phase", fired)

        assertNotNull(
            "the real AndroidInstallCommitter/BackupControl stack must have registered the module",
            Books.installed().getBook("TestDict")
        )
    }
}
