package net.bible.android.control.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fix batch 5 §1.1: every `DatabaseContainer.reset()` in BackupControl sits inside `replacingDatabases { }`. */
class BackupControlReplaceGuardTest {
    private val file = File("src/main/java/net/bible/android/control/backup/BackupControl.kt")

    /** Indices of `DatabaseContainer.reset()` not enclosed by a `replacingDatabases {` brace block. */
    internal fun unguardedResets(src: String): List<Int> {
        val blocks = Regex("""replacingDatabases\s*\{""").findAll(src).map { m ->
            var depth = 0; var i = m.range.last
            while (i < src.length) {
                when (src[i]) { '{' -> depth++; '}' -> { depth--; if (depth == 0) break } }
                i++
            }
            m.range.last..i
        }.toList()
        return Regex("""DatabaseContainer\.reset\(\)""").findAll(src).map { it.range.first }
            .filter { idx -> blocks.none { idx in it } }.toList()
    }

    @Test fun everyResetIsInsideAReplace() = assertEquals(emptyList<Int>(), unguardedResets(file.readText()))
    @Test fun theScanSeesTheSource() = assertTrue("run from app/", file.isFile && "DatabaseContainer.reset()" in file.readText())
    @Test fun theGuardCanFail() = assertEquals(1, unguardedResets("fun x() { DatabaseContainer.reset() }").size)
}
