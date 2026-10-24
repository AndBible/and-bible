package net.bible.service.installzip

import kotlinx.coroutines.test.runTest
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.sharedcore.platform.files.BackupFileType
import net.bible.test.testCoreStrings
import org.crosswire.jsword.book.sword.SwordBookPathJvm
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

    private fun notMyBibleBytes(): ByteArray {
        val f = File.createTempFile("other-fixture", ".SQLite3")
        try {
            openSqlite(f.path).use { c -> c.exec("CREATE TABLE something (a TEXT)") }
            return f.readBytes()
        } finally { f.delete() }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val file = File.createTempFile("install-root", ".zip")
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { (n, b) -> out.putNextEntry(ZipEntry(n)); out.write(b); out.closeEntry() }
        }
        return file
    }

    @Test fun rootLevelMyBibleModuleIsAValidMyBibleZip() {
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        val file = zipOf("KJV.SQLite3" to myBibleBytes())
        try {
            assertEquals(InstallInspector.SwordZipScan(emptyList(), 1, false, false), scanSwordZip(file.toOkioPath()))
        } finally { file.delete() }
    }

    @Test fun rootLevelMyBibleModuleWithAuxiliaryFilesIsValid() {
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        val file = zipOf("X.SQLite3" to myBibleBytes(), "X.commentaries.SQLite3" to myBibleBytes())
        try {
            assertEquals(InstallInspector.SwordZipScan(emptyList(), 2, false, false), scanSwordZip(file.toOkioPath()))
        } finally { file.delete() }
    }

    @Test fun rootLevelNonMyBibleSqliteStaysInvalidAndReachesTheUiAsAnError() = runTest {
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        val file = zipOf("X.SQLite3" to notMyBibleBytes())
        try {
            val scan = scanSwordZip(file.toOkioPath())
            assertTrue(scan.invalid)
            val plan = InstallInspector { BackupFileType.ZIP }.inspect(
                file.toOkioPath(), "x.zip", null, false, { error("unused") }, { scanSwordZip(it) }, { false })
            assertTrue(plan is InstallPlan.Invalid)
            // The runner turns Invalid into Error(INVALID_FILE), which the service toasts as invalidModule.
            val strings = testCoreStrings()
            assertEquals(strings.invalidModule,
                terminalEventMessage(InstallPhase.Error(InstallError.INVALID_FILE, "x.zip"), strings))
        } finally { file.delete() }
    }

    @Test fun rootLevelGarbageSqliteEntryIsInvalid() {
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        val file = zipOf("X.SQLite3" to byteArrayOf(1, 2, 3))
        try { assertTrue(scanSwordZip(file.toOkioPath()).invalid) } finally { file.delete() }
    }

    @Test fun myBibleDirectoryZipStillScansValid() {
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        val file = zipOf("mybible/KJV.SQLite3" to myBibleBytes())
        try {
            assertEquals(InstallInspector.SwordZipScan(emptyList(), 1, false, false), scanSwordZip(file.toOkioPath()))
        } finally { file.delete() }
    }
}
