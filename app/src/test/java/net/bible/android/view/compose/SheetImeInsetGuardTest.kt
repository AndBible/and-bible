package net.bible.android.view.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fix batch 2, correction C1. Sheet windows are edge-to-edge + ADJUST_NOTHING from API 30 (material3
 * 1.4.0 `ModalBottomSheetDialogWrapper`), and the ONLY thing that lifts a text field in them is
 * `ModalBottomSheet`'s default `contentWindowInsets` (`BottomSheetDefaults.windowInsets` =
 * `safeDrawing.only(Top + Bottom)`, which includes `ime`). Overriding it would silently un-lift
 * LabelIdentitySheet, AbCreateItemSheet, AbSearchablePicker and the rest.
 */
class SheetImeInsetGuardTest {
    private val roots = listOf(File("../sharedUi/src/commonMain/kotlin"), File("src/main/java"))

    internal fun offenders(files: List<File>): List<String> = files
        .filter { it.extension == "kt" }
        .filter { f -> f.readText().let { "ModalBottomSheet(" in it && Regex("""contentWindowInsets\s*=""").containsMatchIn(it) } }
        .map { it.path }

    @Test fun noSheetOverridesItsContentInsets() =
        assertEquals(emptyList<String>(), offenders(roots.flatMap { it.walkTopDown().toList() }))

    @Test fun theScanSeesTheSources() =
        assertTrue("run from app/ -- the scan would pass vacuously", roots.all { it.isDirectory })

    @Test fun theGuardCanFail() {
        val probe = File.createTempFile("sheetProbe", ".kt").apply {
            writeText("ModalBottomSheet(onDismissRequest = {}, contentWindowInsets = { WindowInsets(0) }) {}")
            deleteOnExit()
        }
        assertEquals(listOf(probe.path), offenders(listOf(probe)))
    }
}
