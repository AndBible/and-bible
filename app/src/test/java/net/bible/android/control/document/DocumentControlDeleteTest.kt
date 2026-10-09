package net.bible.android.control.document

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.Collections
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.SwordDocumentInfoDao
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookDriver
import net.bible.android.control.document.DocumentChanges
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 * Deleting a document is files + backup row; it must finish even when the screen that asked for it goes away
 * between the two steps (back/finish while a multi-document delete is running).
 */
class DocumentControlDeleteTest {
    private val appScope = AppCoroutineScope()
    private val filesDeleted = mutableListOf<String>()
    private val backupRowsDeleted = mutableListOf<String>()
    private val daoEntered = CompletableDeferred<Unit>()
    private val daoGate = CompletableDeferred<Unit>()
    private val errors = mutableListOf<String>()

    private val dao = object : SwordDocumentInfoDao by Mockito.mock(SwordDocumentInfoDao::class.java) {
        // parks like a real suspend DAO call: a cancellation point between the file delete and the row delete
        override suspend fun deleteByOsisId(initials: String) {
            daoEntered.complete(Unit)
            daoGate.await()
            backupRowsDeleted += initials
        }
    }
    private val notifier = object : UserNotifier {
        override fun showError(message: String, cause: Throwable?) { errors += message }
    }
    private val book = Mockito.mock(Book::class.java).also {
        Mockito.`when`(it.initials).thenReturn("GoneBook")
        Mockito.`when`(it.bookCategory).thenReturn(BookCategory.DICTIONARY)
    }

    // The subclass mock maker cannot stub WindowControl's final activeWindowPageManager, so the page tidy-up after each
    // delete throws here (an NPE from the real getter); deleteDocuments collects that per document and carries on, which
    // is why the asserts below cover the files, rows and notification rather than checkCurrentDocumenInstalled itself.
    // The real getter does reach the open windowRepository getter, though: that records the thread the tidy-up ran on.
    private val tidyUpThreads = Collections.synchronizedList(mutableListOf<String>())
    private val windowControl = Mockito.mock(WindowControl::class.java).also {
        Mockito.`when`(it.windowRepository).thenAnswer { tidyUpThreads += Thread.currentThread().name.substringBefore(" @"); null }
    }

    /** Stands in for the Android main thread: the tidy-up and the notification must run here. */
    private val mainExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, MAIN) }

    @Before fun setMain() { Dispatchers.setMain(mainExecutor.asCoroutineDispatcher()) }

    @After fun resetMain() {
        Dispatchers.resetMain()
        mainExecutor.shutdownNow()
    }

    private fun deletableBook(initials: String) = Mockito.mock(Book::class.java).also {
        Mockito.`when`(it.initials).thenReturn(initials)
        Mockito.`when`(it.bookCategory).thenReturn(BookCategory.DICTIONARY)
        val driver = Mockito.mock(BookDriver::class.java)
        Mockito.`when`(driver.isDeletable(it)).thenReturn(true)
        Mockito.`when`(it.driver).thenReturn(driver)
    }

    private fun control() = DocumentControl(
        windowControl, Mockito.mock(AppSettings::class.java), notifier, Mockito.mock(CoreStrings::class.java),
        appScope, deleteFiles = DocumentFileDeleter { filesDeleted += it.initials }, backupDaoProvider = { dao },
    )

    @Test fun backupRowIsDeletedEvenWhenTheCallerIsCancelledBetweenFilesAndRow() = runBlocking {
        val callerScope = CoroutineScope(Job() + Dispatchers.Default)
        try {
            // runCatching: the mocked window tidy-up throws (see windowControl); that must not leak as an uncaught
            // coroutine exception into the one-JVM suite (runTest elsewhere reports those)
            val caller = callerScope.launch { runCatching { control().deleteDocument(book) } }
            withTimeout(10_000) { daoEntered.await() } // files are gone, the row delete is in flight
            caller.cancel()                             // the screen went away (withContext still waits for the app-scope work)
            daoGate.complete(Unit)
            caller.join()
            withTimeout(10_000) { while (backupRowsDeleted.isEmpty()) kotlinx.coroutines.delay(10) }
            withTimeout(10_000) { while (tidyUpThreads.isEmpty()) kotlinx.coroutines.delay(10) }
        } finally {
            callerScope.coroutineContext[Job]?.cancel()
            appScope.coroutineContext[Job]?.cancel()
        }
        assertEquals(listOf("GoneBook"), filesDeleted)
        assertEquals("backup row must go with the files", listOf("GoneBook"), backupRowsDeleted)
        assertEquals("the page tidy-up runs (on the main thread) although the caller went away", listOf(MAIN), tidyUpThreads)
        assertTrue("cancelling the screen is not an error: $errors", errors.isEmpty())
    }

    @Test fun cancellingTheCallerStillDeletesEveryDocumentAndNotifiesInstalledChanged() = runBlocking {
        val books = listOf(deletableBook("A"), deletableBook("B"), deletableBook("C"))
        var notified = 0
        val notifyThreads = Collections.synchronizedList(mutableListOf<String>())
        val sub = DocumentChanges.installedChanged.subscribe { notified++; notifyThreads += Thread.currentThread().name.substringBefore(" @") }
        // a dispatcher other than the app scope's, like the Main thread in production: with the same one, withContext
        // returns undispatched and a caller cancellation is never observed between documents
        val callerDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val callerScope = CoroutineScope(Job() + callerDispatcher)
        try {
            val caller = callerScope.launch { control().deleteDocuments(books) }
            withTimeout(10_000) { daoEntered.await() } // first document: files gone, its row delete parked
            caller.cancel()                             // the screen went away mid-loop
            daoGate.complete(Unit)
            caller.join()
            kotlinx.coroutines.withTimeoutOrNull(3_000) { while (notified == 0) kotlinx.coroutines.delay(10) } // bounded: a miss fails the asserts below
        } finally {
            sub.cancel()
            callerDispatcher.close()
            callerScope.coroutineContext[Job]?.cancel()
            appScope.coroutineContext[Job]?.cancel()
        }
        assertEquals("every document's files must go", listOf("A", "B", "C"), filesDeleted)
        assertEquals("every document's backup row must go", listOf("A", "B", "C"), backupRowsDeleted)
        assertEquals("observers of the installed list must be told exactly once", 1, notified)
        assertEquals("the installed-changed notification runs on the main thread", listOf(MAIN), notifyThreads)
        assertEquals("each document's page tidy-up runs on the main thread", listOf(MAIN, MAIN, MAIN), tidyUpThreads)
        assertTrue("cancelling the screen is not an error: $errors", errors.isEmpty())
    }

    private companion object {
        const val MAIN = "test-main"
    }
}
