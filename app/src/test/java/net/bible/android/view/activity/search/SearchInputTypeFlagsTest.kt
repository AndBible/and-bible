package net.bible.android.view.activity.search

import android.text.InputType
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SearchInputTypeFlagsTest {

    @Test
    fun searchLayoutDisablesSuggestionsAndKeepsSentenceCaps() {
        assertNoSuggestionsAndSentenceCaps(R.layout.search)
    }

    @Test
    fun epubSearchLayoutDisablesSuggestionsAndKeepsSentenceCaps() {
        assertNoSuggestionsAndSentenceCaps(R.layout.epub_search)
    }

    private fun assertNoSuggestionsAndSentenceCaps(layoutResId: Int) {
        val inputType = readInputType(layoutResId)

        assertTrue(
            "Expected textNoSuggestions in layout $layoutResId",
            inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        )
        assertTrue(
            "Expected textCapSentences in layout $layoutResId",
            inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
        )
        assertTrue(
            "Did not expect textAutoCorrect in layout $layoutResId",
            inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT == 0
        )
    }

    private fun readInputType(layoutResId: Int): Int {
        val parser = RuntimeEnvironment.getApplication().resources.getLayout(layoutResId)
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "EditText") {
                    return parser.getAttributeIntValue(
                        ANDROID_NS,
                        "inputType",
                        InputType.TYPE_CLASS_TEXT
                    )
                }
                eventType = parser.next()
            }
        } finally {
            parser.close()
        }
        error("EditText not found in layout $layoutResId")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
