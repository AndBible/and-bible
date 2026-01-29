package net.bible.android.andbiblecontextdataextensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class ProcessTextHeadlessActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Headless activity created to process text.")

        if (intent?.action == Intent.ACTION_PROCESS_TEXT) {
            val query = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Log.i(TAG, "Received query from ACTION_PROCESS_TEXT: $query")

            if (!query.isNullOrEmpty()) {
                val searchIntent = Intent("net.bible.android.action.SEARCH").apply {
                    component = ComponentName(
                        "net.bible.android.activity",
                        "net.bible.android.view.activity.search.Search"
                    )
                    putExtra("search_string", query)
                    // Add flags to bring the launched app's task to the foreground.
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try {
                    startActivity(searchIntent)
                    Log.i(TAG, "startActivity called for search.")
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "AndBible app not found. Cannot launch search.", e)
                    Toast.makeText(this, "AndBible app not found. Please install it to use this feature.", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Immediately finish this activity so it is never visible to the user.
        finish()
    }

    companion object {
        private const val TAG = "ProcessTextHeadless"
    }
}
