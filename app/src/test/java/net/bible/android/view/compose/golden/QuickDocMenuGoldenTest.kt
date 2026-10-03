package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import net.bible.android.TEST_SDK
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.reading.QuickDocMenuItem
import net.bible.sharedui.reading.QuickDocMenuRows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers Batch 12g Task 6's [net.bible.sharedui.reading.QuickDocMenu] (the reading-view toolbar's
 * quick-document picker).
 *
 * NOTE on capture approach: `QuickDocMenu` itself is a thin wrapper around a Material3
 * `DropdownMenu` (a `Popup`, i.e. a second window). This golden instead captures
 * [QuickDocMenuRows] - the exact same row-building composable `QuickDocMenu` calls internally,
 * wrapped here in a plain `Column` instead of the real popup - for the same reason documented on
 * `ReadingOverflowMenuGoldenTest`: force-opening a real Compose `DropdownMenu` under
 * Robolectric/Roborazzi has repeatedly, intermittently HUNG this repo's golden capture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class QuickDocMenuGoldenTest {

    /**
     * A Bible, a commentary and a dictionary - exactly the mixture the Commentary button produces
     * (classic parity: `commentariesForVerse + getBooks(GENERAL_BOOK) + getBooks(DICTIONARY)`), with
     * the commentary disabled to also cover the dimmed-icon state.
     */
    private val items = listOf(
        QuickDocMenuItem(id = "ESV", label = "English Standard Version", enabled = true, category = DocCategory.BIBLE),
        QuickDocMenuItem(id = "MHC", label = "Matthew Henry's Commentary", enabled = false, category = DocCategory.COMMENTARY),
        QuickDocMenuItem(id = "EAST", label = "Easton's Bible Dictionary", enabled = true, category = DocCategory.DICTIONARY),
    )

    @Test fun items_matrix() =
        captureMatrix("QuickDocMenu", "items") {
            Column { QuickDocMenuRows(items, onSelect = {}) }
        }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun items_rtl() =
        captureRtl("QuickDocMenu", "items") {
            Column { QuickDocMenuRows(items, onSelect = {}) }
        }
}
