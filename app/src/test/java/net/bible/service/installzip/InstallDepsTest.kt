package net.bible.service.installzip

import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.platform.files.BackupFileType
import net.bible.test.testCoreStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import okio.Path.Companion.toOkioPath
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class InstallDepsTest {
    @Test fun terminalMessagesPreserveExistingToastMapping() {
        val strings = testCoreStrings()
        assertEquals(strings.installZipSuccessful, terminalEventMessage(InstallPhase.Done, strings))
        assertEquals(strings.installZipCanceled, terminalEventMessage(InstallPhase.Cancelled, strings))
        assertEquals(strings.invalidModule, terminalEventMessage(InstallPhase.Error(InstallError.INVALID_FILE, "bad.sqlite"), strings))
        assertEquals(strings.errorOccurred, terminalEventMessage(InstallPhase.Error(InstallError.GENERIC, null), strings))
        assertEquals("", terminalEventMessage(InstallPhase.Inspecting, strings))
    }

    @Test fun localSniffingPreservesMagicAndFailureFallback() = runTest {
        val file = File.createTempFile("install-sniff", ".tmp")
        try {
            file.writeBytes("SQLite format 3".toByteArray(Charsets.US_ASCII) + byteArrayOf(0))
            assertEquals(BackupFileType.SQLITE3, determineLocalFileType(file.toOkioPath()))
            file.writeBytes(byteArrayOf(0x50, 0x4b, 3, 4))
            assertEquals(BackupFileType.ZIP, determineLocalFileType(file.toOkioPath()))
            file.writeText("SQLite format 3 ")
            assertEquals(BackupFileType.UNKNOWN, determineLocalFileType(file.toOkioPath()))
            file.writeBytes(byteArrayOf())
            assertEquals(BackupFileType.UNKNOWN, determineLocalFileType(file.toOkioPath()))
            file.delete()
            assertEquals(BackupFileType.UNKNOWN, determineLocalFileType(file.toOkioPath()))
        } finally { file.delete() }
    }

    @Test fun swordScanDistinguishesModulesEpubAndUnexpectedEntries() {
        val file = File.createTempFile("install-scan", ".zip")
        fun zip(vararg entries: String) {
            ZipOutputStream(file.outputStream()).use { out ->
                entries.forEach { out.putNextEntry(ZipEntry(it)); out.write(1); out.closeEntry() }
            }
        }
        try {
            zip("mods.d/test.conf", "modules/test/data")
            assertEquals(InstallInspector.SwordZipScan(emptyList(), 2, false, false), scanSwordZip(file.toOkioPath()))
            zip("META-INF/container.xml", "content.xhtml")
            assertEquals(InstallInspector.SwordZipScan(emptyList(), 2, true, false), scanSwordZip(file.toOkioPath()))
            zip("mods.d/test.conf", "unexpected")
            assertTrue(scanSwordZip(file.toOkioPath()).invalid)
            zip()
            assertTrue(scanSwordZip(file.toOkioPath()).invalid)
            // Zip-slip: a traversal name hidden behind a valid-looking prefix is an invalid archive.
            zip("mods.d/test.conf", "modules/../../evil")
            assertTrue(scanSwordZip(file.toOkioPath()).invalid)
        } finally { file.delete() }
    }
}
