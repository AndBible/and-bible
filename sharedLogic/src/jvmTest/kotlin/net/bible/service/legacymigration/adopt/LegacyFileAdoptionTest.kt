package net.bible.service.legacymigration.adopt

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.sqlite.SQLiteException
import net.bible.service.db.openSqlite
import okio.FileSystem
import okio.Path.Companion.toOkioPath

class LegacyFileAdoptionTest {
    private val roots = mutableListOf<File>()

    @AfterTest
    fun cleanUp() { roots.forEach { it.deleteRecursively() }; roots.clear() }

    private val documents: File by lazy { Files.createTempDirectory("adopt-").toFile().also { roots += it } }
    private val modules: File get() = File(documents, "sword")

    private fun adopt(): AdoptionResult =
        LegacyFileAdoption(FileSystem.SYSTEM, documents.toOkioPath(), modules.toOkioPath()).adopt()

    private fun file(root: File, relative: String, text: String): File =
        File(root, relative).also { it.parentFile.mkdirs(); it.writeText(text) }

    /** A package tree as the old app extracts it: container.xml, an OPF and a chapter. */
    private fun packageTree(root: File, marker: String) {
        file(root, "META-INF/container.xml", "container $marker")
        file(root, "OEBPS/content.opf", "opf $marker")
        file(root, "OEBPS/ch1.xhtml", "chapter $marker")
    }

    /** The old app's EPUB index: a SQLite file whose `metadata` table holds `source_file_name`. */
    private fun index(target: File, sourceFileName: String?) {
        target.parentFile.mkdirs()
        val db = openSqlite(target.path)
        try {
            db.prepare("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)").use { it.step() }
            if (sourceFileName != null) {
                db.prepare("INSERT INTO metadata (key, value) VALUES ('source_file_name', ?)").use { it.bindText(1, sourceFileName); it.step() }
            }
        } finally { db.close() }
    }

    /** One published generation: `.epub-generations/<id>/<gen>/{package,index.sqlite3}` plus the `<id>.epub-current.json` pointer. */
    private fun generation(id: String, gen: String, sourceFileName: String?, marker: String, publish: Boolean = true) {
        val dir = File(documents, "epub/.epub-generations/$id/$gen")
        packageTree(File(dir, "package"), marker)
        index(File(dir, "index.sqlite3"), sourceFileName)
        if (publish) {
            file(documents, "epub/$id.epub-current.json", """{"schemaVersion":1,"identifier":"$id","generationIdentifier":"$gen"}""")
        }
    }

    private fun hashTree(root: File): Map<String, String> =
        root.walkTopDown().filter { it.isFile }.associate { f ->
            f.relativeTo(root).path to MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        }

    // ---- row: Documents/epub generation -> sword/epub/<source file name> --------------------------------------------

    @Test
    fun epubFolderMovedIntoModulesDir() {
        generation("My_Book-abc", "g1", "My Book.epub", "v1")
        val before = hashTree(File(documents, "epub"))

        val result = adopt()

        assertEquals(listOf("epub/My Book.epub"), result.moved)
        assertEquals("opf v1", File(modules, "epub/My Book.epub/OEBPS/content.opf").readText())
        assertEquals("container v1", File(modules, "epub/My Book.epub/META-INF/container.xml").readText())
        assertFalse(File(modules, "epub/My Book.epub/index.sqlite3").exists(), "only the package tree is adopted, not the iOS index")
        assertEquals(before, hashTree(File(documents, "epub")), "the old library is copied, never modified")
        assertEquals(listOf("My Book.epub"), File(modules, "epub").list().orEmpty().sorted(), "no temp or hidden entry is left where the new app scans")
    }

    @Test
    fun onlyTheCurrentGenerationIsAdopted() {
        generation("Old-1", "g-old", "Old.epub", "stale", publish = false)
        generation("Old-1", "g-new", "Old.epub", "current")
        file(documents, "epub/Old-1.epub-current.json", """{"schemaVersion":1,"identifier":"Old-1","generationIdentifier":"g-new"}""")

        adopt()

        assertEquals("opf current", File(modules, "epub/Old.epub/OEBPS/content.opf").readText())
    }

    // ---- row: Documents/epub/<id> flat package from before generations -> sword/epub/<source file name> ---------------

    @Test
    fun legacyFlatEpubPackageIsAdopted() {
        packageTree(File(documents, "epub/Flat-123"), "flat")
        index(File(documents, "epub/Flat-123.index.sqlite3"), "Flat Book.epub")

        val result = adopt()

        assertEquals(listOf("epub/Flat Book.epub"), result.moved)
        assertEquals("opf flat", File(modules, "epub/Flat Book.epub/OEBPS/content.opf").readText())
    }

