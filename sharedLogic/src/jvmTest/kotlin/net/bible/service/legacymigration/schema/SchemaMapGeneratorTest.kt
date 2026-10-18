package net.bible.service.legacymigration.schema

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import net.bible.service.legacymigration.testsupport.FixtureDir
import net.bible.service.legacymigration.testsupport.allFixtures
import net.bible.service.legacymigration.testsupport.columnNames
import net.bible.service.legacymigration.testsupport.fingerprint
import net.bible.service.legacymigration.testsupport.fixture
import net.bible.service.legacymigration.testsupport.manifestFingerprints
import net.bible.service.legacymigration.testsupport.openStore
import net.bible.service.legacymigration.testsupport.query
import net.bible.service.legacymigration.testsupport.storeFiles
import net.bible.service.legacymigration.testsupport.tableNames

class SchemaMapGeneratorTest {
    private val schemaDir = File("src/commonMain/kotlin/net/bible/service/legacymigration/schema")

    /** Set `REGENERATE=true` (or `-Dregenerate=true`) and run `:sharedLogic:cleanJvmTest :sharedLogic:jvmTest --tests '*SchemaMapGeneratorTest.regenerate*'`. */
    @Test
    fun regenerateCommittedSources() {
        if (System.getProperty("regenerate") != "true" && System.getenv("REGENERATE") != "true") return
        SchemaMapGenerator.writeTo(schemaDir, SchemaMapGenerator.generate(allFixtures()))
    }

    @Test
    fun regeneratedMapsEqualCommittedSources() {
        val regenerate = System.getProperty("regenerate") == "true" || System.getenv("REGENERATE") == "true"
        if (regenerate) return // the regenerate test rewrites the files; comparing now would race with it
        val generated = SchemaMapGenerator.generate(allFixtures())
        assertTrue(generated.size >= 5, "expected 4 schema maps plus KnownSchemaMaps, got ${generated.keys}")
        for ((relativePath, source) in generated) {
            val committed = File(schemaDir, relativePath)
            assertTrue(committed.isFile, "missing committed $committed; regenerate the schema maps")
            assertEquals(source, committed.readText(), "$relativePath is stale; regenerate the schema maps")
        }
        val committedGenerated = File(schemaDir, "generated").listFiles().orEmpty().map { "generated/${it.name}" }.toSet()
        assertEquals(generated.keys.filter { it.startsWith("generated/") }.toSet(), committedGenerated, "stray or missing generated files")
    }

    @Test
    fun fingerprintsPartitionFixturesLikeTheManifest() {
        val manifest = manifestFingerprints()
        val byKotlin = mutableMapOf<String, MutableSet<String>>()
        for (f in allFixtures()) {
            for (store in f.storeFiles()) {
                openStore(store).use { c ->
                    byKotlin.getOrPut(c.fingerprint()) { mutableSetOf() } += manifest.getValue("${f.app}/${f.ios}/${f.scenario}")
                }
            }
        }
        assertEquals(4, byKotlin.size)
        // Each Kotlin fingerprint corresponds to exactly one manifest (Swift oracle) fingerprint, and vice versa.
        assertTrue(byKotlin.values.all { it.size == 1 }, "$byKotlin")
        assertEquals(4, byKotlin.values.flatten().toSet().size)
        assertEquals(byKotlin.keys, KnownSchemaMaps.all.keys)
    }

    @Test
    fun everyColumnOfEveryTableIsMappedOrSystem() {
        val system = setOf("Z_PK", "Z_ENT", "Z_OPT")
        val metaTables = setOf("Z_PRIMARYKEY", "Z_METADATA", "Z_MODELCACHE")
        for (f in allFixtures()) for (store in f.storeFiles()) openStore(store).use { c ->
            val label = "${f.app}/${f.ios}/${f.scenario}/${store.name}"
            val map = assertNotNull(KnownSchemaMaps.forFingerprint(c.fingerprint()), "$label: unknown fingerprint")
            val entityTables = c.tableNames().filter { it.startsWith("Z") && it !in metaTables }
            assertEquals(map.entities.values.map { it.table }.toSet(), entityTables.toSet(), "$label: tables")
            for (entity in map.entities.values) {
                val mapped = entity.columns.values.map { it.column } + entity.toOne.values
                assertEquals(mapped.size, mapped.toSet().size, "$label ${entity.name}: a column is mapped twice")
                val actual = c.columnNames(entity.table)
                assertEquals(emptyList(), actual - system - mapped.toSet(), "$label ${entity.name}: unmapped columns")
                assertEquals(emptyList(), mapped - actual.toSet(), "$label ${entity.name}: mapped columns missing from the table")
                val ent = c.query("SELECT Z_ENT FROM Z_PRIMARYKEY WHERE Z_NAME = ?", entity.name) { it.getLong(0).toInt() }.single()
                assertEquals(ent, entity.entityNumber, "$label ${entity.name}: Z_ENT")
            }
        }
    }

