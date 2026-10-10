package net.bible.sharedui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AbAlertDialogGuardTest {
    private val root = File("../sharedUi/src/commonMain/kotlin")
    private val bare = Regex("""(?<![A-Za-z])AlertDialog\(""")

    internal fun offenders(files: List<File>): List<String> = files
        .filter { it.extension == "kt" && it.name != "AbAlertDialog.kt" }
        .filter { bare.containsMatchIn(it.readText()) }
        .map { it.path }

    @Test fun everyDialogUsesAbAlertDialog() =
        assertEquals("route these through AbAlertDialog (monochrome border/scrim)",
            emptyList<String>(), offenders(root.walkTopDown().toList()))

    @Test fun theScanSeesTheSources() = assertTrue(root.isDirectory)

    @Test fun theGuardCanFail() {
        val probe = File.createTempFile("dialogProbe", ".kt").apply {
            writeText("AlertDialog(onDismissRequest = {})")
            deleteOnExit()
        }
        val fine = File.createTempFile("dialogFine", ".kt").apply {
            writeText("AbAlertDialog(onDismissRequest = {})\nBasicAlertDialog(onDismissRequest = {})")
            deleteOnExit()
        }
        assertEquals(listOf(probe.path), offenders(listOf(probe, fine)))
    }
}
