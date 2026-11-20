package net.bible.android.view.widget

import android.app.Application
import android.view.View
import android.widget.ImageView
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.android.control.widget.ListeningRow
import net.bible.android.platform.AndroidWidgetStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = Application::class)
class ListeningListGlanceWidgetTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val strings get() = AndroidWidgetStrings(context)

    private fun list(w: Int, h: Int, rows: List<ListeningRow>) =
        renderWidget(context, w, h) { ListeningListContent(stoppedState().copy(listening = rows), strings, w.dp, h.dp) }

    /** Progress bars are the only FIT_XY images; their window x and width. */
    private fun bars(v: View): List<Pair<Int, Int>> = v.allViews().filterIsInstance<ImageView>()
        .filter { it.scaleType == ImageView.ScaleType.FIT_XY }
        .map { bar ->
            var x = 0; var p: View? = bar
            while (p != null && p !== v) { x += p.left; p = p.parent as? View }
            x to bar.width
        }

    @Test fun rowsShowReferenceTranslationRepeatAndBar() {
        val v = list(334, 278, listOf(row("Psalm 23:1", "WEB", 34, repeats = true), row("John 3:16", "KJV", 80)))
        assertTrue(v.texts().containsAll(listOf("Psalm 23:1", "WEB", "John 3:16", "KJV")))
        assertEquals(2, bars(v).size)
        assertTrue(v.texts().contains("2"))
    }

    /** Review Focus 3: long names never move or resize the bar. */
    @Test fun longNamesDoNotChangeBarGeometry() {
        val v = list(334, 278, listOf(
            row("Psalm 23:1", "WEB", 34, id = "a"),
            row("Pilgrim's Progress, Part 1", "PilgrimsProgress", 34, id = "b"),
            row("Genesis 1:1", "WEB", null, id = "c"),
            row("Romans 8:1", "FinRK", 12, repeats = true, current = true, id = "d"),
        ))
        val bars = bars(v)
        assertEquals("the generic bookmark draws no bar", 3, bars.size)
        assertEquals("bar start and length are identical on every row", 1, bars.toSet().size)
        val tv = v.allViews().filterIsInstance<android.widget.TextView>().first { it.text.toString() == "PilgrimsProgress" }
        assertEquals("one-line ellipsised translation", 1, tv.maxLines)
    }

    @Test fun compactShowsNoTranslation() {
        val v = list(162, 162, listOf(row("Psalm 23:1", "WEB", 34)))
        assertTrue(v.texts().contains("Psalm 23:1"))
        assertFalse(v.texts().contains("WEB"))
        assertEquals(1, bars(v).size)
    }

    @Test fun emptyListShowsExplanation() {
        val v = list(334, 182, emptyList())
        assertTrue(v.texts().contains(strings.listeningListEmptyTitle))
        assertTrue(v.texts().contains(strings.listeningListEmptyText))
        assertNotNull("tapping the empty widget opens the app", v.rootClickable())
    }

    @Test fun currentRowShowsPause() {
        val v = list(334, 278, listOf(row("Psalm 23:1", "WEB", current = true), row("John 3:16", "KJV")))
        assertEquals(1, v.allViews().count { it.contentDescription?.toString() == context.getString(R.string.pause) })
        assertNull(list(334, 278, listOf(row("John 3:16", "KJV"))).byDescription(context.getString(R.string.pause)))
    }

    @Test fun onlyRowsThatFitAreDrawn() {
        val rows = (1..9).map { row("Psalm $it:1", "WEB", id = "r$it") }
        val shown = list(334, 182, rows).texts().count { it.startsWith("Psalm ") }
        assertTrue("fewer than all nine rows fit 182 dp (shown $shown)", shown in 1..4)
    }
}