    @Test
    fun suffixedLeavesMapToDistinctProperties() {
        for (app in listOf("1.0.1", "1.0.2")) for (ios in listOf("17", "18", "26")) {
            val f = fixture(app, ios, "rich")
            val store = f.storeFiles().single { it.name == "AndBible.store" }
            openStore(store).use { c ->
                val map = KnownSchemaMaps.forFingerprint(c.fingerprint())!!
                val ws = map.entities.getValue("Workspace")
                fun col(path: String) = ws.columns.getValue(path).column
                // Workspace "Study A" is seed 1, the Hebrew-named one seed 2; both are non-default on every leaf.
                for ((name, seed) in listOf("Study A ✨" to 1, "מרחב עבודה B" to 2)) {
                    val expected = textDisplayExpected(seed, "textDisplaySettings.") + speakExpected(seed, "workspaceSettings.speakSettings.") +
                        workspaceSettingsExpected(seed, "workspaceSettings.")
                    val columns = expected.keys.map { col(it) }
                    val row = c.query("SELECT ${columns.joinToString()} FROM ${ws.table} WHERE ${col("name")} = ?", name) { st ->
                        columns.indices.map { st.getLong(it) to st.getText(it) }
                    }.single()
                    expected.entries.forEachIndexed { i, (path, want) ->
                        when (want) {
                            is Long -> assertEquals(want, row[i].first, "workspace $name $path (${columns[i]}) in $app/$ios")
                            is String -> assertEquals(want, row[i].second, "workspace $name $path in $app/$ios")
                        }
                    }
                }
                // PageManagers carry seed 10 + window seed (3, 4, 5), keyed by their distinct bible chapter 1 + windowSeed.
                val pm = map.entities.getValue("PageManager")
                for (windowSeed in listOf(3, 4, 5)) {
                    val expected = textDisplayExpected(10 + windowSeed, "textDisplaySettings.")
                    val columns = expected.keys.map { pm.columns.getValue(it).column }
                    val chapter = pm.columns.getValue("bibleChapterNo").column
                    val row = c.query("SELECT ${columns.joinToString()} FROM ${pm.table} WHERE $chapter = ?", 1 + windowSeed) { st ->
                        columns.indices.map { st.getLong(it) to st.getText(it) }
                    }.single()
                    expected.entries.forEachIndexed { i, (path, want) ->
                        when (want) {
                            is Long -> assertEquals(want, row[i].first, "pageManager seed $windowSeed $path in $app/$ios")
                            is String -> assertEquals(want, row[i].second, "pageManager seed $windowSeed $path in $app/$ios")
                        }
                    }
                }
            }
        }
    }

    /**
     * The value check of [suffixedLeavesMapToDistinctProperties] cannot see a swap of the `decodedMalformedKnownField`
     * leaves (excluded from CodingKeys, always NULL; numbering is proven by column position). So assert structurally:
     * for every group of equal leaf names (`ZX`, `ZX1`, ...), the nearest preceding mapped column of the table in
     * `pragma_table_info` order maps to a property with the same parent path as the group member.
     */
    @Test
    fun suffixedGroupsFollowTheirParentComposite() {
        var groupsChecked = 0
        val seen = mutableSetOf<String>()
        for (f in allFixtures()) for (store in f.storeFiles()) openStore(store).use { c ->
            val map = KnownSchemaMaps.forFingerprint(c.fingerprint())!!
            if (!seen.add(map.fingerprint)) return@use
            for (entity in map.entities.values) {
                val pathByColumn = entity.columns.entries.associate { (path, col) -> col.column to path }
                val tableColumns = c.columnNames(entity.table)
                fun parent(path: String) = path.substringBeforeLast('.', "")
                val groups = pathByColumn.keys.groupBy { it.trimEnd { ch -> ch.isDigit() } }.filterValues { it.size > 1 }
                for (member in groups.values.flatten()) {
                    val index = tableColumns.indexOf(member)
                    val preceding = tableColumns.subList(0, index).lastOrNull { it in pathByColumn }
                    assertNotNull(preceding, "${map.fingerprint.take(12)} ${entity.name}.$member has no preceding mapped column")
                    assertEquals(
                        parent(pathByColumn.getValue(member)), parent(pathByColumn.getValue(preceding)),
                        "${map.fingerprint.take(12)} ${entity.name}: $member is mapped to ${pathByColumn.getValue(member)} but follows $preceding",
                    )
                    groupsChecked++
                }
            }
        }
        assertTrue(groupsChecked >= 8, "expected the Workspace decodedMalformedKnownField groups in every fingerprint, checked $groupsChecked")
    }

