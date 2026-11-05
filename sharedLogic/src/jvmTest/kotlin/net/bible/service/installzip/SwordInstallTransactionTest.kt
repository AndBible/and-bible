package net.bible.service.installzip

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwordInstallTransactionTest {
    private val files = FileSystem.SYSTEM

    private fun fixture(test: (Path) -> Unit) {
        val root = Files.createTempDirectory("sword-transaction").toFile()
        try { test(root.absolutePath.toPath()) } finally { root.deleteRecursively() }
    }

    private fun write(path: Path, text: String = "data") {
        files.createDirectories(path.parent!!)
        files.write(path) { writeUtf8(text) }
    }

    @Test fun recoveryRemovesAllAffectedGenerationsButPreservesUnrelatedBooks() = fixture { root ->
        val targets = listOf("mods.d/a.conf", "mods.d/b.conf", "modules/old/a", "modules/new/a", "modules/b/a").map { root / it }
        targets.forEach { write(it) }
        write(root / "mods.d/keep.conf", "keep")
        write(root / "modules/keep/a", "keep")
        val transaction = SwordInstallTransaction(root)
        transaction.beginPublish(targets)
        // A previous recovery was itself killed after removing a subset of files.
        files.delete(targets.first())
        SwordInstallTransaction.recoverInterrupted(root)
        SwordInstallTransaction.recoverInterrupted(root)
        targets.forEach { assertFalse(files.exists(it), "$it left behind") }
        assertEquals("keep", files.read(root / "mods.d/keep.conf") { readUtf8() })
        assertEquals("keep", files.read(root / "modules/keep/a") { readUtf8() })
        assertFalse(files.exists(transaction.staging))
    }

    @Test fun incompleteJournalCannotClaimLiveFiles() = fixture { root ->
        val live = root / "modules/old/a"
        write(live)
        val transaction = SwordInstallTransaction(root)
        write(transaction.stage(live))
        write(transaction.staging / "publishing.tmp", "modules/old/a\n")
        SwordInstallTransaction.recoverInterrupted(root)
        assertTrue(files.exists(live))
        assertFalse(files.exists(transaction.staging))
    }

    @Test fun successfulPublicationSurvivesNextStartup() = fixture { root ->
        val live = root / "modules/new/a"
        val transaction = SwordInstallTransaction(root)
        write(transaction.stage(live))
        transaction.beginPublish(listOf(live))
        write(live)
        transaction.complete()
        SwordInstallTransaction.recoverInterrupted(root)
        assertTrue(files.exists(live))
    }

    @Test fun journalTreatsNewlinesAsPartOfTheOwnedFilename() = fixture { root ->
        val unrelated = root / "modules/keep/data"
        write(unrelated, "keep")
        val owned = root / "modules/a\nmodules/keep/data"
        write(owned)
        val transaction = SwordInstallTransaction(root)
        transaction.beginPublish(listOf(owned))
        SwordInstallTransaction.recoverInterrupted(root)
        assertEquals("keep", files.read(unrelated) { readUtf8() })
        assertFalse(files.exists(owned))
    }

    @Test fun stagingAcceptsWindowsPathsWithoutTheHostFileSystem() = fixture { root ->
        // Okio's parser understands drive/backslash paths even when this test runs on Linux.
        val transaction = SwordInstallTransaction(root)
        SwordInstallTransaction::class.java.getDeclaredField("root").apply { isAccessible = true }
            .set(transaction, "C:\\store".toPath())
        val relative = SwordInstallTransaction::class.java.getDeclaredMethod("relative", Path::class.java).apply { isAccessible = true }
        assertEquals("modules/foo/data", relative.invoke(transaction, "C:\\store\\modules\\foo\\data".toPath()))
    }

    @Test fun recoveryRejectsTraversalWithoutDeletingAnyLiveFile() = fixture { root ->
        val live = root / "modules/keep/a"
        write(live)
        val transaction = SwordInstallTransaction(root)
        write(transaction.staging / "publishing", "[\"modules/keep/a\",\"../outside\"]")
        assertFailsWith<okio.IOException> { SwordInstallTransaction.recoverInterrupted(root) }
        assertTrue(files.exists(live))
        assertTrue(files.exists(transaction.staging / "publishing"))
    }
}
