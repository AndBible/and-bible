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
 * `ReadingHostState.quickSheet` holds, and the Android host binds that slot to the common
 * `ReadingHostSlots.QuickSheet`, whose `QuickSheetContent` (QuickSheets.kt) is where every
 * `AbQuickSheet` lives: the Android host mounts none.
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

    @Test fun theQuickSheetSlotIsBoundOnceToTheCommonSlot() {
        val src = sourceWithoutComments()
        assertTrue("the common ReadingHost must exist at ${commonHost.path}", commonHost.exists())
        val common = withoutComments(commonHost)
        assertEquals("the Android host no longer declares a QuickSheetContent", 0,
            Regex("""fun QuickSheetContent\(""").findAll(src).count())
        // Note the case: `QuickSheetContent(` is the composable, `quickSheetContent` the slot PARAMETER.
        assertEquals(
            "exactly one binding of the host slot to the common slot",
            1, Regex("""quickSheetContent\s*=\s*\{\s*sheet\s*->\s*slots\.QuickSheet\(sheet\)\s*\}""").findAll(src).count(),
        )
        assertEquals("the host calls slots.QuickSheet nowhere else", 1, Regex("""slots\.QuickSheet\(""").findAll(src).count())
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
        // History, Workspaces, Documents, the map/general-book list and the grid chooser. Share and
        // ReadHistory mount through their own sheet composables, which are not AbQuickSheet calls here.
        assertEquals("the scan must find exactly the sheets it guards", 5,
            Regex("""\bAbQuickSheet\(""").findAll(src).count())
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

    private fun mountCount(src: String) = Regex("""\bAbQuickSheet\(""").findAll(src).count()

    @Test fun theAndroidHostAndTheCommonHostMountNoQuickSheet() {
        val src = sourceWithoutComments()
        // NOTE: do not spell the shell composable's name directly followed by an open paren
        // anywhere in this file, comments included -- SettingsEditorSheetGuardTest's
        // noGoldenTestCapturesSettingsEditorSheet scans this whole package by raw text for exactly
        // that substring, and it would self-report as a false positive.
        assertTrue("the host source must be non-trivial", src.length > 10_000)
        assertEquals("the Android host mounts a quick sheet", 0, mountCount(src))
        assertEquals("the common host mounts a quick sheet", 0, mountCount(withoutComments(commonHost)))
    }

    @Test fun aPlantedMountInTheAndroidHostIsCaught() {
        val shell = "AbQuick" + "Sheet"
        val planted = "private fun QuickSheetContent(sheet: Any) { $shell(open = true) }"
        assertEquals("the planted mount must be seen by the host scan", 1, mountCount(planted))
    }
}
