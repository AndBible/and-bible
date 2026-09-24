package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice 8 finding M5: no InstallZip caller may wait on `awaitIntent` (or an ActivityResult launcher).
 * On the nav host such a wait is an F53-shaped self-launch the platform answers with an IMMEDIATE
 * synthetic `RESULT_CANCELED`, so the caller resumes before the install ran. InstallZip callers use
 * `NavHostComposeActivity.openInstallZip { result -> … }` instead (a `NavResultChannel<InstallZipResult>`).
 *
 * An "InstallZip marker" is `Screen.InstallZip`, `NavRoutes.installZip(` or `NavRoutes.INSTALL_ZIP_PATTERN`.
 * Offending shapes: the marker inside an `awaitIntent(…)` argument list; `val x = <marker…>` followed within
 * 600 chars by `awaitIntent(x)`; the marker inside a `.launch(…)` argument list (an ActivityResult launcher).
 *
 * [EXTERNAL_ACTIVITY_ALLOWLIST] names files whose waits run in a DIFFERENT Activity (a real result comes
 * back). It is a ratchet: each entry must still offend, and it is empty by the end of Phase F.
 */
class InstallZipAwaitGuardTest {

    private val marker = Regex("""Screen\.InstallZip\b|NavRoutes\.installZip\(|NavRoutes\.INSTALL_ZIP_PATTERN\b""")

    private fun code(file: File): String = file.readLines()
        .filterNot { it.trimStart().let { t -> t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") } }
        .joinToString("\n")

    private fun closeParen(text: String, open: Int): Int {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return i }
            }
        }
        return text.length - 1
    }

    /** Every offending file name, one entry per offence. */
    private fun offences(): List<String> {
        val found = mutableListOf<String>()
        File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            if (file.name == "ScreenLauncher.kt") return@forEach
            val text = code(file)
            for (call in listOf("awaitIntent(", ".launch(")) {
                var from = 0
                while (true) {
                    val at = text.indexOf(call, from)
                    if (at < 0) break
                    val open = at + call.length - 1
                    val close = closeParen(text, open)
                    from = close + 1
                    val args = text.substring(open + 1, close)
                    val direct = marker.containsMatchIn(args)
                    val viaVariable = call == "awaitIntent(" && Regex("""^\s*(\w+)\s*$""").find(args)?.groupValues?.get(1)
                        ?.let { name ->
                            val before = text.substring(maxOf(0, at - 600), at)
                            val assign = Regex("""\bval\s+$name\s*=([^\n]*(\n[^\n]*){0,3})""").findAll(before).lastOrNull()
                            assign != null && marker.containsMatchIn(assign.value)
                        } == true
                    if (direct || viaVariable) found += file.name
                }
            }
        }
        return found
    }

    @Test
    fun theScanSeesTheTree() {
        assertTrue(File("src/main/java").isDirectory)
    }

    @Test
    fun noInstallZipCallerWaitsOnAwaitIntent() {
        val live = offences().filterNot { it in EXTERNAL_ACTIVITY_ALLOWLIST.keys }
        assertEquals(
            "these files wait on InstallZip through awaitIntent or an ActivityResult launcher. On the nav host " +
                "that is answered by a synthetic RESULT_CANCELED before the install runs (M5). Use " +
                "NavHostComposeActivity.openInstallZip { result -> … } instead.",
            emptyList<String>(),
            live,
        )
    }

    @Test
    fun everyAllowlistedFileStillWaits() {
        val found = offences().toSet()
        val stale = EXTERNAL_ACTIVITY_ALLOWLIST.keys.filterNot { it in found }
        assertEquals("delete these allowlist entries -- the file no longer waits on InstallZip", emptyList<String>(), stale)
    }

    private companion object {
        val EXTERNAL_ACTIVITY_ALLOWLIST: Map<String, String> = mapOf(
            "StartupActivity.kt" to "showFirstLayout's onLoadFromZip (:393), a separate Activity; removed by Task E4",
            "StartupComposeActivity.kt" to "onImport (:191), a separate Activity; the file is deleted by Task F6",
            "ChooseDocumentComposeActivity.kt" to "onInstallZip (:424), a separate Activity; deleted by Task F6",
        )
    }
}