    @Test
    fun aFlatPackageWithAPublishedGenerationIsNotAdoptedTwice() {
        generation("Both-1", "g1", "Both.epub", "generation")
        packageTree(File(documents, "epub/Both-1"), "flat leftover")
        index(File(documents, "epub/Both-1.index.sqlite3"), "Both.epub")

        val result = adopt()

        assertEquals(listOf("epub/Both.epub"), result.moved)
        assertEquals("opf generation", File(modules, "epub/Both.epub/OEBPS/content.opf").readText())
    }

    // ---- guards -----------------------------------------------------------------------------------------------------

    @Test
    fun secondRunIsNoOp() {
        generation("A-1", "g1", "A.epub", "a")
        assertEquals(listOf("epub/A.epub"), adopt().moved)
        val after = hashTree(modules)

        val second = adopt()

        assertEquals(emptyList(), second.moved)
        assertEquals(listOf("epub/A.epub"), second.alreadyInPlace)
        assertEquals(emptyList(), second.conflicts)
        assertEquals(after, hashTree(modules))
    }

    @Test
    fun conflictKeepsTargetAndReports() {
        generation("C-1", "g1", "C.epub", "from old app")
        packageTree(File(modules, "epub/C.epub"), "already in the new app")
        val target = hashTree(modules)

        val result = adopt()

        assertEquals(emptyList(), result.moved)
        assertEquals(listOf("epub/C.epub"), result.conflicts)
        assertEquals(target, hashTree(modules), "an existing target is never overwritten")
    }

    @Test
    fun identicalExistingTargetIsAlreadyInPlaceNotAConflict() {
        generation("D-1", "g1", "D.epub", "same")
        packageTree(File(modules, "epub/D.epub"), "same")

        val result = adopt()

        assertEquals(listOf("epub/D.epub"), result.alreadyInPlace)
        assertEquals(emptyList(), result.conflicts)
    }

    @Test
    fun targetWithAnExtraFileIsAConflict() {
        generation("E-1", "g1", "E.epub", "same")
        packageTree(File(modules, "epub/E.epub"), "same")
        file(modules, "epub/E.epub/optimize.lock", "x")

        assertEquals(listOf("epub/E.epub"), adopt().conflicts)
    }

    @Test
    fun swordModulesUntouched() {
        file(documents, "sword/mods.d/kjv.conf", "[KJV]\nModDrv=zText\n")
        file(documents, "sword/modules/texts/ztext/kjv/ot.bzs", "binary-ish")
        generation("S-1", "g1", "S.epub", "s")
        val before = hashTree(File(modules, "mods.d")) to hashTree(File(modules, "modules"))

        adopt()

        assertEquals(before, hashTree(File(modules, "mods.d")) to hashTree(File(modules, "modules")))
    }

    @Test
    fun identityRowsAreLeftAlone() {
        // MyBible, MySword, e-Sword, the generated epub registrations and fonts already sit where the new app reads them.
        file(documents, "sword/mybible/Bible.sqlite3", "mb")
        file(documents, "sword/mysword/Study.mybible", "ms")
        file(documents, "sword/esword/Book.bblx", "es")
        file(documents, "sword/epub/Android Book/META-INF/container.xml", "registered")
        file(documents, "sword/ttf/Font.ttf", "ttf")
        file(documents, "sword/mods.d/mybible-bible.conf", "[MyBible-Bible]")
        val before = hashTree(modules)

        val result = adopt()

        assertEquals(before, hashTree(modules))
        assertEquals(AdoptionResult(emptyList(), emptyList(), emptyList()), result)
    }

    @Test
    fun noOldLibraryIsANoOp() {
        assertEquals(AdoptionResult(emptyList(), emptyList(), emptyList()), adopt())
        assertFalse(File(modules, "epub").exists(), "nothing is created when there is nothing to adopt")
    }

