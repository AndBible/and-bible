package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 15b Task 9: `ComposeReadingViewHost.currentKeyChooserPage()` classifies the active JSword
 * page for `KeyChooserRoute`, and its `is` chain has an ordering trap that no type checker and no
 * `:sharedCore` test can see.
 *
 * `CurrentMyNotePage` EXTENDS `CurrentCommentaryPage` and does not override `startKeyChooser`, so a
 * chain that tests the commentary branch first silently classifies every my-note page as a
 * commentary. Today both route to the same Grid sheet, so the bug would be invisible — which is
 * exactly why it is worth pinning now, before the two ever diverge (see `KeyChooserPage`'s kdoc).
 *
 * A source-shape assertion is crude, but the alternative — constructing a real `CurrentMyNotePage`,
 * which needs a `CurrentPageManager`, a `BibleTraverser` and a live Bible document — costs orders of
 * magnitude more to set up than the defect costs to state.
 */
class KeyChooserDispatchGuardTest {
    private val host = File("src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt")

    private fun dispatchBody(): String {
        val src = host.readText()
        val start = src.indexOf("internal fun currentKeyChooserPage()")
        assertTrue("the host must declare currentKeyChooserPage()", start >= 0)
        val end = src.indexOf("\n    }", start)
        assertTrue("currentKeyChooserPage() must have a body", end > start)
        return src.substring(start, end)
    }

    @Test fun theMyNoteBranchIsTestedBeforeTheCommentaryBranchItExtends() {
        val body = dispatchBody()
        val myNote = body.indexOf("is CurrentMyNotePage ->")
        val commentary = body.indexOf("is CurrentCommentaryPage ->")
        assertTrue("currentKeyChooserPage() must classify CurrentMyNotePage", myNote >= 0)
        assertTrue("currentKeyChooserPage() must classify CurrentCommentaryPage", commentary >= 0)
        assertTrue(
            "CurrentMyNotePage extends CurrentCommentaryPage, so its branch must come FIRST " +
                "(my-note at $myNote, commentary at $commentary)",
            myNote < commentary,
        )
    }

    /** The other subtype pair in the same chain: every page shape KeyChooserPage names is decided. */
    @Test fun everyPageShapeIsClassified() {
        val body = dispatchBody()
        listOf(
            "is CurrentBiblePage ->", "is CurrentMyNotePage ->", "is CurrentCommentaryPage ->",
            "is CurrentDictionaryPage ->", "is CurrentMapPage ->", "is CurrentGeneralBookPage ->",
        ).forEach { branch ->
            assertTrue("currentKeyChooserPage() must contain `$branch`", body.contains(branch))
        }
    }
}
