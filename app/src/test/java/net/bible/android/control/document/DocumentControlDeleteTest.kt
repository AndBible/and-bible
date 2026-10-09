package net.bible.android.control.document

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun control() = DocumentControl(
        Mockito.mock(WindowControl::class.java), Mockito.mock(AppSettings::class.java), notifier, Mockito.mock(CoreStrings::class.java),
        appScope, deleteFiles = { filesDeleted += it.initials }, backupDaoProvider = { dao },
    )

    @Test fun backupRowIsDeletedEvenWhenTheCallerIsCancelledBetweenFilesAndRow() = runBlocking {
        val callerScope = CoroutineScope(Job() + Dispatchers.Default)
        try {
            val caller = callerScope.launch { control().deleteDocument(book) }
            withTimeout(10_000) { daoEntered.await() } // files are gone, the row delete is in flight
            caller.cancel()                             // the screen went away (withContext still waits for the app-scope work)
            daoGate.complete(Unit)
            caller.join()
            withTimeout(10_000) { while (backupRowsDeleted.isEmpty()) kotlinx.coroutines.delay(10) }
        } finally {
            callerScope.coroutineContext[Job]?.cancel()
            appScope.coroutineContext[Job]?.cancel()
        }
        assertEquals(listOf("GoneBook"), filesDeleted)
        assertEquals("backup row must go with the files", listOf("GoneBook"), backupRowsDeleted)
        assertTrue("cancelling the screen is not an error: $errors", errors.isEmpty())
    }
}