    @Test
    fun unresolvableOrUnsafeNamesAreSkippedNotGuessed() {
        generation("NoName-1", "g1", null, "x")
        generation("Evil-1", "g1", "../escape.epub", "x")
        generation("Slash-1", "g1", "a/b.epub", "x")
        generation("Dot-1", "g1", "..", "x")
        // A pointer to a generation that does not exist, and an index file that is not SQLite at all.
        file(documents, "epub/Missing-1.epub-current.json", """{"schemaVersion":1,"identifier":"Missing-1","generationIdentifier":"nope"}""")
        packageTree(File(documents, "epub/.epub-generations/Junk-1/g1/package"), "x")
        file(documents, "epub/.epub-generations/Junk-1/g1/index.sqlite3", "not a database")
        file(documents, "epub/Junk-1.epub-current.json", """{"schemaVersion":1,"identifier":"Junk-1","generationIdentifier":"g1"}""")

        val result = adopt()

        assertEquals(emptyList(), result.moved)
        assertEquals(listOf("Dot-1", "Evil-1", "Junk-1", "Missing-1", "NoName-1", "Slash-1"), result.skipped.sorted())
        assertFalse(File(documents, "escape.epub").exists() || File(modules, "escape.epub").exists() || File(modules, "../escape.epub").exists())
        assertFalse(File(modules, "epub").list().orEmpty().isNotEmpty())
    }

    @Test
    fun oneBrokenEntryDoesNotStopTheOthers() {
        generation("Bad-1", "g1", null, "x")
        generation("Good-1", "g1", "Good.epub", "ok")

        val result = adopt()

        assertEquals(listOf("epub/Good.epub"), result.moved)
        assertTrue(result.skipped.contains("Bad-1"))
    }

    // ---- error classification ---------------------------------------------------------------------------------------

    private fun adoptWith(open: (okio.Path) -> androidx.sqlite.SQLiteConnection) =
        LegacyFileAdoption(FileSystem.SYSTEM, documents.toOkioPath(), modules.toOkioPath(), open).adopt()

    @Test
    fun aTransientIndexErrorIsFailedNotSkippedSoTheAdoptionIsRetried() {
        generation("T-1", "g1", "T.epub", "t")
        for (code in listOf(14, 10, 5)) { // CANTOPEN, IOERR, BUSY
            val result = adoptWith { throw SQLiteException("Error code: $code, message: transient") }
            assertEquals(listOf("T-1"), result.failed)
            assertEquals(emptyList(), result.skipped)
            assertFalse(result.complete)
        }
        assertFalse(File(modules, "epub/T.epub").exists())
        // The next attempt, with the index readable again, adopts it.
        assertEquals(listOf("epub/T.epub"), adopt().moved)
    }

    @Test
    fun aCorruptIndexIsSkippedForGood() {
        generation("K-1", "g1", "K.epub", "k")
        for (code in listOf(11, 26)) { // CORRUPT, NOTADB
            val result = adoptWith { throw SQLiteException("Error code: $code, message: bad") }
            assertEquals(listOf("K-1"), result.skipped)
            assertTrue(result.complete)
        }
    }

    @Test
    fun anIndexThatCannotBeOpenedAsAFileIsFailed() {
        generation("D-9", "g1", "D.epub", "d")
        val index = File(documents, "epub/.epub-generations/D-9/g1/index.sqlite3")
        index.delete()
        index.mkdirs() // a directory where the database should be: CANTOPEN, not a verdict on the book
        val result = adopt()
        assertEquals(listOf("D-9"), result.failed)
        assertFalse(result.complete)
    }

    @Test
    fun aLeftoverTempFolderOfAKilledRunIsCleanedAndTheBookThenAdopted() {
        generation("L-1", "g1", "L.epub", "fresh")
        file(modules, ".legacy-adopt-tmp/L-1/OEBPS/content.opf", "half a copy")
        file(modules, ".legacy-adopt-tmp/L-1/stray.txt", "stray")

        val result = adopt()

        assertEquals(listOf("epub/L.epub"), result.moved)
        assertEquals("opf fresh", File(modules, "epub/L.epub/OEBPS/content.opf").readText())
        assertFalse(File(modules, "epub/L.epub/stray.txt").exists())
        assertFalse(File(modules, ".legacy-adopt-tmp").exists())
    }

    @Test
    fun aLargeFileThatDiffersLateIsAConflictAndEqualOnesAreNot() {
        generation("B-1", "g1", "B.epub", "b")
        val big = ByteArray(200_000) { (it % 251).toByte() }
        File(documents, "epub/.epub-generations/B-1/g1/package/big.bin").writeBytes(big)
        File(modules, "epub/B.epub").also { packageTree(it, "b") }
        File(modules, "epub/B.epub/big.bin").writeBytes(big)
        assertEquals(listOf("epub/B.epub"), adopt().alreadyInPlace)
        File(modules, "epub/B.epub/big.bin").writeBytes(big.also { it[199_999] = 0 })
        assertEquals(listOf("epub/B.epub"), adopt().conflicts)
    }
}
