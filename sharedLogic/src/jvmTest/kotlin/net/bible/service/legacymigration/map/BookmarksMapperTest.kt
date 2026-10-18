package net.bible.service.legacymigration.map

import androidx.room3.useReaderConnection
import androidx.sqlite.execSQL
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.bible.android.common.isConvertibleTo
import net.bible.android.common.toV11n
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.system.Versifications
import net.bible.android.database.bookmarks.AI_LABEL_ID
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_ID
import net.bible.android.database.bookmarks.UNLABELED_LABEL_ID
import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyStoreReader
import net.bible.service.legacymigration.read.LegacyValue
import net.bible.service.legacymigration.stage.StagingDatabases
import net.bible.service.legacymigration.testsupport.ApprovedDifference
import net.bible.service.legacymigration.testsupport.FixtureDir
import net.bible.service.legacymigration.testsupport.JvmLegacyDatabaseFactory
import net.bible.service.legacymigration.testsupport.allFixtures
import net.bible.service.legacymigration.testsupport.compareWithOracleDetailed
import net.bible.service.legacymigration.testsupport.fixture
import net.bible.service.legacymigration.testsupport.openStore
import okio.Path.Companion.toOkioPath

class BookmarksMapperTest {
    private fun tempDir(): File = Files.createTempDirectory("staging-").toFile()

    private fun snapshot(fx: FixtureDir) = LegacySnapshot(
        LegacyStoreReader(File(fx.root.toFile(), "AndBible.store").path),
        LegacyStoreReader(File(fx.root.toFile(), "LocalStore.store").path),
    )

    private inline fun <T> withStaging(block: (StagingDatabases) -> T): T {
        val dir = tempDir()
        val staging = StagingDatabases(dir.toOkioPath(), JvmLegacyDatabaseFactory())
        try {
            return block(staging)
        } finally {
            staging.close()
            dir.deleteRecursively()
        }
    }

    /** Approved, documented deviations from the Swift export; none are needed when this is empty. */
    private val approved = emptySet<ApprovedDifference>()

    private fun mapAndCompare(fx: FixtureDir) = runBlocking {
        withStaging { staging ->
            val report = MigrationReport()
            snapshot(fx).use { BookmarksMapper().map(it, staging, report) }
            compareWithOracleDetailed(fx, LegacyCategory.BOOKMARKS, staging, approved) to report
        }
    }

    private fun label(fx: FixtureDir) = "${fx.app}/${fx.ios}/${fx.scenario}"

    @Test
    fun everyFixtureMatchesItsOracle() {
        val fixtures = allFixtures()
        assertEquals(24, fixtures.size)
        for (fx in fixtures) {
            val (result, report) = mapAndCompare(fx)
            assertEquals(emptyList(), result.differences, label(fx))
            // The fixtures hold no row the mapper may not carry over: nothing is skipped.
            assertEquals(emptyList(), report.entries.filterIsInstance<ReportEntry.Skipped>(), label(fx))
        }
    }

    @Test
    fun comparisonIsNotVacuous() {
        for (fx in allFixtures().filter { it.scenario == "rich" || it.scenario == "restored-android" }) {
            val counts = mapAndCompare(fx).first.comparedRows
            for (table in listOf("Label", "BibleBookmark", "BibleBookmarkNotes", "BibleBookmarkToLabel", "GenericBookmark", "StudyPadTextEntry", "StudyPadTextEntryText")) {
                assertTrue((counts[table] ?: 0) > 0, "${label(fx)}: no $table rows compared (counts $counts)")
            }
        }
    }

    @Test
    fun restoredAndroidFixturesCarryTheSystemLabelsWithTheirFixedIds() {
        val fixed = setOf(SPEAK_LABEL_ID, UNLABELED_LABEL_ID, PARAGRAPH_BREAK_LABEL_ID, AI_LABEL_ID).map { it.toString() }.toSet()
        for (fx in allFixtures().filter { it.scenario == "restored-android" }) {
            runBlocking {
                withStaging { staging ->
                    snapshot(fx).use { BookmarksMapper().map(it, staging, MigrationReport()) }
                    val ids = staging.bookmarks.useReaderConnection { c ->
                        c.usePrepared("SELECT lower(hex(id)) FROM Label") { st -> buildSet { while (st.step()) add(st.getText(0)) } }
                    }
                    val stagedFixed = ids.filter { id -> fixed.any { it.replace("-", "") == id } }
                    assertTrue(stagedFixed.isNotEmpty(), "${label(fx)}: no system label with a fixed id staged ($ids)")
                }
            }
        }
    }

    @Test
    fun plantedDefectIsReported() {
        // Same effect as a schema map that points a bookmark column at the wrong Core Data column: the oracle must notice.
        val fx = fixture("1.0.2", "18", "rich")
        val differences = runBlocking {
            withStaging { staging ->
                snapshot(fx).use { snap ->
                    val data = LegacyBookmarkData.from(snap)
                    val defect = LegacyBookmarkData(
                        data.labels, data.bibleBookmarks.map { it.copy(values = it.values + ("ordinalEnd" to it.values["ordinalStart"])) },
                        data.bibleNotes, data.bibleLinks, data.genericBookmarks, data.genericNotes, data.genericLinks,
                        data.studyPadEntries, data.studyPadTexts, data.settings,
                    )
                    BookmarksMapper().map(defect, staging, MigrationReport())
                }
                compareWithOracleDetailed(fx, LegacyCategory.BOOKMARKS, staging, approved).differences
            }
        }
        assertTrue(differences.isNotEmpty(), "planted defect went unnoticed")
        assertTrue(differences.all { it.contains("BibleBookmark[") && it.contains("ordinalEnd") }, differences.toString())
    }

