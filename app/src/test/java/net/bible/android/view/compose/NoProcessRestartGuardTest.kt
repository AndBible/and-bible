package net.bible.android.view.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fix batch 5 §1.2 (F112): changing the UI language must never restart the process again.
 *
 * `exitProcess(2)` itself stays legal (`forceStopApp`, the poor-translations refusal); what is banned
 * is the restart SHAPE: `restartApp(`, the `localeOverrideAtStartUp` snapshot it compared against,
 * and an `exitProcess(` in a file that also schedules a relaunch through `AlarmManager`.
 */
class NoProcessRestartGuardTest {
    private val roots = listOf(File("src/main/java"))

    internal fun offenders(files: List<File>): List<String> = files
        .filter { it.extension == "kt" }
        .flatMap { f ->
            val t = f.readText()
            buildList {
                if ("restartApp(" in t) add("${f.path}: restartApp(")
                if ("localeOverrideAtStartUp" in t) add("${f.path}: localeOverrideAtStartUp")
                if ("exitProcess(" in t && "AlarmManager" in t) add("${f.path}: exitProcess( + AlarmManager")
            }
        }

    @Test fun noProcessRestart() =
        assertEquals(emptyList<String>(), offenders(roots.flatMap { it.walkTopDown().toList() }))

    @Test fun theScanSeesTheSources() =
        assertTrue("run from app/ -- the scan would pass vacuously", roots.all { it.isDirectory && it.walkTopDown().any { f -> f.extension == "kt" } })

    @Test fun theGuardCanFail() {
        val probe = File.createTempFile("restartProbe", ".kt").apply {
            writeText("fun x(a: AlarmManager) { restartApp(a); localeOverrideAtStartUp; exitProcess(2) }")
            deleteOnExit()
        }
        assertEquals(3, offenders(listOf(probe)).size)
    }
}
