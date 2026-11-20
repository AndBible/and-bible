package net.bible.service.addons.collections

import kotlinx.coroutines.test.runTest
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME
import net.bible.service.llm.PromptCsvUtils
import net.bible.sharedlogic.addons.SharedAddonScanner
import net.bible.sharedlogic.addons.fakeAddonBook
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sample add-on modules under `docs/addon-samples/` (author guide: `docs/addon-modules.md`) are loaded through the
 * same code a user's device runs: the add-on scanner, then the collection importer or the prompt CSV parser.
 */
class SampleModulesTest {
    private val samples: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "docs/addon-samples") }.first { it.isDirectory }

    /** The conf as a SWORD reader sees it: first-wins single values and every value of repeated keys. */
    private class Conf(val initials: String, val values: Map<String, List<String>>) {
        fun one(key: String) = values[key]?.firstOrNull()
        /** The module folder: `DataPath` relative to the module's zip root (the sample directory). */
        fun dataDir(root: File) = File(root, one("DataPath")!!.removePrefix("./"))
    }

    private fun readConf(sample: String): Pair<Conf, File> {
        val root = File(samples, sample)
        val conf = root.resolve("mods.d").listFiles { f -> f.extension == "conf" }!!.single()
        var initials = ""
        val values = linkedMapOf<String, MutableList<String>>()
        conf.readLines().filter { it.isNotBlank() }.forEach { line ->
            if (line.startsWith("[")) initials = line.trim('[', ']', ' ')
            else values.getOrPut(line.substringBefore('=').trim()) { mutableListOf() }.add(line.substringAfter('=').trim())
        }
        return Conf(initials, values) to root
    }

    @Test
    fun theCollectionSampleIsDiscoveredAndImportsItsLabelsVersesNotesAndStudyPad() = runTest {
        val (conf, root) = readConf("collection-sample")
        assertEquals("And Bible", conf.one("Category"))
        val book = fakeAddonBook(conf.initials, conf.dataDir(root).path, labels = conf.values.getValue("AndBibleProvidesLabels"), version = conf.one("Version"))
        val ref = SharedAddonScanner({ listOf(book) }).collections().single()
        assertEquals(conf.initials, ref.moduleInitials)
        assertEquals("1.0", ref.version)
        assertTrue(FileSystem.SYSTEM.exists(ref.file), "the file named by AndBibleProvidesLabels exists")

        val dir = newDir()
        val local = newLocalDb(dir)
        try {
            // A device that has used a StudyPad has its own paragraph-break label; the sample's link maps to it.
            local.sql("INSERT INTO Label (id, name) VALUES (${hex(PARAGRAPH_BREAK_LABEL_ID)}, '$PARAGRAH_BREAK_LABEL_NAME')", "DELETE FROM LogEntry")
            AddonCollectionImporter({ local }, testEnv(dir), openCopy = { openBookmarkDb(it) }).replace(ref, sha256(ref.file))

            assertEquals(listOf("Comfort", "John study"), local.rows("SELECT name FROM Label WHERE name NOT LIKE '\\_\\_%' ESCAPE '\\' ORDER BY name").map { it[0] })
            assertEquals(10, local.count("SELECT COUNT(*) FROM BibleBookmark"))
            assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmarkNotes"))
            assertEquals(1, local.count("SELECT COUNT(*) FROM StudyPadTextEntry"))
            // 6 verses in Comfort, 4 in John study, and one paragraph break link inside the StudyPad.
            assertEquals(6, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel l JOIN Label x ON x.id = l.labelId WHERE x.name = 'Comfort'"))
            assertEquals(4, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel l JOIN Label x ON x.id = l.labelId WHERE x.name = 'John study'"))
            assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmarkToLabel WHERE labelId = ${hex(PARAGRAPH_BREAK_LABEL_ID)}"))
            // The text view finds bookmarks by KJVA ordinal (the Apocrypha shift the New Testament): John 3:16 and Ps 23:1.
            assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmark WHERE kjvOrdinalStart = 33095 AND kjvOrdinalEnd = 33095 AND ordinalStart = 27182"))
            assertEquals(1, local.count("SELECT COUNT(*) FROM BibleBookmark WHERE kjvOrdinalStart = 14758 AND ordinalStart = 14758"))
            assertEquals(0, local.count("SELECT COUNT(*) FROM LogEntry"))
        } finally {
            local.close()
        }
    }

    @Test
    fun thePromptSampleIsDiscoveredAndParsesItsTwoPrompts() {
        val (conf, root) = readConf("prompt-sample")
        assertEquals("And Bible", conf.one("Category"))
        val book = fakeAddonBook(conf.initials, conf.dataDir(root).path, prompts = conf.one("AndBibleProvidesPrompts"), version = conf.one("Version"))
        val pack = SharedAddonScanner({ listOf(book) }).promptPacks().single()
        assertEquals(conf.initials, pack.moduleName)

        val prompts = FileSystem.SYSTEM.source(pack.path.toPath()).let { PromptCsvUtils.parsePromptsFromCsv(it) }
        assertEquals(listOf("Cross-reference ideas", "Plain-language summary"), prompts.map { it.name })
        assertEquals(listOf("5a8c1c70-1111-4d2e-9a10-0000000000a1", "5a8c1c70-1111-4d2e-9a10-0000000000a2"), prompts.map { it.id.toString() })
        assertTrue(prompts.all { it.promptTemplate.isNotBlank() })
        assertEquals(setOf(net.bible.service.llm.PromptContext.VERSE_SELECTION), prompts[0].showIn)
    }
}
