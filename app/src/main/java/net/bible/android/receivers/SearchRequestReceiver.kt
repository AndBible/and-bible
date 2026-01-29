package net.bible.android.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import net.bible.android.view.activity.search.Search

class SearchRequestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("SearchRequestReceiver", "Received broadcast")
        if (intent.action == ACTION_SEARCH) {
            val query = intent.getStringExtra(EXTRA_QUERY)
            Log.i("SearchRequestReceiver", "Query: $query")
            if (query != null) {
                val searchIntent = Intent(context, Search::class.java).apply {
                    putExtra(Search.SEARCH_STRING, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(searchIntent)
                Log.i("SearchRequestReceiver", "Search intent started")
            }
        }
    }

    companion object {
        const val ACTION_SEARCH = "net.bible.android.action.SEARCH"
        const val EXTRA_QUERY = "net.bible.android.extra.QUERY"
    }
}
