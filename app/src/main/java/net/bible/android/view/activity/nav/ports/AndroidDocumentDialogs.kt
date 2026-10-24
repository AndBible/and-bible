package net.bible.android.view.activity.nav.ports

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.service.common.CommonUtils
import net.bible.sharedui.app.ports.DocumentDialogs
import org.crosswire.jsword.book.Books

class AndroidDocumentDialogs(private val activity: AppCompatActivity) : DocumentDialogs {
    override fun showAbout(documentId: String) {
        activity.lifecycleScope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(documentId) ?: return@launch
            CommonUtils.showAbout(activity, book)
        }
    }

    override fun unlock(documentId: String, onUnlocked: () -> Unit) {
        activity.lifecycleScope.launch(Dispatchers.Main) {
            val book = Books.installed().getBook(documentId) ?: return@launch
            if (CommonUtils.unlockDocument(activity, book)) onUnlocked()
        }
    }

    override suspend fun awaitUnlock(documentId: String): Boolean = withContext(Dispatchers.Main) {
        val book = Books.installed().getBook(documentId) ?: return@withContext false
        CommonUtils.unlockDocument(activity, book)
    }
}
