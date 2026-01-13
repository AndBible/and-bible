package net.bible.android.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import net.bible.android.BibleApplication
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.DaggerActivityComponent
import javax.inject.Inject
import android.widget.Toast;

import net.bible.android.control.document.DocumentControl;

/**
 * An activity to handle text selection and get a Bible quote from it.
 * This is triggered by the `android.intent.action.PROCESS_TEXT` intent.
 */
class ProcessTextActivity : ComponentActivity() {
    @Inject lateinit var documentControl: DocumentControl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ProcessTextActivity created")

        DaggerActivityComponent.builder()
            .applicationComponent(BibleApplication.application.applicationComponent)
            .build().inject(this)

        handleIntent(intent)

        // Finish the activity immediately after processing the text
        finish()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_PROCESS_TEXT) {
            val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            val isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)

            Log.d(TAG, "Received text to process: '$selectedText', isReadOnly: $isReadOnly")

            if (!selectedText.isNullOrEmpty()) {
                val theCite = selectedText.toString();
                val theBible = documentControl.suggestedBible;
                if(theBible == null) {
                    val theMsg = "No Bible selected";
                    Log.w(TAG, theMsg);
                    Toast.makeText(this, theMsg, Toast.LENGTH_SHORT).show();
                    return;
                }
                val theKey = theBible.getKey(theCite);
                if(theKey == null){
                    val theMsg = "No verse found for '$theCite'";
                    Log.w(TAG, theMsg);
                    Toast.makeText(this, theMsg, Toast.LENGTH_SHORT).show();
                    return;
                }
                val theRawQuote = theBible.getRawText(theKey);
                // val theQuote = preprocess(theRawQuote, html=false, linebreaks=true, pilcrows=true, verseNumbers="\${i}");
                val theQuote = theRawQuote;
                val out = theQuote + theCite;
                // if not readonly, replace cite with quote, otherwise put quote in clipboard
                if(!isReadOnly){
                    intent.putExtra(Intent.EXTRA_PROCESS_TEXT, out)
                } else {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager;
                    val clip = android.content.ClipData.newPlainText("Quote", out);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "$theCite copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.w(TAG, "No text selected, this should be impossible?");
            }
        } else {
            Log.w(TAG, "Intent action is not PROCESS_TEXT: ${intent?.action}")
        }
    }

    companion object {
        private const val TAG = "ProcessTextActivity"
    }
}
