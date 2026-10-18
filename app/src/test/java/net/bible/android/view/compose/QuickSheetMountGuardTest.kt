package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 15b: the quick sheets (four then five since Task 26's `ReadingQuickSheet.Share`, six since
 * Task 27's `ReadingQuickSheet.ReadHistory`) share ONE state and must therefore share ONE mount
 * point. A second `QuickSheetContent(...)` call -- the most plausible way this design decays -- fails
 * here.
 *
 * The mount point is split across two files since the composition glue moved to `:sharedUi`: the
 * common `ReadingHost` hands `deps.quickSheetContent` to one `QuickSheetMount`, which invokes it once, for the sheet that
 * `ReadingHostState.quickSheet` holds, and the Android host binds that slot to its one
 * `QuickSheetContent` composable, which is where every `AbQuickSheet` lives.
 */
class QuickSheetMountGuardTest {
    private val host = net.bible.test.ProductionSources.resolve("src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt")
    private val commonHost = File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/host/ReadingHost.kt")

    private fun withoutComments(file: File): String =
        file.readLines()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
            .joinToString("\n")

    private fun sourceWithoutComments(): String = withoutComments(host)

    @Test fun theQuickSheetSlotIsDeclaredOnceAndBoundOnce() {
        val src = sourceWithoutComments()
        assertTrue("the common ReadingHost must exist at ${commonHost.path}", commonHost.exists())
        val common = withoutComments(commonHost)
        assertTrue("the host must declare a QuickSheetContent", src.contains("private fun QuickSheetContent("))
        // Three precise counts, not one loose one. Note the case: `QuickSheetContent(` is the
        // composable, `quickSheetContent(` is the slot PARAMETER -- a case-insensitive or sloppy
        // pattern conflates them and the assertion stops meaning anything.
        assertEquals(
            "exactly one QuickSheetContent declaration",
            1, Regex("""private fun QuickSheetContent\(""").findAll(src).count(),
        )
        assertEquals(
            "exactly one binding of the host slot to the composable",
            1, Regex("""quickSheetContent\s*=\s*\{\s*sheet\s*->\s*QuickSheetContent\(sheet\)\s*\}""").findAll(src).count(),
        )
        assertEquals(
            "the slot is handed to the one QuickSheetMount, exactly once",
            1, Regex("""QuickSheetMount\(state,\s*deps\.quickSheetContent\)""").findAll(common).count(),
        )
        assertEquals(
            "QuickSheetMount invokes the slot exactly once",
            1, Regex("""(?<![\w.])content\(sheet\)""").findAll(common).count(),
        )
        assertEquals("no other read of the slot", 1, Regex("""deps\.quickSheetContent""").findAll(common).count())
    }

    @Test fun noQuickSheetIsMountedOutsideTheSlot() {
        val src = sourceWithoutComments()
        val slotStart = src.indexOf("private fun QuickSheetContent(")
        assertTrue("the host must declare QuickSheetContent", slotStart >= 0)
        val abQuickSheetUses = Regex("""\bAbQuickSheet\(""").findAll(src).map { it.range.first }.toList()
        assertTrue("the scan must find the sheets it guards", abQuickSheetUses.isNotEmpty())
        abQuickSheetUses.forEach { at ->
            // NOTE: do not spell the shell composable's name directly followed by an open paren
            // anywhere in this file, comments included -- SettingsEditorSheetGuardTest's
            // noGoldenTestCapturesSettingsEditorSheet (whole-branch review fix M3) scans this whole
            // package by raw text (not comment-stripped) for exactly that substring, and it would
            // self-report as a false positive the same way that guard's own kdoc already explains
            // for its own source.
            assertTrue("an AbQuickSheet call at $at is outside QuickSheetContent", at > slotStart)
        }
        // And the common host mounts none itself: it only decides WHEN one is open.
        assertEquals(0, Regex("""\bAbQuickSheet\(""").findAll(withoutComments(commonHost)).count())
    }
}
