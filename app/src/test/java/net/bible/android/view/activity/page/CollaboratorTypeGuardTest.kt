package net.bible.android.view.activity.page

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading-host re-typing R5: the eight easy collaborators (`PageTiltScrollControl`,
 * `LlmDialogHelper`, `DocumentViewManager`, `BibleViewFactory`, `BibleGestureListener`, and the
 * three `OptionsMenuItems` preference classes) each held a `MainBibleActivity`-typed value. This
 * guard is the net: no scanned file may declare a `MainBibleActivity`-typed parameter any more.
 *
 * It scans DECLARED TYPES only (`: MainBibleActivity`) -- a local downcast (`as MainBibleActivity`)
 * inside a file that has already been retyped is not a declared parameter and does not trip this
 * guard; see the R5 report for exactly which accesses still need one and why.
 */
class CollaboratorTypeGuardTest {
    private val files = listOf(
        "src/main/java/net/bible/android/control/page/PageTiltScrollControl.kt",
        "src/main/java/net/bible/android/view/activity/ai/LlmDialogHelper.kt",
        "src/main/java/net/bible/android/view/activity/page/screen/DocumentViewManager.kt",
        "src/main/java/net/bible/android/view/activity/page/BibleViewFactory.kt",
        "src/main/java/net/bible/android/view/activity/page/BibleGestureListener.kt",
        "src/main/java/net/bible/android/view/activity/page/OptionsMenuItems.kt",
    )

    @Test
    fun theScannedFilesExist() = files.forEach { assertTrue("$it not found", File(it).exists()) }

    @Test
    fun noEasyCollaboratorDeclaresAMainBibleActivityParameter() {
        val offenders = files.filter { path ->
            Regex(""":\s*MainBibleActivity\b""").containsMatchIn(File(path).readText())
        }
        assertEquals("R5 re-types these off the Activity", emptyList<String>(), offenders)
    }
}
