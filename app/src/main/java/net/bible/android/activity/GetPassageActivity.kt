package net.bible.android.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.bible.android.BibleApplication
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.BookData
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
        try {
            val cite = intent.getStringExtra("search_string")
            Log.i(TAG, "Processing GET_PASSAGE intent, search_string is '$cite'")

            val bibleApplication = this.application as BibleApplication
            net.bible.service.db.DatabaseContainer.initializeDatabase()

            val appComponent = bibleApplication.applicationComponent
            val windowControl = appComponent.windowControl()
            SwordDocumentFacade.bibles.isEmpty() && return fail("No Bible selected")
            val activeBible = windowControl.defaultBibleDoc()
            val swordKey = activeBible.getKey(cite) ?: return fail("No verse found for '$cite'")
            val bookData = BookData(activeBible, swordKey)
            val quote = XMLUtil.writeToString(bookData.saxEventProvider)
            Log.d(TAG, "Successfully retrieved quote: '$quote'")
            val resultIntent = Intent(INTENT_PUT_PASSAGE).apply {
                putExtra("quote", quote)
                putExtra("citation", cite)
                putExtra("format", "application/xml+osis")
            }
            setResult(Activity.RESULT_OK, resultIntent)
        } finally {
            finish()
        }
    }

    private fun fail(msg: String) {
        Log.w(TAG, msg)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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