    @Test
    fun richFixturesUseThePreservedAndroidPlaybackPayload() {
        // Only bookmark ...201 has a preserved payload (its native playback columns are NULL); the others have none.
        for (fx in allFixtures().filter { it.scenario == "rich" || it.scenario == "preserved-ai" }) {
            runBlocking {
                withStaging { staging ->
                    snapshot(fx).use { BookmarksMapper().map(it, staging, MigrationReport()) }
                    val payloads = staging.bookmarks.useReaderConnection { c ->
                        c.usePrepared("SELECT lower(hex(id)), playbackSettings, book FROM BibleBookmark") { st ->
                            buildMap { while (st.step()) put(st.getText(0), st.getText(1).takeIf { !st.isNull(1) } to st.getText(2)) }
                        }
                    }
                    assertEquals(
                        mapOf("13000000000000000000000000000201" to """{"bookId":"KJV","speed":122}""", "13000000000000000000000000000202" to null, "13000000000000000000000000000203" to null),
                        payloads.mapValues { it.value.first },
                        label(fx),
                    )
                    assertEquals(setOf("KJV"), payloads.values.map { it.second }.toSet(), label(fx))
                }
            }
        }
    }

    // ---- Unverified bookmarks ------------------------------------------------------------------------------

    private fun sha256Of(dir: File): Map<String, String> = dir.walkTopDown().filter { it.isFile }.associate {
        it.relativeTo(dir).path to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) }
    }

    /** Runs [sql] against the fixture's cloud store (a private temp copy). */
    private fun FixtureDir.updateCloudStore(sql: String) {
        val db = openStore(File(root.toFile(), "AndBible.store"))
        try { db.execSQL(sql) } finally { db.close() }
    }

    private fun bibleBookmarkIds(staging: StagingDatabases): List<String> = runBlocking {
        staging.bookmarks.useReaderConnection { c ->
            c.usePrepared("SELECT hex(id) FROM BibleBookmark ORDER BY id") { st -> buildList { while (st.step()) add(st.getText(0)) } }
        }
    }

    private fun count(staging: StagingDatabases, table: String): Int = runBlocking {
        staging.bookmarks.useReaderConnection { c -> c.usePrepared("SELECT COUNT(*) FROM $table") { st -> st.step(); st.getLong(0).toInt() } }
    }

    @Test
    fun unverifiedBookmarksAreReportedNotWritten() {
        val states = mapOf(
            "legacyPendingModule" to "ordinal trust legacyPendingModule",
            "legacyUnresolved" to "ordinal trust legacyUnresolved",
            // An unknown future state decodes fail-closed as unresolved, like Swift.
            "someFutureState" to "ordinal trust legacyUnresolved (unknown state \"someFutureState\")",
        )
        for ((state, reason) in states) {
            val fx = fixture("1.0.2", "18", "rich")
            val total = LegacySnapshot(
                LegacyStoreReader(File(fx.root.toFile(), "AndBible.store").path),
                LegacyStoreReader(File(fx.root.toFile(), "LocalStore.store").path),
            ).use { it.cloud.count("BibleBookmark") }
            // The bookmark that owns a note and a label link, so that both must stay out as well.
            val victim = snapshot(fx).use { s ->
                val withNote = s.cloud.rows("BibleBookmarkNotes").mapNotNull { (it.values["bookmarkId"] as? LegacyValue.UuidV)?.value }.toSet()
                val withLink = s.cloud.rows("BibleBookmarkToLabel").mapNotNull { it.toOne["bookmark"] }.toSet()
                s.cloud.rows("BibleBookmark").first { (it.values["id"] as LegacyValue.UuidV).value in withNote && it.pk in withLink }
            }
            val victimId = (victim.values["id"] as LegacyValue.UuidV).value
            // The victim must really own a label link, or the zero-count assertion on links below would be vacuous.
            snapshot(fx).use { s -> assertTrue(s.cloud.rows("BibleBookmarkToLabel").any { it.toOne["bookmark"] == victim.pk }, "victim has no label link") }
            fx.updateCloudStore("UPDATE ZBIBLEBOOKMARK SET ZORDINALTRUSTSTATERAW = '$state' WHERE Z_PK = ${victim.pk}")
            // Plainly: this priming read hides the effects of the FIRST read of a store (-wal/-shm creation or change),
            // which belong to the reader (Task 4's sourceIsNotModified), not to the mapper. Only the mapper run is measured below.
            snapshot(fx).use { s -> s.cloud.map.entities.keys.forEach { s.cloud.rows(it) } }
            val before = sha256Of(fx.root.toFile())

            runBlocking {
                withStaging { staging ->
                    val report = MigrationReport()
                    snapshot(fx).use { BookmarksMapper().map(it, staging, report) }
                    val hex = victimId.replace("-", "").uppercase()
                    assertEquals(total - 1, count(staging, "BibleBookmark"), state)
                    assertTrue(hex !in bibleBookmarkIds(staging), state)
                    assertEquals(0, scalar(staging, "SELECT COUNT(*) FROM BibleBookmarkNotes WHERE hex(bookmarkId) = '$hex'"), state)
                    assertEquals(0, scalar(staging, "SELECT COUNT(*) FROM BibleBookmarkToLabel WHERE hex(bookmarkId) = '$hex'"), state)
                    val skips = report.entries.filterIsInstance<ReportEntry.Skipped>()
                    assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.BOOKMARKS, reason, 1, "BibleBookmark")), skips.filter { it.reason.startsWith("ordinal trust") }, state)
                    // Nothing else is skipped: exactly the trust entry plus the hidden bookmark's own note and label links.
                    assertEquals(hiddenChildren(fx, victim.pk).toSet(), skips.filter { !it.reason.startsWith("ordinal trust") }.toSet(), state)
                    assertTrue(hiddenChildren(fx, victim.pk).size == 2, "the victim owns both a note and a link")
                    // Everything else still equals the oracle, except the one bookmark (and its note and links) the oracle also leaves out.
                    val diffs = compareWithOracleDetailed(fx, LegacyCategory.BOOKMARKS, staging, approved).differences
                    assertTrue(diffs.all { it.contains("missing in staged database") }, diffs.toString())
                    assertTrue(diffs.any { it.startsWith("BOOKMARKS.BibleBookmark[") }, state)
                }
            }
            // The legacy store is never modified: not the databases, not the -wal files (the -shm index is rebuildable).
            val after = sha256Of(fx.root.toFile())
            val changed = (before.keys + after.keys).filter { before[it] != after[it] }.sorted()
            assertEquals(emptyList(), changed.filterNot { it.endsWith("-shm") }, "$state: changed source files")
        }
    }

    /** The skips the mapper must report for the children (label links, note) of the hidden Bible bookmark [pk]. */
    private fun hiddenChildren(fx: FixtureDir, pk: Long): List<ReportEntry.Skipped> = snapshot(fx).use { s ->
        val id = (s.cloud.rows("BibleBookmark").first { it.pk == pk }.values["id"] as LegacyValue.UuidV).value
        val links = s.cloud.rows("BibleBookmarkToLabel").count { it.toOne["bookmark"] == pk }
        val notes = s.cloud.rows("BibleBookmarkNotes").count { (it.values["bookmarkId"] as? LegacyValue.UuidV)?.value == id }
        listOfNotNull(
            links.takeIf { it > 0 }?.let { ReportEntry.Skipped(LegacyCategory.BOOKMARKS, "Bible bookmark label link of a hidden bookmark", it, "BibleBookmarkToLabel") },
            notes.takeIf { it > 0 }?.let { ReportEntry.Skipped(LegacyCategory.BOOKMARKS, "note of a hidden Bible bookmark", it, "BibleBookmarkNotes") },
        )
    }

    private fun scalar(staging: StagingDatabases, sql: String): Int = runBlocking {
        staging.bookmarks.useReaderConnection { c -> c.usePrepared(sql) { st -> st.step(); st.getLong(0).toInt() } }
    }

    @Test
    fun inconsistentVerifiedMetadataIsReportedAsUnverified() {
        // verifiedAndroid with a wrong mapping version, then with an out-of-range KJVA ordinal, then wrong provenance.
        val cases = listOf(
            "ZORDINALMAPPINGVERSION = 0",
            "ZKJVORDINALEND = 99999999",
            "ZORDINALPROVENANCERAW = 'unknown'",
            "ZORDINALSOURCEVERSIFICATION = 'NoSuchV11n'",
        )
        for (change in cases) {
            val fx = fixture("1.0.2", "18", "restored-android")
            val pk = snapshot(fx).use { s -> s.cloud.rows("BibleBookmark").first { (it.values["ordinalTrustStateRaw"] as? LegacyValue.TextV)?.value == "verifiedAndroid" }.pk }
            fx.updateCloudStore("UPDATE ZBIBLEBOOKMARK SET $change WHERE Z_PK = $pk")
            runBlocking {
                withStaging { staging ->
                    val report = MigrationReport()
                    snapshot(fx).use { BookmarksMapper().map(it, staging, report) }
                    assertEquals(
                        listOf(ReportEntry.Skipped(LegacyCategory.BOOKMARKS, "ordinal trust verifiedAndroid inconsistent", 1, "BibleBookmark")),
                        report.entries.filterIsInstance<ReportEntry.Skipped>().filter { it.reason.startsWith("ordinal trust") },
                        change,
                    )
                    // The only other skips are the hidden bookmark's own children.
                    assertEquals(
                        hiddenChildren(fx, pk).toSet(),
                        report.entries.filterIsInstance<ReportEntry.Skipped>().filter { !it.reason.startsWith("ordinal trust") }.toSet(),
                        change,
                    )
                }
            }
        }
    }

    // ---- verifiedMappingV1: the KJVA range is re-derived through JSword like Swift's isTrustedKJVARange -------

    private fun kjvaOrdinal(v11n: String, ordinal: Int): Int? {
        val verse = Verse(Versifications.instance().getVersification(v11n)!!, ordinal)
        return if (verse.isConvertibleTo(KJVA)) verse.toV11n(KJVA).ordinal else null
    }

    /** A verifiedMappingV1 row whose source range is [start]..[end] in [v11n]; [kjvaStart]/[kjvaEnd] default to the re-derived ones. */
    private fun v1Row(
        id: Int, v11n: String, start: Int, end: Int,
        kjvaStart: Int? = kjvaOrdinal(v11n, start), kjvaEnd: Int? = kjvaOrdinal(v11n, end),
        provenance: String = "nativeMapping", initials: String = "ESV", mapping: Long = 1,
    ) = LegacyRow(
        id.toLong(),
        mapOf(
            "id" to u(id), "ordinalTrustStateRaw" to t("verifiedMappingV1"), "ordinalMappingVersion" to i(mapping),
            "ordinalProvenanceRaw" to t(provenance), "bookInitials" to t(initials), "v11n" to t(v11n),
            "ordinalSourceVersification" to t(v11n), "ordinalSourceStart" to i(start.toLong()), "ordinalSourceEnd" to i(end.toLong()),
            "kjvOrdinalStart" to i(kjvaStart!!.toLong()), "kjvOrdinalEnd" to i(kjvaEnd!!.toLong()),
            "ordinalStart" to i(start.toLong()), "ordinalEnd" to i(end.toLong()),
        ),
        emptyMap(),
    )

    private fun trustOutcome(row: LegacyRow): Pair<Int, List<ReportEntry.Skipped>> {
        val report = MigrationReport()
        val stage = BookmarksMapper().build(data(bible = listOf(row)), report)
        return stage.bibleBookmarks.size to report.entries.filterIsInstance<ReportEntry.Skipped>()
    }

    private val v1Mismatch = listOf(ReportEntry.Skipped(LegacyCategory.BOOKMARKS, "ordinal trust verifiedMappingV1 inconsistent", 1, "BibleBookmark"))

    /** A (versification, ordinal) whose KJVA ordinal differs from the source ordinal, found in JSword's built-in systems. */
    private fun differingVerse(): Triple<String, Int, Int> {
        for (name in listOf("Synodal", "SynodalProt", "Vulg", "Luther", "German", "Catholic", "Catholic2", "NRSV", "Leningrad", "MT", "Segond", "Calvin")) {
            if (Versifications.instance().getVersification(name) == null) continue
            for (ordinal in listOf(1000, 5000, 20000, 25000, 30000, 31000)) {
                val kjva = runCatching { kjvaOrdinal(name, ordinal) }.getOrNull() ?: continue
                if (kjva != ordinal) return Triple(name, ordinal, kjva)
            }
        }
        error("no built-in versification with differing ordinals found")
    }

    @Test
    fun verifiedMappingV1IsTrustedWhenTheRederivedKjvaRangeMatches() {
        assertEquals(1 to emptyList(), trustOutcome(v1Row(10, "KJV", 100, 103)))
        assertEquals(1 to emptyList(), trustOutcome(v1Row(11, "KJV", 100, 103, provenance = "legacyMigration")))
        val (name, ordinal, kjva) = differingVerse()
        // The stored KJVA ordinals equal the mapped ones, not the source ordinals: accepted.
        assertEquals(1 to emptyList(), trustOutcome(v1Row(12, name, ordinal, ordinal + 1)), "$name $ordinal -> $kjva")
    }

    @Test
    fun verifiedMappingV1IsRejectedWhenMetadataOrRederivedRangeDisagrees() {
        val (name, ordinal, kjva) = differingVerse()
        val rejected = mapOf(
            "blank bookInitials" to v1Row(10, "KJV", 100, 103, initials = "  "),
            "androidImport provenance" to v1Row(11, "KJV", 100, 103, provenance = "androidImport"),
            "unknown provenance" to v1Row(12, "KJV", 100, 103, provenance = "unknown"),
            "mapping version 0" to v1Row(13, "KJV", 100, 103, mapping = 0),
            // Stored KJVA ordinals are the unmapped source ordinals: the re-derivation differs.
            "stored kjva differs" to v1Row(14, name, ordinal, ordinal + 1, kjvaStart = ordinal, kjvaEnd = ordinal + 1),
            "stored kjva end differs" to v1Row(15, "KJV", 100, 103, kjvaEnd = kjvaOrdinal("KJV", 103)!! + 1),
        )
        assertTrue(kjva != ordinal)
        for ((what, row) in rejected) {
            val (written, skipped) = trustOutcome(row)
            assertEquals(0, written, what)
            assertEquals(v1Mismatch, skipped, what)
        }
    }

    @Test
    fun verifiedMappingV1IsRejectedWhenTheSourceEndIsPastTheSourceCanon() {
        // Verse() alone would clamp the ordinal to the last verse; Swift's JSwordCanon.reference(at:) finds none.
        val past = Versifications.instance().getVersification("KJV")!!.maximumOrdinal() + 50
        val row = v1Row(10, "KJV", 100, past, kjvaEnd = BookmarksMapper.MAX_KJVA_ORDINAL.toInt())
        assertEquals(0 to v1Mismatch, trustOutcome(row))
    }

    @Test
    fun verifiedMappingV1RequiresAnOrderedSourceRange() {
        // Swift: sourceEnd >= sourceStart, checked before any mapping (a reversing mapping cannot be built by hand here).
        val start = kjvaOrdinal("KJV", 103)!!
        val end = kjvaOrdinal("KJV", 100)!!
        assertEquals(0 to v1Mismatch, trustOutcome(v1Row(10, "KJV", 103, 100, kjvaStart = minOf(start, end), kjvaEnd = maxOf(start, end))))
    }

    // ---- Staged read-back with distinct values, and the INSERT column lists --------------------------------

    private fun <T> readBack(staging: StagingDatabases, sql: String, read: (androidx.sqlite.SQLiteStatement) -> T): List<T> = runBlocking {
        staging.bookmarks.useReaderConnection { c -> c.usePrepared(sql) { st -> buildList { while (st.step()) add(read(st)) } } }
    }

    @Test
    fun mapWritesEveryColumnOfBibleAndGenericBookmarksToItsOwnStagedColumn() {
        val label = uuid(1)
        val bible = LegacyRow(
            10,
            trusted + mapOf(
                "id" to u(10), "createdAt" to d(1_700_000_000_123), "lastUpdatedOn" to d(1_800_000_000_456),
                "kjvOrdinalStart" to i(101), "kjvOrdinalEnd" to i(102), "ordinalStart" to i(103), "ordinalEnd" to i(104),
                "v11n" to t("KJV"), "bookInitials" to t("ESV"), "startOffset" to i(7), "endOffset" to i(9),
                "primaryLabelId" to LegacyValue.UuidV(label), "wholeVerse" to b(false), "type" to t("EXAMPLE"), "customIcon" to t("heart"),
                "sourcePromptId" to u(77), "editAction.mode.mode" to t("PREPEND"), "editAction.content" to t("added"),
                "playbackSettings.speed" to i(150),
            ),
            emptyMap(),
        )
        val generic = LegacyRow(
            11,
            mapOf(
                "id" to u(11), "key" to t("Gen.1.1"), "createdAt" to d(1_600_000_000_111), "bookInitials" to t("Josephus"),
                "ordinalStart" to i(21), "ordinalEnd" to i(22), "startOffset" to i(3), "endOffset" to i(5),
                "primaryLabelId" to LegacyValue.UuidV(label), "lastUpdatedOn" to d(1_650_000_000_222), "wholeVerse" to b(false),
                "customIcon" to t("star"), "sourcePromptId" to u(78), "editAction.mode.mode" to t("APPEND"), "editAction.content" to t("tail"),
                "playbackSettings.speed" to i(90),
            ),
            emptyMap(),
        )
        runBlocking {
            withStaging { staging ->
                BookmarksMapper().map(data(labels = listOf(labelRow(1, label, "L")), bible = listOf(bible), generic = listOf(generic)), staging, MigrationReport())
                val hex = { n: Int -> uuid(n).replace("-", "") }
                val b = readBack(
                    staging,
                    "SELECT kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, lower(hex(id)), createdAt, book, startOffset, endOffset, " +
                        "lower(hex(primaryLabelId)), lastUpdatedOn, wholeVerse, type, customIcon, lower(hex(sourcePromptId)), editAction_mode, " +
                        "editAction_content, playbackSettings FROM BibleBookmark",
                ) { st -> (0 until 19).map { if (st.isNull(it)) null else st.getText(it) } }.single()
                assertEquals(
                    listOf(
                        "101", "102", "103", "104", "KJV", hex(10), "1700000000123", "ESV", "7", "9", hex(1), "1800000000456", "0", "EXAMPLE",
                        "heart", hex(77), "PREPEND", "added",
                    ),
                    b.take(18),
                )
                assertTrue(b[18]!!.contains("\"speed\":150"))
                val g = readBack(
                    staging,
                    "SELECT lower(hex(id)), `key`, createdAt, bookInitials, ordinalStart, ordinalEnd, startOffset, endOffset, lower(hex(primaryLabelId)), " +
                        "lastUpdatedOn, wholeVerse, customIcon, lower(hex(sourcePromptId)), editAction_mode, editAction_content, playbackSettings FROM GenericBookmark",
                ) { st -> (0 until 16).map { if (st.isNull(it)) null else st.getText(it) } }.single()
                assertEquals(
                    listOf(
                        hex(11), "Gen.1.1", "1600000000111", "Josephus", "21", "22", "3", "5", hex(1), "1650000000222", "0", "star", hex(78),
                        "APPEND", "tail",
                    ),
                    g.take(15),
                )
                assertTrue(g[15]!!.contains("\"speed\":90"))
            }
        }
    }

    @Test
    fun insertColumnListsCoverEveryColumnOfTheStagedTables() {
        // A schema bump (a new Android column) must be a deliberate review point: it fails here until the INSERT is updated.
        val statements = listOf(
            BookmarksMapper.LABEL_SQL, BookmarksMapper.BIBLE_BOOKMARK_SQL, BookmarksMapper.GENERIC_BOOKMARK_SQL,
            BookmarksMapper.noteSql("BibleBookmarkNotes"), BookmarksMapper.noteSql("GenericBookmarkNotes"),
            BookmarksMapper.linkSql("BibleBookmarkToLabel"), BookmarksMapper.linkSql("GenericBookmarkToLabel"),
            BookmarksMapper.STUDYPAD_ENTRY_SQL, BookmarksMapper.STUDYPAD_TEXT_SQL,
        )
        runBlocking {
            withStaging { staging ->
                staging.bookmarks.useReaderConnection { c ->
                    for (sql in statements) {
                        val m = Regex("INSERT INTO (\\w+) \\((.*?)\\) VALUES \\((.*)\\)").matchEntire(sql)!!
                        val table = m.groupValues[1]
                        val columns = m.groupValues[2].split(",").map { it.trim().trim('`') }
                        val placeholders = m.groupValues[3].count { it == '?' }
                        val actual = c.usePrepared("PRAGMA table_info(`$table`)") { st -> buildList { while (st.step()) add(st.getText(1)) } }
                        assertEquals(actual.toSet(), columns.toSet(), "columns of $table")
                        assertEquals(columns.size, placeholders, "placeholders of $table")
                    }
                }
            }
        }
    }

    // ---- Hand-built rows: distinct values the fixtures cannot tell apart -------------------------------------

    private fun uuid(n: Int) = "14000000-0000-0000-0000-%012d".format(n)
    private fun t(s: String) = LegacyValue.TextV(s)
    private fun i(n: Long) = LegacyValue.IntV(n)
    private fun b(v: Boolean) = LegacyValue.BoolV(v)
    private fun u(n: Int) = LegacyValue.UuidV(uuid(n))
    private fun d(ms: Long) = LegacyValue.DateV(ms)

    private fun labelRow(pk: Long, id: String, name: String, vararg extra: Pair<String, LegacyValue?>) =
        LegacyRow(pk, mapOf("id" to LegacyValue.UuidV(id), "name" to t(name)) + extra, emptyMap())

    private fun data(
        labels: List<LegacyRow> = emptyList(),
        bible: List<LegacyRow> = emptyList(),
        bibleNotes: List<LegacyRow> = emptyList(),
        bibleLinks: List<LegacyRow> = emptyList(),
        generic: List<LegacyRow> = emptyList(),
        genericNotes: List<LegacyRow> = emptyList(),
        genericLinks: List<LegacyRow> = emptyList(),
        entries: List<LegacyRow> = emptyList(),
        texts: List<LegacyRow> = emptyList(),
        settings: Map<String, String> = emptyMap(),
    ) = LegacyBookmarkData(labels, bible, bibleNotes, bibleLinks, generic, genericNotes, genericLinks, entries, texts, settings)

    private val trusted = mapOf<String, LegacyValue?>(
        "ordinalTrustStateRaw" to t("verifiedAndroid"), "ordinalMappingVersion" to i(1), "ordinalProvenanceRaw" to t("androidImport"),
        "ordinalSourceVersification" to t("KJV"), "ordinalSourceStart" to i(11), "ordinalSourceEnd" to i(12),
        "kjvOrdinalStart" to i(101), "kjvOrdinalEnd" to i(102),
    )

    @Test
    fun labelStyleBooleansMapLikeTheDisplayStyleMigration() {
        fun row(pk: Long, name: String, hide: Boolean, marker: Boolean, underline: Boolean, hideW: Boolean, markerW: Boolean, underlineW: Boolean) =
            labelRow(
                pk, uuid(pk.toInt()), name,
                "hideStyle" to b(hide), "markerStyle" to b(marker), "underlineStyle" to b(underline),
                "hideStyleWholeVerse" to b(hideW), "markerStyleWholeVerse" to b(markerW), "underlineStyleWholeVerse" to b(underlineW),
                "color" to i(0xFF112233L), "favourite" to b(true), "type" to t("EXAMPLE"), "customIcon" to t("star"),
            )
        val stage = BookmarksMapper().build(
            data(
                labels = listOf(
                    row(1, "a-none", false, false, false, false, false, false),
                    row(2, "b-underline", false, false, true, false, false, true),
                    row(3, "c-marker", false, true, false, false, true, false),
                    row(4, "d-hide", true, false, false, true, false, false),
                    row(5, "e-hide-wins", true, true, true, false, false, false),
                    row(6, "f-marker-wins", false, true, true, false, false, false),
                    row(7, "g-whole-differs", false, false, false, true, false, false),
                    row(8, "h-both-differ", false, false, true, false, true, false),
                ),
            ),
            MigrationReport(),
        )
        val styles = stage.labels.associate { it.name to (it.displayStyle to it.displayStyleWholeVerse) }
        assertEquals(
            mapOf(
                "a-none" to (0 to null), "b-underline" to (1 to null), "c-marker" to (2 to null), "d-hide" to (3 to null),
                "e-hide-wins" to (3 to 0), "f-marker-wins" to (2 to 0), "g-whole-differs" to (0 to 3), "h-both-differ" to (1 to 2),
            ),
            styles,
        )
        val first = stage.labels.first()
        assertEquals(listOf(true, "EXAMPLE", "star", 0xFF112233L.toInt()), listOf(first.favourite, first.type, first.customIcon, first.color))
    }

    @Test
    fun systemLabelsGetTheFixedIdsAndEverythingReferencingThemFollows() {
        val localSpeak = uuid(1)
        val localUnlabeled = uuid(2)
        val localPara = uuid(3)
        val localAi = uuid(4)
        val user = uuid(5)
        val stage = BookmarksMapper().build(
            data(
                labels = listOf(
                    labelRow(1, localSpeak, "__SPEAK_LABEL__"), labelRow(2, localUnlabeled, "__UNLABELED__"),
                    labelRow(3, localPara, "__PARAGRAPH_BREAK_LABEL__"), labelRow(4, localAi, "__AI_LABEL__"), labelRow(5, user, "Mine"),
                ),
                bible = listOf(
                    LegacyRow(
                        10, trusted + mapOf("id" to u(10), "createdAt" to d(1), "primaryLabelId" to LegacyValue.UuidV(localSpeak)),
                        emptyMap(),
                    ),
                ),
                bibleLinks = listOf(
                    LegacyRow(20, mapOf("orderNumber" to i(1)), mapOf("bookmark" to 10L, "label" to 1L)),
                    LegacyRow(21, mapOf("orderNumber" to i(2)), mapOf("bookmark" to 10L, "label" to 5L)),
                ),
                entries = listOf(
                    LegacyRow(30, mapOf("id" to u(30), "orderNumber" to i(0)), mapOf("label" to 4L)),
                ),
            ),
            MigrationReport(),
        )
        assertEquals(
            setOf(SPEAK_LABEL_ID, UNLABELED_LABEL_ID, PARAGRAPH_BREAK_LABEL_ID, AI_LABEL_ID, IdType.fromString(user)),
            stage.labels.map { it.id }.toSet(),
        )
        assertEquals(SPEAK_LABEL_ID, stage.bibleBookmarks.single().primaryLabelId)
        assertEquals(setOf(SPEAK_LABEL_ID, IdType.fromString(user)), stage.bibleLinks.map { it.labelId }.toSet())
        assertEquals(AI_LABEL_ID, stage.studyPadEntries.single().labelId)
    }

    @Test
    fun everyBibleBookmarkColumnComesFromItsOwnSourceField() {
        // Distinct values everywhere (the fixtures' booleans alternate by parity and cannot show a swap).
        val labelId = uuid(1)
        val row = LegacyRow(
            10,
            trusted + mapOf(
                "id" to u(10), "createdAt" to d(1_700_000_000_123), "lastUpdatedOn" to d(1_800_000_000_456),
                "kjvOrdinalStart" to i(101), "kjvOrdinalEnd" to i(102), "ordinalStart" to i(103), "ordinalEnd" to i(104),
                "v11n" to t("KJV"), "bookInitials" to t("  ESV "), "book" to t("Genesis"),
                "startOffset" to i(7), "endOffset" to i(9), "primaryLabelId" to LegacyValue.UuidV(labelId),
                "wholeVerse" to b(false), "type" to t("EXAMPLE"), "customIcon" to t("heart"), "sourcePromptId" to u(77),
                "editAction.mode.mode" to t("PREPEND"), "editAction.content" to t("added"),
                "playbackSettings.speakChapterChanges" to b(false), "playbackSettings.speakTitles" to b(true),
                "playbackSettings.speakFootnotes" to b(true), "playbackSettings.speed" to i(150),
                "playbackSettings.bookId" to t("NET/x"), "playbackSettings.bookmarkWasCreated" to b(true),
                "playbackSettings.verseRangeAndroidString" to t("KJV::Gen.1.1-Gen.1.3"),
            ),
            emptyMap(),
        )
        val stage = BookmarksMapper().build(data(labels = listOf(labelRow(1, labelId, "L")), bible = listOf(row)), MigrationReport())
        val bm = stage.bibleBookmarks.single()
        assertEquals(
            listOf<Any?>(101, 102, 103, 104, "KJV", "ESV", 1_700_000_000_123L, 1_800_000_000_456L, 7, 9, false, "EXAMPLE", "heart", "PREPEND", "added"),
            listOf(
                bm.kjvOrdinalStart, bm.kjvOrdinalEnd, bm.ordinalStart, bm.ordinalEnd, bm.v11n, bm.book, bm.createdAt, bm.lastUpdatedOn,
                bm.startOffset, bm.endOffset, bm.wholeVerse, bm.type, bm.customIcon, bm.editActionMode, bm.editActionContent,
            ),
        )
        assertEquals(IdType.fromString(uuid(77)), bm.sourcePromptId)
        assertEquals(IdType.fromString(labelId), bm.primaryLabelId)
        // Sorted keys, explicit nulls only for absent fields, `/` escaped like NSJSONSerialization.
        assertEquals(
            """{"bookId":"NET\/x","bookmarkWasCreated":true,"speakChapterChanges":false,"speakFootnotes":true,"speakTitles":true,"speed":150,"verseRange":"KJV::Gen.1.1-Gen.1.3"}""",
            bm.playbackSettings,
        )
    }

    @Test
    fun playbackWithOnlyDefaultsAndWithoutAnyColumns() {
        val withDefaults = LegacyRow(
            10, trusted + mapOf(
                "id" to u(10), "playbackSettings.speakChapterChanges" to b(true), "playbackSettings.speakTitles" to b(true),
                "playbackSettings.speakFootnotes" to b(false), "playbackSettings.speed" to i(100),
            ), emptyMap(),
        )
        val without = LegacyRow(11, trusted + mapOf("id" to u(11)), emptyMap())
        val stage = BookmarksMapper().build(data(bible = listOf(withDefaults, without)), MigrationReport())
        val byId = stage.bibleBookmarks.associateBy { it.id.toString() }
        assertEquals(
            """{"bookId":null,"bookmarkWasCreated":null,"speakChapterChanges":true,"speakFootnotes":false,"speakTitles":true,"speed":100,"verseRange":null}""",
            byId.getValue(uuid(10)).playbackSettings,
        )
        assertNull(byId.getValue(uuid(11)).playbackSettings)
    }

    @Test
    fun androidFidelityOverridesBookAndFillsMissingPlayback() {
        val keep = uuid(10)
        val nulled = uuid(11)
        val plain = "14000000-0000-0000-0000-0000000000ab"
        fun bible(id: String, pk: Long) = LegacyRow(pk, trusted + mapOf("id" to LegacyValue.UuidV(id), "bookInitials" to t("ESV")), emptyMap())
        val stage = BookmarksMapper().build(
            data(
                bible = listOf(bible(keep, 10), bible(nulled, 11), bible(plain, 12)),
                generic = listOf(LegacyRow(13, mapOf("id" to u(13), "key" to t("k"), "bookInitials" to t("Josephus")), emptyMap())),
                settings = mapOf(
                    "remote_sync.bookmarks.android_book.$keep" to "NET",
                    "remote_sync.bookmarks.android_book.$nulled" to "__android_null__",
                    "remote_sync.bookmarks.android_playback_settings.bible.$keep" to """{"speed":120}""",
                    "remote_sync.bookmarks.android_playback_settings.generic.${uuid(13)}" to """{"speed":130}""",
                    // The Swift reader ignores upper-case spellings, empty values and unknown kinds.
                    "remote_sync.bookmarks.android_book.${plain.uppercase()}" to "IGNORED",
                    "remote_sync.bookmarks.android_playback_settings.bible.$plain" to "",
                    "remote_sync.bookmarks.android_playback_settings.other.$plain" to "{}",
                ),
            ),
            MigrationReport().also { report = it },
        )
        val byId = stage.bibleBookmarks.associateBy { it.id.toString() }
        assertEquals("NET", byId.getValue(keep).book)
        assertNull(byId.getValue(nulled).book)
        assertEquals("ESV", byId.getValue(plain).book)
        assertEquals("""{"speed":120}""", byId.getValue(keep).playbackSettings)
        assertNull(byId.getValue(plain).playbackSettings)
        assertEquals("""{"speed":130}""", stage.genericBookmarks.single().playbackSettings)
        assertEquals(
            listOf(ReportEntry.Skipped(LegacyCategory.BOOKMARKS, "malformed bookmark fidelity settings", 3)),
            report!!.entries.filterIsInstance<ReportEntry.Skipped>(),
        )
    }

    private var report: MigrationReport? = null

    @Test
    fun linkAndStudyPadFieldsAreNotSwapped() {
        val l1 = uuid(1)
        val l2 = uuid(2)
        val stage = BookmarksMapper().build(
            data(
                labels = listOf(labelRow(1, l1, "A"), labelRow(2, l2, "B")),
                bible = listOf(LegacyRow(10, trusted + mapOf("id" to u(10), "createdAt" to d(5)), emptyMap())),
                generic = listOf(LegacyRow(11, mapOf("id" to u(11), "key" to t("Gen.1"), "bookInitials" to t("Bk"), "createdAt" to d(6), "wholeVerse" to b(false)), emptyMap())),
                bibleLinks = listOf(
                    LegacyRow(20, mapOf("orderNumber" to i(3), "indentLevel" to i(4), "expandContent" to b(true)), mapOf("bookmark" to 10L, "label" to 2L)),
                    LegacyRow(21, mapOf("orderNumber" to i(1), "indentLevel" to i(2), "expandContent" to b(false)), mapOf("bookmark" to 10L, "label" to 1L)),
                ),
                genericLinks = listOf(
                    LegacyRow(22, mapOf("orderNumber" to i(8), "indentLevel" to i(9), "expandContent" to b(true)), mapOf("bookmark" to 11L, "label" to 1L)),
                ),
                bibleNotes = listOf(
                    LegacyRow(
                        40, mapOf("bookmarkId" to u(10), "notes" to t("note"), "contentType" to t("MARKDOWN"), "sourcePromptId" to u(55)),
                        mapOf("bookmark" to 10L),
                    ),
                    // An invalid content type reads as NULL (inherit), not as HTML.
                    LegacyRow(41, mapOf("bookmarkId" to u(11), "notes" to t("orphan"), "contentType" to t("RTF")), emptyMap()),
                ),
                genericNotes = listOf(LegacyRow(42, mapOf("bookmarkId" to u(11), "notes" to t("g"), "contentType" to t("RTF")), emptyMap())),
                entries = listOf(
                    LegacyRow(30, mapOf("id" to u(30), "orderNumber" to i(6), "indentLevel" to i(7), "contentType" to t("HTML"), "sourcePromptId" to u(56)), mapOf("label" to 2L)),
                    LegacyRow(31, mapOf("id" to u(31), "orderNumber" to i(5), "indentLevel" to i(0)), emptyMap()),
                ),
                texts = listOf(
                    LegacyRow(50, mapOf("studyPadTextEntryId" to u(30), "text" to t("entry text")), emptyMap()),
                    LegacyRow(51, mapOf("studyPadTextEntryId" to u(31), "text" to t("label-less entry")), emptyMap()),
                ),
            ),
            MigrationReport().also { report = it },
        )
        assertEquals(
            listOf(listOf(l1, 1, 2, false), listOf(l2, 3, 4, true)),
            stage.bibleLinks.sortedBy { it.orderNumber }.map { listOf(it.labelId.toString(), it.orderNumber, it.indentLevel, it.expandContent) },
        )
        assertEquals(listOf(l1, 8, 9, true), stage.genericLinks.single().let { listOf(it.labelId.toString(), it.orderNumber, it.indentLevel, it.expandContent) })
        assertEquals(StagedNote(IdType.fromString(uuid(10)), "note", "MARKDOWN", IdType.fromString(uuid(55))), stage.bibleNotes.single())
        assertNull(stage.genericNotes.single().contentType)
        assertFalse(stage.genericBookmarks.single().wholeVerse)
        val entry = stage.studyPadEntries.single()
        assertEquals(listOf(l2, 6, 7, "HTML", IdType.fromString(uuid(56))), listOf(entry.labelId.toString(), entry.orderNumber, entry.indentLevel, entry.contentType, entry.sourcePromptId))
        assertEquals(listOf("entry text"), stage.studyPadTexts.map { it.text })
        val skipped = report!!.entries.filterIsInstance<ReportEntry.Skipped>().associate { it.reason to it.rows }
        assertEquals(
            mapOf(
                "Bible bookmark note without bookmark" to 1, // note 41 points at a generic bookmark's id
                "StudyPad entry without label" to 1,
                "StudyPad text without exported entry" to 1,
                "unknown content type dropped" to 2,
            ),
            skipped,
        )
    }

    @Test
    fun valuesTheCurrentAndroidReadersWouldRejectAreNotWritten() {
        val unknownLabel = uuid(99)
        val stage = BookmarksMapper().build(
            data(
                labels = listOf(labelRow(1, uuid(1), "L", "type" to t("BOGUS"))),
                bible = listOf(
                    LegacyRow(10, trusted + mapOf("id" to u(10), "v11n" to t("NoSuchV11n")), emptyMap()),
                    LegacyRow(
                        11,
                        trusted + mapOf(
                            "id" to u(11), "primaryLabelId" to LegacyValue.UuidV(unknownLabel), "type" to t("BOGUS"),
                            "editAction.mode.mode" to t("BOGUS"), "playbackSettings.speed" to i(100),
                            "playbackSettings.verseRangeAndroidString" to t("NoSuchV11n::Gen.1.1"),
                        ),
                        emptyMap(),
                    ),
                ),
            ),
            MigrationReport().also { report = it },
        )
        assertEquals(listOf(uuid(11)), stage.bibleBookmarks.map { it.id.toString() })
        val bm = stage.bibleBookmarks.single()
        assertEquals(listOf(null, null, null), listOf(bm.primaryLabelId, bm.type, bm.editActionMode))
        assertTrue(bm.playbackSettings!!.endsWith(""""verseRange":null}"""))
        assertNull(stage.labels.single().type)
        val skipped = report!!.entries.filterIsInstance<ReportEntry.Skipped>().associate { it.reason to it.rows }
        assertEquals(1, skipped["Bible bookmark with unknown versification"])
        assertEquals(1, skipped["primary label of an unknown label dropped"])
        assertEquals(1, skipped["unknown bookmark type dropped"])
        assertEquals(1, skipped["unknown edit action mode dropped"])
        assertEquals(1, skipped["unknown label type dropped"])
        assertEquals(1, skipped["playback verse range not parseable by JSword dropped"])
    }

    @Test
    fun unexpectedValueTypesAreReportedNotDropped() {
        val stage = BookmarksMapper().build(
            data(bible = listOf(LegacyRow(10, trusted + mapOf("id" to u(10), "startOffset" to t("seven")), emptyMap()))),
            MigrationReport().also { report = it },
        )
        assertNull(stage.bibleBookmarks.single().startOffset)
        assertEquals(
            listOf(ReportEntry.Skipped(LegacyCategory.BOOKMARKS, "unexpected value type TextV for startOffset", 1)),
            report!!.entries.filterIsInstance<ReportEntry.Skipped>(),
        )
    }

    @Test
    fun textIsStoredByteExactIncludingNulAndLargeValues() {
        val nul = "before\u0000after 😀 שלום"
        val big = "x".repeat(200_000) + "‮👍"
        runBlocking {
            withStaging { staging ->
                BookmarksMapper().map(
                    data(
                        labels = listOf(labelRow(1, uuid(1), nul)),
                        bible = listOf(LegacyRow(10, trusted + mapOf("id" to u(10)), emptyMap())),
                        bibleNotes = listOf(LegacyRow(40, mapOf("bookmarkId" to u(10), "notes" to t(nul + big)), emptyMap())),
                        generic = listOf(LegacyRow(11, mapOf("id" to u(11), "key" to t(nul), "bookInitials" to t("B")), emptyMap())),
                    ),
                    staging, MigrationReport(),
                )
                val stored = staging.bookmarks.useReaderConnection { c ->
                    c.usePrepared("SELECT name FROM Label") { st -> st.step(); st.getBlob(0) } to
                        c.usePrepared("SELECT notes FROM BibleBookmarkNotes") { st -> st.step(); st.getBlob(0) } to
                        c.usePrepared("SELECT `key` FROM GenericBookmark") { st -> st.step(); st.getBlob(0) }
                }
                assertTrue(nul.encodeToByteArray().contentEquals(stored.first.first))
                assertTrue((nul + big).encodeToByteArray().contentEquals(stored.first.second))
                assertTrue(nul.encodeToByteArray().contentEquals(stored.second))
            }
        }
    }

    @Test
    fun richFixtureNotesAreInTheStagedDatabaseByteExact() {
        // The fixture notes (emoji, right-to-left text, NUL, 200 kB) must arrive unchanged: compare the bytes of every
        // note against the legacy store, not only against the oracle.
        val fx = fixture("1.0.2", "18", "rich")
        runBlocking {
            withStaging { staging ->
                snapshot(fx).use { snap ->
                    BookmarksMapper().map(snap, staging, MigrationReport())
                    val legacy = snap.cloud.rows("BibleBookmarkNotes").associate {
                        (it.values["bookmarkId"] as LegacyValue.UuidV).value to (it.values["notes"] as LegacyValue.TextV).value
                    }
                    assertTrue(legacy.isNotEmpty())
                    val stored = staging.bookmarks.useReaderConnection { c ->
                        c.usePrepared("SELECT lower(hex(bookmarkId)), notes FROM BibleBookmarkNotes") { st ->
                            buildMap { while (st.step()) put(st.getText(0), st.getBlob(1)) }
                        }
                    }
                    assertEquals(legacy.size, stored.size)
                    for ((id, text) in legacy) {
                        val bytes = assertNotNull(stored[id.replace("-", "")], "note of $id")
                        assertTrue(text.encodeToByteArray().contentEquals(bytes), "note of $id differs")
                    }
                    assertTrue(legacy.values.any { it.length > 100_000 }, "fixture has no large note")
                    // No fixture note holds a NUL (the NUL case is `textIsStoredByteExactIncludingNulAndLargeValues`).
                    assertTrue(legacy.values.any { it.any { c -> c in '\u0590'..'\u05FF' || c in '\u0600'..'\u06FF' } }, "fixture has no right-to-left note")
                    assertTrue(legacy.values.any { it.any { c -> c.isHighSurrogate() } }, "fixture has no emoji note")
                }
            }
        }
    }
}
