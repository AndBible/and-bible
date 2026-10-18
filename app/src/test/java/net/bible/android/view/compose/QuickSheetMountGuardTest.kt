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
    private val slotsDir = File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/host/slots")
    private val quickSheets = File(slotsDir, "QuickSheets.kt")
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

    /** Offsets of every shell-composable call that sits BEFORE the slot's declaration in [src]. */
    private fun mountsOutsideSlot(src: String, slotDeclaration: String): List<Int> {
        val slotStart = src.indexOf(slotDeclaration)
        assertTrue("the source must declare $slotDeclaration", slotStart >= 0)
        return Regex("""\bAbQuickSheet\(""").findAll(src).map { it.range.first }.filter { it < slotStart }.toList()
    }

    @Test fun theCommonSlotDeclaresOneQuickSheetContentAndEveryMountIsInsideIt() {
        assertTrue("QuickSheets.kt must exist at ${quickSheets.path}", quickSheets.exists())
        val src = withoutComments(quickSheets)
        val decl = "internal fun QuickSheetContent(slots: ReadingHostSlots, sheet: ReadingQuickSheet)"
        assertEquals("exactly one slot declaration", 1, Regex(Regex.escape(decl)).findAll(src).count())
        assertTrue("the scan must find the sheets it guards", Regex("""\bAbQuickSheet\(""").findAll(src).count() >= 3)
        assertEquals("no AbQuickSheet call precedes the slot", emptyList<Int>(), mountsOutsideSlot(src, decl))
        // The slot is reached through exactly one public member of ReadingHostSlots.
        val slots = withoutComments(File(slotsDir, "ReadingHostSlots.kt"))
        assertEquals(1, Regex("""QuickSheetContent\(this,\s*sheet\)""").findAll(slots).count())
    }

    @Test fun noOtherSlotFileMountsAQuickSheet() {
        val others = slotsDir.listFiles { f -> f.extension == "kt" && f.name != "QuickSheets.kt" }!!
        assertTrue("the scan must see the slot files", others.isNotEmpty())
        others.forEach {
            assertEquals("${it.name} mounts a quick sheet outside QuickSheets.kt", 0,
                Regex("""\bAbQuickSheet\(""").findAll(withoutComments(it)).count())
        }
    }

    @Test fun aPlantedMountOutsideTheSlotIsCaught() {
        // Spelled through [shell] so this file never contains the raw call text the golden-capture guard scans for.
        val shell = "AbQuick" + "Sheet"
        val decl = "internal fun QuickSheetContent("
        val planted = "private fun Rogue() { $shell(open = true) }\n$decl) { $shell(open = true) }"
        assertEquals("the planted mount must be reported", 1, mountsOutsideSlot(planted, decl).size)
        val clean = "$decl) { $shell(open = true) }\nprivate fun Later() { $shell(open = true) }"
        assertEquals(emptyList<Int>(), mountsOutsideSlot(clean, decl))
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
