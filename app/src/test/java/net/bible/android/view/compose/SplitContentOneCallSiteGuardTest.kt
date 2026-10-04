package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F64's structural half. The pane group must be composed from ONE call site, so that an orientation
 * change is a layout parameter rather than a move between two `key(w.id)` scopes -- which re-keys the
 * subtree, detaches the panes' cached `BibleView`s, and (below API 35, where the IME shrink used to flip
 * the orientation) makes the keyboard dismiss itself in a loop.
 *
 * Same idiom as `QuickSheetMountGuardTest`, which enforces the same invariant for the quick sheets.
 */
class SplitContentOneCallSiteGuardTest {
    private val splitContent =
        File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/SplitContent.kt")

    private fun sourceWithoutComments(): String {
        require(splitContent.exists()) { "${splitContent.path} not found -- this guard scans it" }
        return splitContent.readLines()
            .filterNot {
                it.trimStart().startsWith("//") || it.trimStart().startsWith("*") ||
                    it.trimStart().startsWith("/*")
            }
            .joinToString("\n")
    }

    @Test
    fun theHostedPaneIsInvokedFromExactlyOnePlace() {
        val src = sourceWithoutComments()
        assertTrue("the file must still declare a pane parameter", src.contains("pane: @Composable"))
        assertEquals(
            "`pane(w.id)` must appear exactly once -- two call sites mean an orientation flip re-keys " +
                "the pane subtree and destroys the panes' WebViews (F64)",
            1,
            Regex("""(?<![\w.])pane\(w\.id\)""").findAll(src).count(),
        )
        assertEquals(
            "`key(w.id)` must appear exactly once, for the same reason",
            1,
            Regex("""(?<![\w.])key\(w\.id\)""").findAll(src).count(),
        )
    }
}
