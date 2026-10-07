package net.bible.android.control.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fix batch 5 §1.1 / batch 6 §1.2: every `DatabaseContainer.reset()` in BackupControl, and every
 * `.resetLocalDb()` in CloudSync, sits inside `replacingDatabases { }`.
 */
class BackupControlReplaceGuardTest {
    private val file = File("src/main/java/net/bible/android/control/backup/BackupControl.kt")
    private val cloudSync = File("src/main/java/net/bible/service/cloudsync/CloudSync.kt")

    /** Index ranges of the brace blocks that open at [open]. */
    internal fun blocks(src: String, open: Regex): List<IntRange> = open.findAll(src).map { m ->
        var depth = 0; var i = m.range.last
        while (i < src.length) {
            when (src[i]) { '{' -> depth++; '}' -> { depth--; if (depth == 0) break } }
            i++
        }
        m.range.last..i
    }.toList()

    /** Indices of [target] not enclosed by a `replacingDatabases {` brace block. */
    internal fun unguarded(src: String, target: Regex): List<Int> {
        val blocks = blocks(src, Regex("""replacingDatabases\s*\{"""))
        return target.findAll(src).map { it.range.first }.filter { idx -> blocks.none { idx in it } }.toList()
    }

    internal fun unguardedResets(src: String): List<Int> = unguarded(src, Regex("""DatabaseContainer\.reset\(\)"""))

    @Test fun everyResetIsInsideAReplace() = assertEquals(emptyList<Int>(), unguardedResets(file.readText()))
    @Test fun theScanSeesTheSource() = assertTrue("run from app/", file.isFile && "DatabaseContainer.reset()" in file.readText())
    @Test fun theGuardCanFail() = assertEquals(1, unguardedResets("fun x() { DatabaseContainer.reset() }").size)

    @Test fun everyLocalDbResetInCloudSyncIsInsideAReplace() =
        assertEquals(emptyList<Int>(), unguarded(cloudSync.readText(), Regex("""\.resetLocalDb\(\)""")))
    @Test fun theCloudSyncScanSeesTheSource() = assertTrue(cloudSync.isFile && "resetLocalDb()" in cloudSync.readText())
    @Test fun theLocalDbGuardCanFail() =
        assertEquals(1, unguarded("fun x() { dbDef.resetLocalDb() }", Regex("""\.resetLocalDb\(\)""")).size)

    // F115 (fix batch 6 §1.3): every replace in BackupControl is followed by exactly one reload.
    @Test fun everyReplaceIsInsideAReloadingAfterReplace() {
        val src = file.readText()
        val reloadBlocks = blocks(src, Regex("""reloadingAfterReplace\s*\{"""))
        val replaces = Regex("""replacingDatabases\s*\{""").findAll(src).map { it.range.first }.toList()
        assertTrue("scan must see replaces", replaces.isNotEmpty())
        assertEquals(emptyList<Int>(), replaces.filter { idx -> reloadBlocks.none { idx in it } })
    }
    @Test fun theReloadIsPostedExactlyOnceInTheFile() =
        assertEquals(1, Regex("""notifyDatabaseRestored\(\)""").findAll(file.readText()).count())
    @Test fun theReloadGuardCanFail() {
        val src = "fun x() { DatabaseContainer.replacingDatabases { } }"
        assertEquals(1, Regex("""replacingDatabases\s*\{""").findAll(src).count { m -> blocks(src, Regex("""reloadingAfterReplace\s*\{""")).none { m.range.first in it } })
    }
}