    private val PAGE_SCROLL_AMOUNT_VALUES = listOf(25, 33, 50, 66, 75)

    private fun flag(seed: Int, k: Int): Long = if ((seed + k) % 2 == 0) 1L else 0L

    /** Mirrors `I3Seed.sentinelTextDisplay` (iosApp/legacy-fixtures/I3FixtureGeneratorTests.swift); `PAGE_SCROLL_AMOUNT_VALUES` is `TextDisplaySettings.pageScrollAmountValues` as stored in the fixtures. */
    private fun textDisplayExpected(seed: Int, p: String): Map<String, Any> = linkedMapOf<String, Any>(
        "fontSize" to (20L + seed), "fontFamily" to "family-$seed", "lineSpacing" to (30L + seed),
        "marginLeft" to (40L + seed), "marginRight" to (50L + seed), "maxWidth" to (200L + seed), "topMargin" to (60L + seed),
        "strongsMode" to (seed % 3 + 1L),
        "showMorphology" to flag(seed, 1), "showFootNotes" to flag(seed, 2), "showFootNotesInline" to flag(seed, 3),
        "expandXrefs" to flag(seed, 4), "showXrefs" to flag(seed, 5), "showRedLetters" to flag(seed, 6),
        "showSectionTitles" to flag(seed, 7), "showVerseNumbers" to flag(seed, 8), "showVersePerLine" to flag(seed, 9),
        "showBookmarks" to flag(seed, 10), "showMyNotes" to flag(seed, 11), "justifyText" to flag(seed, 12),
        "hyphenation" to flag(seed, 13), "showPageNumber" to flag(seed, 14), "infiniteScroll" to flag(seed, 15),
        "nonStrongsWordItalic" to flag(seed, 16), "showMarkAsReadButton" to flag(seed, 17),
        "showTitleScrollButton" to flag(seed, 18), "showMemorizationIndicators" to flag(seed, 19),
        "showAiDocMarkers" to flag(seed, 20), "showOrdinals" to flag(seed, 21),
        "pageScrollAmount" to PAGE_SCROLL_AMOUNT_VALUES[seed % 5].toLong(),
        "dayTextColor" to (-1_000_000L - seed), "dayBackground" to (-2_000_000L - seed), "dayNoise" to (70L + seed),
        "nightTextColor" to (-3_000_000L - seed), "nightBackground" to (-4_000_000L - seed), "nightNoise" to (80L + seed),
        "enableVerseSelection" to flag(seed, 22),
    ).mapKeys { p + it.key }

    /** Mirrors `I3Seed.sentinelSpeak`. The two `decodedMalformedKnownField` leaves are excluded from the Swift CodingKeys and always NULL, so no value can be compared; numbering is proven by column position in [suffixedGroupsFollowTheirParentComposite]. */
    private fun speakExpected(seed: Int, p: String): Map<String, Any> {
        val even = if (seed % 2 == 0) 1L else 0L
        val odd = 1L - even
        return linkedMapOf<String, Any>(
            "playbackSettings.speakChapterChanges" to even, "playbackSettings.speakTitles" to odd,
            "playbackSettings.speakFootnotes" to even, "playbackSettings.speed" to (110L + seed),
            "playbackSettings.bookId" to "SPK$seed", "playbackSettings.bookmarkWasCreated" to even,
            "sleepTimer" to (20L + seed), "lastSleepTimer" to (30L + seed), "queue" to even,
            "repeatPlayback" to odd, "numPagesToSpeakId" to (5L + seed),
        ).mapKeys { p + it.key }
    }

    /** Mirrors `I3Seed.sentinelWorkspaceSettings` (the scalar leaves). */
    private fun workspaceSettingsExpected(seed: Int, p: String): Map<String, Any> {
        val even = if (seed % 2 == 0) 1L else 0L
        val odd = 1L - even
        return linkedMapOf<String, Any>(
            "enableTiltToScroll" to odd, "enableReverseSplitMode" to even, "autoPin" to even,
            "restoreButtonsVisible" to odd, "limitAmbiguousModalSize" to even,
        ).mapKeys { p + it.key }
    }
}
