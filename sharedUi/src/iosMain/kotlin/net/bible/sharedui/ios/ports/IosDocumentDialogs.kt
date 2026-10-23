@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.persistCipherKey
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedui.app.ports.DocumentDialogs
import net.bible.sharedui.app.ports.DocumentDialogsCore
import net.bible.sharedui.strings.keyed.StringTable
import org.crosswire.jsword.book.Book

/** iOS uses the same unlock loop and persisted cipherKey as Android. */
@kotlin.native.HiddenFromObjC
class IosDocumentDialogs(
    private val scope: CoroutineScope,
    dialogs: AppDialogController,
    private val table: () -> StringTable,
    private val notifier: UserNotifier,
    private val watcher: BookInstallWatcher,
) : DocumentDialogs {
    val core = DocumentDialogsCore(dialogs, table, live = true)
    private fun launch(action: suspend () -> Unit) { scope.launch { safely { action(); true } } }
    private suspend fun safely(action: suspend () -> Boolean): Boolean = try { action() }
    catch (e: CancellationException) { throw e }
    catch (e: Throwable) {
        net.bible.sharedcore.log.Log.e("IosDocumentDialogs", "Document dialog failed", e)
        notifier.showError(table().text("error_occurred"), e)
        false
    }
    override fun showAbout(documentId: String) = launch {
        SwordDocumentFacade.getDocumentByInitials(documentId)?.let { core.showAbout(it) }
    }
    override fun unlock(documentId: String, onUnlocked: () -> Unit) = launch { if (awaitUnlock(documentId)) onUnlocked() }
    override suspend fun awaitUnlock(documentId: String): Boolean = safely {
        SwordDocumentFacade.getDocumentByInitials(documentId)?.let { unlockBook(it) } ?: false
    }
    suspend fun showAboutBook(book: Book) { safely { core.showAbout(book); true } }
    suspend fun confirmUpgrade(): Boolean = safely { core.confirmUpgrade() }
    suspend fun unlockBook(book: Book): Boolean = safely {
        core.unlockDocument(book) { key -> persistCipherKey(book, key, watcher) }
    }
}
