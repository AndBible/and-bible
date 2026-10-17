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

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withTimeout
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.common.activate.Activator
import org.crosswire.common.util.NetUtilJvm
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaDataJvm
import org.junit.Assert.assertNotSame
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Thin-adapter tests for [DefaultInstallCommitter]. Since every `commit*` here delegates to
 * already-tested engine code (see [net.bible.android.control.backup.ModuleBackupRoundTripTest] /
 * `ModuleArchiveExtractionTest` for that coverage), this only exercises: (1) the one genuine
 * bit of logic this file owns -- the SQLite header validity gate -- and (2) one real,
 * self-contained integration-style commit ([commitSwordZip]) that proves the wiring reaches the
 * shared engine and reports progress, without depending on any externally-provisioned test
 * modules (`~/.sword` / `.local/testmods.zip`): a minimal, hand-fabricated RawLD dictionary
 * (same fixture shape as `ModuleBackupRoundTripTest.registerMinimalRawLdDictionary`) is fully
 * sufficient to prove extraction + registration end-to-end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DefaultInstallCommitterTest {
    private val committer = DefaultInstallCommitter(org.koin.java.KoinJavaComponent.get(net.bible.android.control.backup.BackupControl::class.java), org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.CoreStrings::class.java), org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppEnvironment::class.java))

    private lateinit var originalDownloadDir: File

    @Before
    fun setUp() {
        originalDownloadDir = SwordBookPathJvm.getSwordDownloadDir()
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
    }

    @After
    fun tearDown() {
        Books.installed().getBook("TestDict")?.let { Books.installed().removeBook(it) }
        File(SharedConstants.modulesDir, "mods.d/testdict.conf").delete()
        File(SharedConstants.modulesDir, "modules/lexdict/rawld/testdict").deleteRecursively()
        SwordBookPathJvm.setDownloadDir(originalDownloadDir)
    }

    /** Build a zip in [CommonUtils.tmpDir] with the given entries, in order. */
    private fun buildZip(name: String, entries: List<Pair<String, ByteArray>>): File {
        val zipFile = File(CommonUtils.tmpDir, name)
        if (zipFile.exists()) zipFile.delete()
        ZipOutputStream(FileOutputStream(zipFile)).use { out ->
            for ((entryName, bytes) in entries) {
                out.putNextEntry(ZipEntry(entryName))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return zipFile
    }

    /**
     * A minimal RawLD dictionary zip (conf + 2 data files) is the simplest genuine SWORD module
     * fixture (mirrors `ModuleBackupRoundTripTest`). Proves [DefaultInstallCommitter.commitSwordZip]
     * really delegates to [net.bible.android.control.backup.BackupControl.extractAndRegisterModuleArchive]
     * end-to-end: the `.conf` lands on disk, the module registers and is readable, and progress
     * reaches 100%.
     */
    @Test
    fun commitSwordZipExtractsAndRegistersRealModule() = runBlocking {
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

        val zip = buildZip(
            "commit-sword-zip.zip", listOf(
                "mods.d/testdict.conf" to conf.toByteArray(Charsets.UTF_8),
                "modules/lexdict/rawld/testdict/test.dat" to datBytes,
                "modules/lexdict/rawld/testdict/test.idx" to idxBytes,
            )
        )

        val progressValues = mutableListOf<Int>()
        committer.commitSwordZip(zip, totalEntries = 3, onProgress = { progressValues.add(it) })

        val downloadDir = SwordBookPathJvm.getSwordDownloadDir()
        assertTrue("conf must land on disk", File(downloadDir, "mods.d/testdict.conf").exists())
        assertEquals("progress must reach 100%", 100, progressValues.last())

        val book = Books.installed().getBook("TestDict")
        assertNotNull("module should register from the committed zip", book)
        assertTrue(
            "registered module should be readable",
            book!!.getRawText(book.getKey("strong")).contains("The test definition body.")
        )
    }

    @Test
    fun lockedSwordReinstallPreservesMetadataAndUnlocksReplacement() = verifyEncryptedReinstall("")

    @Test
    fun archiveCipherKeyDoesNotReplaceRetainedUserKey() = verifyEncryptedReinstall("different-archive-key")

    @Test
    fun invalidRetainedKeyFallsBackToWorkingArchiveKey() = verifyEncryptedReinstall("k4-Test-key", invalidRetained = true)

    @Test
    fun cloudKeyApplicationIsOrderedAfterHeldInstallWrite() = verifyEncryptedReinstall("", cloudKey = true)

    @Test
    fun swordCommitWaitsForHeldKeyRestoration() = verifyEncryptedReinstall("", holdCompletion = true)

    private fun verifyEncryptedReinstall(
        archiveKey: String,
        invalidRetained: Boolean = false,
        cloudKey: Boolean = false,
        holdCompletion: Boolean = false,
    ) = runBlocking {
        val scope = net.bible.sharedcore.platform.AppCoroutineScope()
        val launcher = OrderedLauncher(scope)
        val watcher = BookInstallWatcher(launcher,
            org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.cloud.DocumentSyncStarter::class.java),
            org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.DocumentSyncProvider::class.java),
            org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java), net.bible.android.platform.AndroidAddonCatalog())
        val committer = DefaultInstallCommitter(
            org.koin.java.KoinJavaComponent.get(net.bible.android.control.backup.BackupControl::class.java),
            org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.CoreStrings::class.java),
            org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppEnvironment::class.java)) { watcher }
        val sync = net.bible.service.cloudsync.documents.DocumentSync(
            org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.platform.AppEnvironment::class.java),
            org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.CloudSyncProvider::class.java),
            org.koin.java.KoinJavaComponent.get(net.bible.sharedcore.cloud.DocumentSyncStarter::class.java), watcher,
            org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java),
            { org.koin.java.KoinJavaComponent.get(net.bible.android.control.backup.BackupControl::class.java) })
        val held = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val dao = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
        val registeredField = BookInstallWatcher::class.java.getDeclaredField("registered").apply { isAccessible = true }
        val previousListener = registeredField.get(null) as org.crosswire.jsword.book.BooksListener?
        watcher.startListening()
        // Use checked-in, genuinely encrypted K4 bytes, not plaintext with a locked flag.
        val root = File(requireNotNull(System.getProperty("k5.project.root")))
        val fixtureRoot = File(root, "jsword/src/commonTest/resources/k4-modules/space dir/ä")
        val conf = File(fixtureRoot, "mods.d/K4Cipher.conf").readText()
            .replace("[K4Cipher]", "[E2Cipher]")
            .replace("./modules/K4Cipher", "./modules/texts/ztext/e2cipher")
            .replace("CipherKey=k4-Test-key", "CipherKey=")
        val entries = listOf("mods.d/E2Cipher.conf" to conf.toByteArray(Charsets.UTF_8)) +
            listOf("ot.bzz", "ot.bzv", "ot.bzs", "nt.bzz", "nt.bzv", "nt.bzs").map {
                "modules/texts/ztext/e2cipher/$it" to File(fixtureRoot, "modules/K4Cipher/$it").readBytes()
            }
        val zip = buildZip("commit-locked-sword.zip", entries)
        val key = "k4-Test-key"
        val plaintext = "In the beginning was the Word, and the Word was with God, and the Word was God."
        var fresh: org.crosswire.jsword.book.Book? = null
        try {
            committer.commitSwordZip(zip, entries.size) {}
            withTimeout(30_000) { watcher.updateRow("E2Cipher") { null } }
            val original = Books.installed().getBook("E2Cipher")!!
            assertTrue("first install must really be locked", original.isLocked)
            assertTrue(original.unlock(key))
            assertTrue(original.getRawText(original.getKey("John 1:1")).contains(plaintext))
            watcher.updateRow("E2Cipher") { it!!.copy(repository = "FixtureRepo",
                cipherKey = if (invalidRetained || cloudKey) "invalid-old-key" else key) }
            Activator.deactivate(original)

            val replacementEntries = entries.toMutableList().apply {
                this[0] = "mods.d/E2Cipher.conf" to conf.replace("CipherKey=", "CipherKey=$archiveKey").toByteArray(Charsets.UTF_8)
            }
            buildZip(zip.name, replacementEntries)
            if (holdCompletion || cloudKey) {
                org.koin.java.KoinJavaComponent.get<net.bible.service.cloudsync.documents.DocumentSyncSettings>(
                    net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java).ensureLoaded()
                watcher.beforeWrite = { held.complete(Unit); release.await() }
                // Lock state of the registered Book at the exact moment commitSwordZip returns.
                val lockedAtCompletion = kotlinx.coroutines.CompletableDeferred<Boolean>()
                val commitJob = async(kotlinx.coroutines.Dispatchers.Default) {
                    committer.commitSwordZip(zip, entries.size) {}
                    lockedAtCompletion.complete(Books.installed().getBook("E2Cipher")!!.isLocked)
                }
                withTimeout(30_000) { held.await() }
                var cloudJob: kotlinx.coroutines.Deferred<Unit>? = null
                try {
                    if (cloudKey) {
                        cloudJob = async(start = CoroutineStart.UNDISPATCHED) {
                            sync.applyCipherKey("E2Cipher", key)
                        }
                        // Settings are loaded, so applyCipherKey ran synchronously up to its queued write.
                        // Unlocking the Book there, ahead of the held install write that restores the
                        // older stored key on the same Book, would let that write override the newer key.
                        assertEquals("cloud key must not touch the Book ahead of the held install write",
                            "", Books.installed().getBook("E2Cipher")!!.unlockKey ?: "")
                    }
                    if (holdCompletion) {
                        // Extraction is done once the write is held; an unguarded commit returns at once.
                        val earlyLocked = kotlinx.coroutines.withTimeoutOrNull(2_000) { lockedAtCompletion.await() }
                        assertEquals("commit must not complete while key restoration is held", null, earlyLocked)
                    }
                } finally {
                    release.complete(Unit)
                    withTimeout(30_000) { commitJob.await(); cloudJob?.await() }
                    watcher.beforeWrite = {}
                }
                if (holdCompletion) {
                    assertFalse("commit must return only after the retained key unlocked the new Book",
                        lockedAtCompletion.await())
                }
            } else committer.commitSwordZip(zip, entries.size) {}
            // The earlier reads left plaintext in JSword's pooled block cache; decode from disk instead.
            dropPooledFileStates("E2Cipher")
            val replacement = Books.installed().getBook("E2Cipher")!!
            assertNotSame("registration must replace the old cached book", original, replacement)
            // Assert before checking the row: queue completion alone must not mask a locked new Book.
            assertFalse("retained key must unlock the newly registered book", replacement.isLocked)
            assertEquals("archive key must not displace retained user key", key, replacement.unlockKey)
            assertTrue("replacement must decrypt real ciphertext",
                replacement.getRawText(replacement.getKey("John 1:1")).contains(plaintext))
            val row = dao.getBook("E2Cipher")!!
            assertEquals("FixtureRepo", row.repository)
            assertEquals(key, row.cipherKey)

            dropPooledFileStates("E2Cipher")
            val diskConf = File(SharedConstants.modulesDir, "mods.d/E2Cipher.conf")
            assertTrue("restoring app key must not rewrite archive config",
                diskConf.readText().contains("CipherKey=$archiveKey\n"))
            val metadata = SwordBookMetaDataJvm.create(diskConf, NetUtilJvm.getURI(SharedConstants.modulesDir))
            metadata.setDriver(SwordBookDriver.instance())
            val diskBook = BookType.fromString("zText")!!.createBook(metadata)
            fresh = diskBook
            assertEquals("independent disk-loaded book must use archive config", archiveKey.isEmpty(), diskBook.isLocked)
            assertEquals(archiveKey, diskBook.unlockKey)
            assertTrue(diskBook.unlock(row.cipherKey))
            assertTrue("persisted key must decrypt a fresh disk-loaded book",
                diskBook.getRawText(diskBook.getKey("John 1:1")).contains(plaintext))
        } finally {
            release.complete(Unit)
            watcher.beforeWrite = {}
            fresh?.let { Activator.deactivate(it) }
            Books.installed().getBook("E2Cipher")?.let {
                Activator.deactivate(it)
                Books.installed().removeBook(it)
            }
            withTimeout(30_000) {
                launcher.join("book-install")
                launcher.join("book-install-auto-upload")
            }
            File(SharedConstants.modulesDir, "mods.d/E2Cipher.conf").delete()
            File(SharedConstants.modulesDir, "modules/texts/ztext/e2cipher").deleteRecursively()
            zip.delete()
            Books.installed().removeBooksListener(watcher.listener)
            previousListener?.let { Books.installed().addBooksListener(it) }
            registeredField.set(null, previousListener)
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            resetDatabase()
        }
    }

    /**
     * Closes and forgets JSword's pooled open-file states of [initials]. They are shared by every Book
     * of that name and cache the last decoded block, which would let a read "succeed" from plaintext
     * decoded before the reinstall instead of from the files now on disk.
     */
    private fun dropPooledFileStates(initials: String) {
        val manager = org.crosswire.jsword.book.sword.state.OpenFileStateManager.instance()
        val field = manager.javaClass.getDeclaredField("metaToStates").apply { isAccessible = true }
        val lock = manager.javaClass.getDeclaredField("lock").apply { isAccessible = true }.get(manager)!!
        synchronized(lock) {
            @Suppress("UNCHECKED_CAST")
            val states = field.get(manager) as MutableMap<org.crosswire.jsword.book.BookMetaData,
                ArrayDeque<org.crosswire.jsword.book.sword.state.OpenFileState>>
            states.entries.filter { it.key.initials == initials }.forEach { (_, pooled) ->
                pooled.forEach { it.releaseResources() }
                pooled.clear()
            }
        }
    }

    @Test
    fun commitSqliteThrowsInvalidInstallFileOnBadHeader() = runBlocking {
        val bogus = File(CommonUtils.tmpDir, "not-a-sqlite-db.mybible")
        bogus.writeBytes("this is not a sqlite database at all".toByteArray(Charsets.UTF_8))

        var thrown: InvalidInstallFile? = null
        try {
            committer.commitSqlite(bogus, SqliteBookType.MYSWORD, "not-a-sqlite-db.mybible")
        } catch (e: InvalidInstallFile) {
            thrown = e
        }

        assertNotNull("a non-SQLite file must be rejected", thrown)
        assertEquals("not-a-sqlite-db.mybible", thrown!!.filename)
        assertFalse(
            "no output file should be written when the header check fails",
            File(File(SharedConstants.modulesDir, "mysword"), "not-a-sqlite-db.mybible").exists()
        )
    }
}
