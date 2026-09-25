package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 15b: the quick sheets (four then five since Task 26's `ReadingQuickSheet.Share`, six since
 * Task 27's `ReadingQuickSheet.ReadHistory`) share ONE state and must therefore share ONE mount
 * point. A second `QuickSheetSlot()` call — the most plausible way this design decays — fails here.
 */
class QuickSheetMountGuardTest {
    private val host = File("src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt")

    private fun sourceWithoutComments(): String =
        host.readLines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
            .joinToString("\n")

    @Test fun theQuickSheetSlotIsDeclaredOnceAndBoundOnce() {
        val src = sourceWithoutComments()
        assertTrue("the host must declare a QuickSheetSlot", src.contains("private fun QuickSheetSlot()"))
        // Three precise counts, not one loose one. Note the case: `QuickSheetSlot()` is the
        // composable, `quickSheetSlot()` is the slot PARAMETER — a case-insensitive or sloppy
        // pattern conflates them and the assertion stops meaning anything.
        assertEquals(
            "exactly one QuickSheetSlot declaration",
            1, Regex("""private fun QuickSheetSlot\(\)""").findAll(src).count(),
        )
        assertEquals(
            "exactly one binding of the host slot to the composable",
            1, Regex("""quickSheetSlot\s*=\s*\{\s*QuickSheetSlot\(\)\s*\}""").findAll(src).count(),
        )
        assertEquals(
            "the slot parameter is invoked exactly once in the composition",
            1, Regex("""(?<![\w.])quickSheetSlot\(\)""").findAll(src).count(),
        )
    }

    @Test fun noQuickSheetIsMountedOutsideTheSlot() {
        val src = sourceWithoutComments()
        val slotStart = src.indexOf("private fun QuickSheetSlot()")
        val abQuickSheetUses = Regex("""\bAbQuickSheet\(""").findAll(src).map { it.range.first }.toList()
        abQuickSheetUses.forEach { at ->
            // NOTE: do not spell the shell composable's name directly followed by an open paren
            // anywhere in this file, comments included -- SettingsEditorSheetGuardTest's
            // noGoldenTestCapturesSettingsEditorSheet (whole-branch review fix M3) scans this whole
            // package by raw text (not comment-stripped) for exactly that substring, and it would
            // self-report as a false positive the same way that guard's own kdoc already explains
            // for its own source.
            assertTrue("an AbQuickSheet call at $at is outside QuickSheetSlot", at > slotStart)
        }
    }
}
