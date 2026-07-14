package net.bible.android.view.activity.search

import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.parseHighlightHtml
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AndroidEpubSearchServiceTest {
    // The requery SQLite driver is off the unit-test classpath, so the live FTS5 search is not
    // exercisable here (same guard as EpubBook/EpubSearch). This locks the highlight->StyledText
    // mapping the host relies on; the live search is on-device A/B (deferred new-verified).
    @Test fun highlight_html_maps_to_bold_run() {
        val st = parseHighlightHtml("say <b>peace</b>")
        assertEquals(listOf(StyledRun("say ", false), StyledRun("peace", true)), st.runs)
    }
}
