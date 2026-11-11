package net.bible.service.installzip

import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.ByteString.Companion.encodeUtf8
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

    @Test fun interruptedReplacementBeforePublicationRestoresOriginalBytes() = fixture { root ->
        val live = root / "mybible/old.sqlite3"
        val transaction = SwordInstallTransaction(root)
        write(transaction.staging / "originals.json", "{\"mybible/old.sqlite3\":null}")
        write(transaction.staging / "originals/mybible/old.sqlite3", "original")
        SwordInstallTransaction.recoverInterrupted(root, root / "internal")
        assertEquals("original", files.read(live) { readUtf8() })
    }

    @Test fun retryAfterDecisionRestoredAndMoveFailsPreservesOriginal() = fixture { root ->
        val internal = root / "internal"
        val live = root / "mybible/old.sqlite3"
        val record = internal / "mybible-versifications" / "modules/mybible/old.sqlite3".encodeUtf8().sha256().hex()
        write(live, "original")
        val failing = object : ForwardingFileSystem(files) {
            override fun atomicMove(source: Path, target: Path) {
                if (target == live && source.toString().contains("/originals/")) throw IOException("injected move failure")
                super.atomicMove(source, target)
            }
        }
        val transaction = SwordInstallTransaction(root, internal, failing)
        transaction.beginPublish(listOf(live), mapOf(live to "KJVA"))
        write(live, "replacement")
        write(record, "German")
        assertFailsWith<IOException> { transaction.rollback() }
        assertEquals("KJVA", files.read(record) { readUtf8() })
        SwordInstallTransaction.recoverInterrupted(root, internal)
        assertEquals("original", files.read(live) { readUtf8() })
        assertEquals("KJVA", files.read(record) { readUtf8() })
        SwordInstallTransaction.recoverInterrupted(root, internal)
        assertEquals("original", files.read(live) { readUtf8() })
    }

    @Test fun interruptedMovingOriginalsKeepsUnmovedFiles() = fixture { root ->
        val first = root / "mybible/a.sqlite3"
        val second = root / "mybible/b.sqlite3"
        write(first, "first")
        write(second, "second")
        val failing = object : ForwardingFileSystem(files) {
            override fun atomicMove(source: Path, target: Path) {
                if (source == second) throw IOException("injected move failure")
                super.atomicMove(source, target)
            }
        }
        val transaction = SwordInstallTransaction(root, root / "internal", failing)
        assertFailsWith<IOException> { transaction.beginPublish(listOf(first, second), mapOf(first to null, second to null)) }
        SwordInstallTransaction.recoverInterrupted(root, root / "internal")
        assertEquals("first", files.read(first) { readUtf8() })
        assertEquals("second", files.read(second) { readUtf8() })
    }

    @Test fun retryAfterDeleteFailureRestoresOriginal() = fixture { root ->
        val live = root / "mybible/a.sqlite3"
        write(live, "original")
        val failing = object : ForwardingFileSystem(files) {
            override fun delete(path: Path, mustExist: Boolean) {
                if (path == live) throw IOException("injected delete failure")
                super.delete(path, mustExist)
            }
        }
        val transaction = SwordInstallTransaction(root, root / "internal", failing)
        transaction.beginPublish(listOf(live), mapOf(live to null))
        write(live, "replacement")
        assertFailsWith<IOException> { transaction.rollback() }
        assertEquals("replacement", files.read(live) { readUtf8() })
        SwordInstallTransaction.recoverInterrupted(root, root / "internal")
        assertEquals("original", files.read(live) { readUtf8() })
    }

    @Test fun interruptedRecoveryDoesNotDeleteAlreadyRestoredOriginal() = fixture { root ->
        val first = root / "mybible/a.sqlite3"
        val second = root / "mybible/b.sqlite3"
        write(first, "first")
        write(second, "second")
        val failing = object : ForwardingFileSystem(files) {
            override fun atomicMove(source: Path, target: Path) {
                if (target == second && source.toString().contains("/originals/")) throw IOException("injected move failure")
                super.atomicMove(source, target)
            }
        }
        val transaction = SwordInstallTransaction(root, root / "internal", failing)
        transaction.beginPublish(listOf(first, second), mapOf(first to null, second to null))
        write(first, "new first")
        write(second, "new second")
        assertFailsWith<IOException> { transaction.rollback() }
        assertEquals("first", files.read(first) { readUtf8() })
        SwordInstallTransaction.recoverInterrupted(root, root / "internal")
        assertEquals("first", files.read(first) { readUtf8() })
        assertEquals("second", files.read(second) { readUtf8() })
    }

    @Test fun committedCleanupInterruptionPreservesPublishedFiles() = fixture { root ->
        val live = root / "mybible/a.sqlite3"
        write(live, "original")
        val transaction = SwordInstallTransaction(root, root / "internal")
        transaction.beginPublish(listOf(live), mapOf(live to null))
        write(live, "replacement")
        val committed = root / ".sword-committed-test"
        files.atomicMove(transaction.staging, committed)
        SwordInstallTransaction.recoverInterrupted(root, root / "internal")
        assertEquals("replacement", files.read(live) { readUtf8() })
        assertFalse(files.exists(committed))
    }

    @Test fun invalidOriginalsManifestCannotDeleteJournalTargets() = fixture { root ->
        val live = root / "modules/keep/a"
        write(live, "keep")
        val transaction = SwordInstallTransaction(root)
        write(transaction.staging / "publishing", "[\"modules/keep/a\"]")
        write(transaction.staging / "originals.json", "{\"../outside\":null}")
        assertFailsWith<IOException> { SwordInstallTransaction.recoverInterrupted(root, root / "internal") }
        assertEquals("keep", files.read(live) { readUtf8() })
    }

    @Test fun missingInternalDirectoryLeavesOriginalsAvailableForRetry() = fixture { root ->
        val live = root / "mybible/a.sqlite3"
        val transaction = SwordInstallTransaction(root)
        write(transaction.staging / "originals.json", "{\"mybible/a.sqlite3\":null}")
        write(transaction.staging / "originals/mybible/a.sqlite3", "original")
        assertFailsWith<IOException> { SwordInstallTransaction.recoverInterrupted(root) }
        SwordInstallTransaction.recoverInterrupted(root, root / "internal")
        assertEquals("original", files.read(live) { readUtf8() })
    }

    @Test fun interruptedNewImportRemovesNewNumberingDecision() = fixture { root ->
        val internal = root / "internal"
        val live = root / "mybible/new.sqlite3"
        val record = internal / "mybible-versifications" / "modules/mybible/new.sqlite3".encodeUtf8().sha256().hex()
        val transaction = SwordInstallTransaction(root, internal)
        transaction.beginPublish(listOf(live), mapOf(live to null))
        write(live, "new bytes")
        write(record, "German")
        SwordInstallTransaction.recoverInterrupted(root, internal)
        assertFalse(files.exists(live))
        assertFalse(files.exists(record))
        SwordInstallTransaction.recoverInterrupted(root, internal)
        assertFalse(files.exists(record))
    }

    @Test fun cleanupFailureAfterCommitCannotRollbackPublishedBytes() = fixture { root ->
        val live = root / "mybible/a.sqlite3"
        write(live, "original")
        val failing = object : ForwardingFileSystem(files) {
            override fun delete(path: Path, mustExist: Boolean) {
                if (path.toString().contains(".sword-committed-")) throw IOException("injected cleanup failure")
                super.delete(path, mustExist)
            }
        }
        val transaction = SwordInstallTransaction(root, root / "internal", failing)
        transaction.beginPublish(listOf(live), mapOf(live to null))
        write(live, "replacement")
        transaction.complete()
        SwordInstallTransaction.recoverInterrupted(root, root / "internal")
        assertEquals("replacement", files.read(live) { readUtf8() })
        assertFalse(files.list(root).any { it.name.startsWith(".sword-committed-") })
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
