/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.bible.service.common

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.common.activate.Activator
import org.crosswire.common.util.NetUtilJvm
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BooksListener
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaDataJvm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CommonUtilsUnlockDocumentTest {
    @get:Rule val dialogReset = AppDialogControllerResetRule()
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    private val dao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()

    @Test
    fun successfulUnlockPersistsKeyForFilesystemDiscoveredBookWithoutMetadataRow() = runTest(timeout = 30.seconds) {
        withLockedFixture { book ->
            // A nonempty backup DB must not hide a newly discovered book's missing row.
            val unrelated = SwordDocumentInfo("UnlockUnrelated", "Other book", "Other", "en", "OtherRepo", "other-key")
            assertNull(dao.getBook(unrelated.initials))
            dao.insert(unrelated)
            assertNull("fixture registration must not seed the subject row", dao.getBook(INITIALS))

            assertTrue(submitUnlock(book, AppDialogResult.Text(PUBLIC_KEY)))
            assertPlaintext(book)
            assertEquals("unlock must not overwrite another document", unrelated, dao.getBook(unrelated.initials))
            assertEquals("successful Android unlock must persist the key even without an install row",
                PUBLIC_KEY, dao.getBook(INITIALS)?.cipherKey)
        }
    }

    @Test
    fun successfulUnlockPreservesExistingRepositoryAndMetadata() = runTest(timeout = 30.seconds) {
        withLockedFixture { book ->
            val existing = SwordDocumentInfo(INITIALS, "Retained title", "Retained abbreviation", "fi", "FixtureRepo")
            dao.insert(existing)

            assertTrue(submitUnlock(book, AppDialogResult.Text(PUBLIC_KEY)))
            assertPlaintext(book)
            assertEquals(existing.copy(cipherKey = PUBLIC_KEY), dao.getBook(INITIALS))
        }
    }

    @Test
    fun cancellingUnlockDoesNotCreateCipherKeyRow() = runTest(timeout = 30.seconds) {
        withLockedFixture { book ->
            assertFalse(submitUnlock(book, AppDialogResult.Cancel, declineRetry = true))
            assertTrue(book.isLocked)
            assertNull(dao.getBook(INITIALS))
        }
    }

    @Test
    fun incorrectPassphraseDoesNotCreateCipherKeyRow() = runTest(timeout = 30.seconds) {
        withLockedFixture { book ->
            assertFalse(submitUnlock(book, AppDialogResult.Text("not-the-public-fixture-key"), declineRetry = true))
            assertTrue(book.isLocked)
            assertNull(dao.getBook(INITIALS))
        }
    }

    private suspend fun submitUnlock(book: Book, answer: AppDialogResult, declineRetry: Boolean = false): Boolean =
        kotlinx.coroutines.coroutineScope {
            val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java)
            try {
                val activity = controller.setup().get()
                val result = async(start = CoroutineStart.UNDISPATCHED) { CommonUtils.unlockDocument(activity, book) }
                try {
                    val input = checkNotNull(dialogs.pending.value)
                    assertTrue(input.request is AppDialogRequest.TextInput)
                    assertEquals("", (input.request as AppDialogRequest.TextInput).initial)
                    dialogs.respond(input.id, answer)
                    if (declineRetry) {
                        val retry = dialogs.pending.first { it?.request is AppDialogRequest.Confirm }!!
                        dialogs.respond(retry.id, AppDialogResult.Cancel)
                    }
                    result.await()
                } finally {
                    result.cancelAndJoin()
                }
            } finally {
                dialogs.cancelAll()
                controller.pause().stop().destroy()
            }
        }

    /** Discover the checked-in ciphertext without an install event, as at startup before watcher registration. */
    private suspend fun withLockedFixture(block: suspend (Book) -> Unit) {
        val root = Files.createTempDirectory("android-unlock-cipher-").toFile()
        val registeredField = BookInstallWatcher::class.java.getDeclaredField("registered").apply { isAccessible = true }
        val listener = registeredField.get(null) as BooksListener?
        var book: Book? = null
        try {
            assertNull("fixture must not replace a globally installed module", Books.installed().getBook(INITIALS))
            val projectRoot = File(requireNotNull(System.getProperty("k5.project.root")))
            val source = File(projectRoot, "jsword/src/commonTest/resources/k4-modules/space dir/ä")
            val conf = File(root, "mods.d/E2Cipher.conf").apply { parentFile.mkdirs() }
            conf.writeText(File(source, "mods.d/K4Cipher.conf").readText()
                .replace("[K4Cipher]", "[$INITIALS]")
                .replace("./modules/K4Cipher", "./modules/E2Cipher")
                .replace("CipherKey=$PUBLIC_KEY", "CipherKey="))
            File(source, "modules/K4Cipher").copyRecursively(File(root, "modules/E2Cipher"))
            val metadata = requireNotNull(SwordBookMetaDataJvm.create(conf, NetUtilJvm.getURI(root)))
            metadata.setDriver(SwordBookDriver.instance())
            val fixture = BookType.fromString("zText")!!.createBook(metadata)
            book = fixture
            // Only the backup install listener is suppressed; the book, decryption and UI channel are real.
            listener?.let { Books.installed().removeBooksListener(it) }
            try {
                Books.installed().addBook(fixture)
            } finally {
                listener?.let { Books.installed().addBooksListener(it) }
            }
            assertTrue("fixture must start locked, not with a preseeded key", fixture.isLocked)
            assertEquals("", fixture.unlockKey)
            assertNull(dao.getBook(INITIALS))
            block(fixture)
        } finally {
            dialogs.cancelAll()
            book?.let { fixture ->
                Activator.deactivate(fixture)
                releasePooledFiles(fixture)
                listener?.let { Books.installed().removeBooksListener(it) }
                try {
                    Books.installed().removeBook(fixture)
                } finally {
                    listener?.let { Books.installed().addBooksListener(it) }
                }
            }
            root.deleteRecursively()
            resetDatabase()
        }
    }

    // Deactivation alone leaves zText file states pooled globally, including decoded blocks.
    private fun releasePooledFiles(book: Book) {
        val manager = org.crosswire.jsword.book.sword.state.OpenFileStateManager.instance()
        val field = manager.javaClass.getDeclaredField("metaToStates").apply { isAccessible = true }
        val lock = manager.javaClass.getDeclaredField("lock").apply { isAccessible = true }.get(manager)!!
        synchronized(lock) {
            @Suppress("UNCHECKED_CAST")
            val states = field.get(manager) as MutableMap<Any, ArrayDeque<org.crosswire.jsword.book.sword.state.OpenFileState>>
            states.values.forEach { pooled ->
                pooled.removeAll { state ->
                    (state.getBookMetaData() === book.bookMetaData).also { if (it) state.releaseResources() }
                }
            }
        }
    }

    private fun assertPlaintext(book: Book) {
        assertFalse("successful unlock must leave the actual book readable", book.isLocked)
        assertTrue("unlock must decrypt the real checked-in ciphertext",
            book.getRawText(book.getKey("John 1:1")).contains(
                "In the beginning was the Word, and the Word was with God, and the Word was God."))
    }

    companion object {
        private const val INITIALS = "E2Cipher"
        private const val PUBLIC_KEY = "k4-Test-key"
    }
}
