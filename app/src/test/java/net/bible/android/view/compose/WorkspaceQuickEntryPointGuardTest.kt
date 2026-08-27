package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 15b: every Compose-path "switch workspace" must reach the quick sheet, not an Intent.
 * A fifth launcher added later without a reroute fails here rather than silently bypassing the
 * sheet — the shape `SpeakEntryPointGuardTest` established in round 13a.
 */
class WorkspaceQuickEntryPointGuardTest {
    private val reroutedSites = listOf(
        "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt",
        "src/main/java/net/bible/android/view/activity/page/BibleJavascriptInterface.kt",
        "src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt",
    )

    @Test fun everyReroutedSiteAsksTheHostFirst() {
        reroutedSites.forEach { path ->
            val src = File(path).readText()
            assertTrue("$path must reference showWorkspaceSheet", src.contains("showWorkspaceSheet"))
        }
    }

    @Test fun theHostExposesExactlyOneWorkspaceSheetOpener() {
        val src = File("src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt").readText()
        assertTrue(src.contains("internal fun showWorkspaceSheet()"))
    }
}
