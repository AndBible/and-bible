package net.bible.android.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.service.common.CommonUtils
import net.bible.service.history.KeyHistoryItem
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookData
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.common.xml.XMLUtil

/**
 * A headless activity that waits for app initialisation then returns a Bible passage.
 * This is triggered by the `net.bible.android.action.GET_PASSAGE` intent.
 */
class GetPassageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerActivityComponent.builder()
            .applicationComponent(BibleApplication.application.applicationComponent)
            .build()
            .inject(this)

        if (intent?.action == INTENT_GET_PASSAGE) {
            lifecycleScope.launch {
                doGetPassage()
            }
        } else {
            fail("Incorrect intent action: ${intent?.action}")
        }
    }

    private suspend fun doGetPassage() {
        val cite = intent.getStringExtra("search_string")
        if (cite.isNullOrBlank()) {
            fail("No citation provided in search_string")
            return
        }

        try {
            Log.i(TAG, "Processing GET_PASSAGE intent for citation: '$cite'")

            // Offload heavy work to IO thread
            val quote = withContext(Dispatchers.IO) {
                // Ensure app is fully initialized (JSword, DB, etc)
                CommonUtils.initializeAppCoroutine()
                net.bible.service.db.DatabaseContainer.initializeDatabase()

                val bibleApplication = application as BibleApplication
                val appComponent = bibleApplication.applicationComponent
                val historyManager = appComponent.historyManager()
                val windowControl = appComponent.windowControl()
                val allWindows = windowControl.windowRepository.windowList
                val activeBible = allWindows
                    .flatMap { historyManager.getHistory(it.id) }
                    .filterIsInstance<KeyHistoryItem>()
                    .filter { it.document.bookCategory == BookCategory.BIBLE }
                    .sortedByDescending { it.createdAt }
                    .mapNotNull { it.document as? SwordBook }
                    .firstOrNull() ?: throw Exception("No active Bible found")

                val swordKey = activeBible.getKey(cite) ?: throw Exception("No verse found for '$cite'")
                val bookData = BookData(activeBible, swordKey)
                
                // JSword XML serialization is CPU-intensive
                XMLUtil.writeToString(bookData.saxEventProvider)
            }

            Log.d(TAG, "Successfully retrieved passage")
            val resultIntent = Intent(INTENT_PUT_PASSAGE).apply {
                putExtra("quote", quote)
                putExtra("citation", cite)
                putExtra("format", "application/xml+osis")
            }
            setResult(Activity.RESULT_OK, resultIntent)

        } catch (e: Exception) {
            fail("Error retrieving passage: ${e.message}")
        } finally {
            // Activity.finish() must be called on the Main thread
            finish()
        }
    }

    private fun fail(msg: String) {
        Log.w(TAG, msg)
        setResult(Activity.RESULT_CANCELED, Intent(INTENT_ERROR_MESSAGE).apply {
            putExtra("message", msg)
        })
        finish()
    }

    companion object {
        private const val TAG = "GetPassageActivity"
        private const val INTENT_GET_PASSAGE = "net.bible.android.action.GET_PASSAGE"
        private const val INTENT_PUT_PASSAGE = "net.bible.android.action.PUT_PASSAGE"
        private const val INTENT_ERROR_MESSAGE = "net.bible.android.action.ERROR_MESSAGE"
    }
}
