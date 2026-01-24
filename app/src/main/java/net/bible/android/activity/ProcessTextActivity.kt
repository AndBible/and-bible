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

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader


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

            Log.i(TAG, "Cite2Quote: got cite '$selectedText', isReadOnly: $isReadOnly")

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
                val theQuote = preprocess(theRawQuote, linebreaks=true, pilcrows=true, verseNumbers=true, chevrons=true);
                Log.i(TAG, "Cite2Quote: quote is '$theQuote'");
                val out = theQuote + ' ' + theCite;
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

    public fun preprocess(
        xmlInput: String,
        linebreaks: Boolean,
        pilcrows: Boolean,
        verseNumbers: Boolean,
        chevrons: Boolean
    ): String {
        val factory = XmlPullParserFactory.newInstance()
        val xpp = factory.newPullParser()
        xpp.setInput(StringReader("<root>$xmlInput</root>")) // Wrapped in root for valid XML
        val linebreak = if (chevrons) "\n> " else "\n"

        val result = StringBuilder()
        var eventType = xpp.eventType
        val verseBuf = StringBuilder()
        var verseHasTextNow = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (xpp.name) {
                        "verse" -> {
                            // Extract the verse number from osisID (e.g., "John.1.6" -> "6")
                            val osisId = xpp.getAttributeValue(null, "osisID")
                            osisId?.split('.')?.lastOrNull()?.let {
                                result.append(verseBuf.toString())
                                verseBuf.clear()
                                verseHasTextNow = false
                                if(verseNumbers){
                                    verseBuf.append(it)
                                }
                            }
                        }
                        "milestone" -> {
                            // Check for the paragraph marker
                            if (xpp.getAttributeValue(null, "marker") == "¶") {
                                if(linebreaks) {
                                    if (!verseHasTextNow) {
                                        verseBuf.insert(0, linebreak)
                                    } else {
                                        verseBuf.append(linebreak)
                                    }
                                }
                                if(pilcrows){
                                    verseBuf.append("¶")
                                }
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    // This captures text inside <w> tags and stray punctuation
                    verseBuf.append(xpp.text)
                    verseHasTextNow = true
                }
            }
            eventType = xpp.next()
        }
        result.append(verseBuf.toString())
        return result.toString();
    }

    companion object {
        private const val TAG = "ProcessTextActivity"
    }
}
