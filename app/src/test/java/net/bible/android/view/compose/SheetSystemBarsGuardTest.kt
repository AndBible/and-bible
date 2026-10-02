package net.bible.android.view.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fix batch 5 F106. Every sheet goes through `AbModalBottomSheet`, which mirrors the host's system bars
 * into the sheet's own dialog window. A bare `ModalBottomSheet(` leaks `hide_status_bar` / fullscreen.
 */
class SheetSystemBarsGuardTest {
    private val roots = listOf(File("../sharedUi/src/commonMain/kotlin"), File("src/main/java"))
    private val bare = Regex("""(?<![A-Za-z])ModalBottomSheet\(""")

    internal fun offenders(files: List<File>): List<String> = files
        .filter { it.extension == "kt" && it.name != "AbModalBottomSheet.kt" }
        .filter { bare.containsMatchIn(it.readText()) }
        .map { it.path }

    @Test fun everySheetUsesAbModalBottomSheet() =
        assertEquals(emptyList<String>(), offenders(roots.flatMap { it.walkTopDown().toList() }))

    @Test fun theScanSeesTheSources() =
        assertTrue("run from app/ -- the scan would pass vacuously", roots.all { it.isDirectory })

    @Test fun theGuardCanFail() {
        val probe = File.createTempFile("sheetProbe", ".kt").apply {
            writeText("ModalBottomSheet(onDismissRequest = {}) {}")
            deleteOnExit()
        }
        val fine = File.createTempFile("sheetFine", ".kt").apply {
            writeText("AbModalBottomSheet(onDismissRequest = {}) {}")
            deleteOnExit()
        }
        assertEquals(listOf(probe.path), offenders(listOf(probe, fine)))
    }
}
