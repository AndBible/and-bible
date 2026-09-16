package net.bible.android.view.activity.page

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading-host re-typing R5: the eight easy collaborators (`PageTiltScrollControl`,
 * `LlmDialogHelper`, `DocumentViewManager`, `BibleViewFactory`, `BibleGestureListener`, and the
 * three `OptionsMenuItems` preference classes) each held a `MainBibleActivity`-typed value. This
 * guard is the net: no scanned file may declare a `MainBibleActivity`-typed parameter, AND no
 * scanned file may bridge the gap with a local `as MainBibleActivity` downcast either.
 *
 * **Fix round 1 (review) added the second assertion.** The first version only scanned for the
 * declared type (`: MainBibleActivity`) and went green on `DocumentViewManager`/`BibleViewFactory`,
 * which had merely been re-labelled: both still reached the full `MainBibleActivity` surface
 * through `(x as MainBibleActivity)` at every access. A parameter typed `Any`, or one spelled with
 * a fully-qualified name, would have passed the old assertion just as easily -- the cast is the
 * real tell that a "retype" changed nothing behind the signature.
 *
 * **`BibleViewFactory.kt` is deliberately EXCLUDED from `files` below, not merely left unchecked.**
 * It is reverted to `MainBibleActivity` and reported BLOCKED for R6 in the fix-round report: its
 * only reason for holding the value is to hand the WHOLE thing to `BibleView`'s constructor, which
 * reaches upwards of a dozen distinct `MainBibleActivity`-only members (`readingInsets` twice,
 * `isSplitVertically`, `showLlmPromptSelector`, `composeSearchIfHosted`, `currentNightMode`,
 * `startActivityForResult`, `awaitIntent`, ...). Decomposing that is real work on a 2000+ line file
 * this task cannot verify with its own scoped `--tests` filter -- R6's job, not R5's mechanical,
 * no-bodies-move scope. [theExcludedFileIsNamedAndStillNeedsMainBibleActivity] below keeps that
 * exclusion visible and pins its reason to the actual code, rather than a silent gap in `files`.
 */
class CollaboratorTypeGuardTest {
    private val files = listOf(
        "src/main/java/net/bible/android/control/page/PageTiltScrollControl.kt",
        "src/main/java/net/bible/android/view/activity/ai/LlmDialogHelper.kt",
        "src/main/java/net/bible/android/view/activity/page/screen/DocumentViewManager.kt",
        "src/main/java/net/bible/android/view/activity/page/BibleGestureListener.kt",
        "src/main/java/net/bible/android/view/activity/page/OptionsMenuItems.kt",
    )

    /** The one collaborator this batch could not decouple without body-level surgery -- see the
     * class KDoc above and `BibleViewFactory.kt`'s own KDoc for why. */
    private val excludedFile = "src/main/java/net/bible/android/view/activity/page/BibleViewFactory.kt"

    @Test
    fun theScannedFilesExist() = files.forEach { assertTrue("$it not found", File(it).exists()) }

    @Test
    fun noEasyCollaboratorDeclaresAMainBibleActivityParameter() {
        val offenders = files.filter { path ->
            Regex(""":\s*MainBibleActivity\b""").containsMatchIn(File(path).readText())
        }
        assertEquals("R5 re-types these off the Activity", emptyList<String>(), offenders)
    }

    @Test
    fun noEasyCollaboratorBridgesTheGapWithADowncast() {
        val offenders = files.filter { path -> File(path).readText().contains("as MainBibleActivity") }
        assertEquals(
            "a retype that still downcasts to the full Activity every time it is used is a " +
                "re-label, not a re-type (review Critical 1) -- push the needed value in as a " +
                "constructor argument instead, or exclude the file here and report it blocked",
            emptyList<String>(), offenders,
        )
    }

    @Test
    fun theExcludedFileIsNamedAndStillNeedsMainBibleActivity() {
        assertTrue("$excludedFile not found", File(excludedFile).exists())
        assertTrue(
            "$excludedFile is listed as excluded because it still needs the full " +
                "MainBibleActivity surface (to pass to BibleView) -- if that is no longer true, " +
                "move it back into `files` above instead of leaving a stale exclusion",
            Regex(""":\s*MainBibleActivity\b""").containsMatchIn(File(excludedFile).readText()),
        )
    }
}
