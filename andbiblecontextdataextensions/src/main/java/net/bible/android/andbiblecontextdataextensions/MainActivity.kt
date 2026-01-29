package net.bible.android.andbiblecontextdataextensions

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import net.bible.android.andbiblecontextdataextensions.databinding.ActivityAbcdeConfiguratorBinding

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityAbcdeConfiguratorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        binding = ActivityAbcdeConfiguratorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "Layout inflated and content view set.")

        binding.buttonSend.setOnClickListener {
            val query = binding.editTextQuery.text.toString()
            Log.i(TAG, "Search button clicked with query: $query")
            if (query.isNotEmpty()) {
                launchSearchInMainApp(query)
            }
        }
        Log.d(TAG, "setOnClickListener has been called on buttonSend.")
    }

    private fun launchSearchInMainApp(query: String) {
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

    companion object {
        private const val TAG = "abcde_configurator"
    }
}
