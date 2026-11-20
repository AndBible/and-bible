package net.bible.android.view.widget

import android.app.Application
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.control.widget.VerseRef
import net.bible.android.control.widget.VerseSource
import net.bible.android.control.widget.VerseWidgetConfig
import net.bible.android.control.widget.VerseWidgetContent
import net.bible.android.platform.AndroidWidgetStrings
import net.bible.android.database.IdType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Goldens of the Verse widget (2x2, 4x2, 4x3, unavailable) and its configuration screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = Application::class, qualifiers = "w500dp-h900dp")
class VerseWidgetGoldenTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val strings get() = AndroidWidgetStrings(context)
    private val config = VerseWidgetConfig(source = VerseSource.Label(IdType()), seed = 1L)
    private val verse = VerseWidgetContent.Verse(
        ref = VerseRef("KJV", "KJV", 100, 100), reference = "Psalm 23:1",
        text = "The LORD is my shepherd; I shall not want. He maketh me to lie down in green pastures: he leadeth me beside the still waters.",
        bibleAbbreviation = "KJV", sourceName = "Memory verses", periodLabel = "Fri 20 Nov", link = "https://example.org/v",
    )

    @Test fun verseWidget_allModes() {
        for (mode in WidgetMode.entries) {
            for ((name, size) in listOf("2x2" to (162 to 162), "4x2" to (334 to 162), "4x3" to (334 to 278))) {
                val (w, h) = size
                captureWidget(context, "verse_$name", w, h, mode) { VerseWidgetBody(verse, config, strings, 1, w.dp, h.dp) }
            }
            captureWidget(context, "verse_4x2_nobuttons", 334, 162, mode) {
                VerseWidgetBody(verse, config.copy(showButtons = false, showSourceAndDate = false), strings, 1, 334.dp, 162.dp)
            }
            captureWidget(context, "verse_unavailable", 334, 162, mode) {
                VerseWidgetBody(VerseWidgetContent.Unavailable(VerseWidgetContent.Unavailable.Reason.LABEL_EMPTY), config, strings, 1, 334.dp, 162.dp)
            }
        }
    }
}
