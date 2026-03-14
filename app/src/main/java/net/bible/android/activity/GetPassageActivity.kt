package net.bible.android.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication
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

        lifecycleScope.launch {
            try {
                doGetPassage()
            } finally {
                finish()
            }
        }
    }

    private suspend fun doGetPassage() {
        try {
            if (intent?.action != INTENT_GET_PASSAGE) {
                throw Exception("Incorrect intent action: ${intent?.action}")
            }
            val cite = intent.getStringExtra(GET_PASSAGE_SEARCH_STRING)
            if (cite.isNullOrBlank()) {
                throw Exception("No citation provided in $GET_PASSAGE_SEARCH_STRING")
            }
            Log.i(TAG, "Processing GET_PASSAGE intent for citation of length ${cite.length}")
            
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
                    .firstNotNullOfOrNull { it.document as? SwordBook } ?: throw Exception("No active Bible found")

                val swordKey = activeBible.getKey(cite) ?: throw Exception("No verse found for '$cite'")
                val bookData = BookData(activeBible, swordKey)
                
                // JSword XML serialization is CPU-intensive
                XMLUtil.writeToString(bookData.saxEventProvider)
            }

            if (quote.length > MAX_INTENT_EXTRA_SIZE) {
                val msg = "Passage too large (${quote.length} bytes)"
                Log.w(TAG, msg)
                setResult(Activity.RESULT_CANCELED, Intent(INTENT_ERROR_MESSAGE).apply {
                    putExtra("message", msg)
                })
            } else {
                Log.d(TAG, "Successfully retrieved passage")
                val resultIntent = Intent(INTENT_PUT_PASSAGE).apply {
                    putExtra("quote", quote)
                    putExtra("citation", cite)
                    putExtra("format", "application/xml+osis")
                }
                setResult(Activity.RESULT_OK, resultIntent)
            }
        } catch (e: Exception) {
            val msg = "Error retrieving passage: ${e.message}"
            Log.w(TAG, msg)
            setResult(Activity.RESULT_CANCELED, Intent(INTENT_ERROR_MESSAGE).apply {
                putExtra("message", msg)
            })
        }
    }

    companion object {
        private const val TAG = "GetPassageActivity"
        private const val INTENT_GET_PASSAGE = "net.bible.android.action.GET_PASSAGE"
        private const val INTENT_PUT_PASSAGE = "net.bible.android.action.PUT_PASSAGE"
        private const val INTENT_ERROR_MESSAGE = "net.bible.android.action.ERROR_MESSAGE"
        private const val GET_PASSAGE_SEARCH_STRING = "search_string"
        
        // Safety limit for Intent extras to avoid TransactionTooLargeException crashing the process
        private const val MAX_INTENT_EXTRA_SIZE = 100_000
    }
}
