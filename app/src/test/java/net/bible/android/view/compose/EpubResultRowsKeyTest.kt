package net.bible.android.view.compose

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.junit4.createComposeRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.search.EpubResultRow
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedui.search.epubResultRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class EpubResultRowsKeyTest {
    @get:Rule val composeRule = createComposeRule()

    private fun row(ordinal: Int) = EpubResultRow(
        keyId = "Epub-book:3",
        ordinal = ordinal,
        keyName = "Chapter 3",
        text = StyledText(listOf(StyledRun("a hit"))),
    )

    /** Two hits in ONE fragment: the exact shape that threw "Key … was already used" on device. */
    @Test
    fun twoHitsInOneFragmentRender() {
        composeRule.setContent {
            LazyColumn { epubResultRows(rows = listOf(row(11), row(12)), onSelect = { _, _ -> }) }
        }
        composeRule.waitForIdle()
    }
}
