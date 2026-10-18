package net.bible.service.legacymigration.map

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.bible.android.database.IdType
import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyStoreReader
import net.bible.service.legacymigration.read.LegacyValue
import net.bible.service.legacymigration.plist.LegacyUuid
import net.bible.service.legacymigration.stage.StagingDatabases
import net.bible.service.legacymigration.testsupport.FixtureDir
import net.bible.service.legacymigration.testsupport.JvmLegacyDatabaseFactory
import net.bible.service.legacymigration.testsupport.allFixtures
import net.bible.service.legacymigration.testsupport.compareWithOracle
import net.bible.service.legacymigration.testsupport.compareWithOracleDetailed
import net.bible.service.legacymigration.testsupport.ApprovedRow
import net.bible.service.legacymigration.testsupport.RowSide
import net.bible.service.legacymigration.testsupport.fixture
import net.bible.service.legacymigration.testsupport.sameCell
import net.bible.android.database.WorkspaceEntities
import okio.Path.Companion.toOkioPath

class WorkspacesMapperTest {
    private val staged = mutableListOf<File>()

    private fun tempDir(): File = Files.createTempDirectory("staging-").toFile().also { staged += it }

    private fun snapshot(fx: FixtureDir) = LegacySnapshot(
        LegacyStoreReader(File(fx.root.toFile(), "AndBible.store").path),
        LegacyStoreReader(File(fx.root.toFile(), "LocalStore.store").path),
    )

    private inline fun <T> withStaging(block: (StagingDatabases, File) -> T): T {
        val dir = tempDir()
        val staging = StagingDatabases(dir.toOkioPath(), JvmLegacyDatabaseFactory())
        try {
            return block(staging, dir)
        } finally {
            staging.close()
            dir.deleteRecursively()
        }
    }

    /** Maps [fx] and compares with its oracle; [tweak] edits the legacy rows first (used to plant a defect). */
    private fun mapAndCompare(fx: FixtureDir, tweak: (LegacyWorkspaceData) -> LegacyWorkspaceData = { it }) = runBlocking {
        withStaging { staging, _ ->
            snapshot(fx).use { snap ->
                WorkspacesMapper().map(tweak(LegacyWorkspaceData.from(snap)), staging, MigrationReport())
            }
            compareWithOracleDetailed(fx, LegacyCategory.WORKSPACES, staging, emptySet())
        }
    }

    @Test
    fun everyFixtureMatchesItsOracle() {
        val fixtures = allFixtures()
        assertEquals(24, fixtures.size)
        for (fx in fixtures) {
            val result = mapAndCompare(fx)
            assertEquals(emptyList(), result.differences, "${fx.app}/${fx.ios}/${fx.scenario}")
        }
    }

    @Test
    fun comparisonIsNotVacuousForRichFixtures() {
        // `rich` and `preserved-ai` hold workspaces; `fresh` and `restored-android` hold none, which must also compare equal.
        for (fx in allFixtures().filter { it.scenario == "rich" || it.scenario == "preserved-ai" }) {
            val counts = mapAndCompare(fx).comparedRows
            val label = "${fx.app}/${fx.ios}/${fx.scenario}"
            assertEquals(2, counts["Workspace"], label)
            assertEquals(3, counts["Window"], label)
            assertEquals(3, counts["PageManager"], label)
            assertEquals(9, counts["HistoryItem"], label)
        }
        val fresh = mapAndCompare(fixture("1.0.2", "18", "fresh"))
        assertEquals(0, fresh.comparedRows.getValue("Workspace"))
    }

    @Test
    fun plantedDefectIsReported() {
        // Same effect as a schema map that points textDisplaySettings.fontSize at ZMARGINLEFT.
        val defect: (LegacyWorkspaceData) -> LegacyWorkspaceData = { data ->
            LegacyWorkspaceData(
                workspaces = data.workspaces.map { row ->
                    row.copy(values = row.values + ("textDisplaySettings.fontSize" to row.values["textDisplaySettings.marginLeft"]))
                },
                windows = data.windows,
                pageManagers = data.pageManagers,
                historyItems = data.historyItems,
                settings = data.settings,
            )
        }
        val differences = mapAndCompare(fixture("1.0.2", "18", "rich"), defect).differences
        assertTrue(differences.isNotEmpty(), "planted defect went unnoticed")
        assertTrue(differences.all { it.contains("Workspace[") && it.contains("fontSize") }, differences.toString())
    }

    @Test
    fun approvedDifferenceIsSkippedOnlyForItsColumn() {
        val fx = fixture("1.0.2", "18", "rich")
        runBlocking {
            withStaging { staging, _ ->
                snapshot(fx).use { WorkspacesMapper().map(it, staging, MigrationReport()) }
                staging.workspaces.workspaceDao().let { dao ->
                    val first = dao.allWorkspaces().first()
                    first.name = "changed"
                    dao.updateWorkspace(first)
                }
                assertEquals(1, compareWithOracle(fx, LegacyCategory.WORKSPACES, staging, emptySet()).size)
                val approved = setOf(net.bible.service.legacymigration.testsupport.ApprovedDifference("Workspace", "name", "test"))
                assertEquals(emptyList(), compareWithOracle(fx, LegacyCategory.WORKSPACES, staging, approved))
            }
        }
    }

    @Test
    fun missingBibleBookEncodingColumnOf101StoreComesOutAtGenesisLikeTheOracle() {
        val fx = fixture("1.0.1", "18", "rich")
        snapshot(fx).use { snap ->
            val rows = snap.cloud.rows("PageManager")
            assertTrue(rows.isNotEmpty())
            // 1.0.1 pre-dates PageManager.bibleBookEncoding: the property does not exist in this store's model.
            assertTrue(rows.all { "bibleBookEncoding" !in it.values }, "1.0.1 store unexpectedly has bibleBookEncoding")
            assertTrue(rows.any { (it.values["bibleBibleBook"] as LegacyValue.IntV).value != 2L }, "fixture should hold a non-Genesis location")
        }
        runBlocking {
            withStaging { staging, _ ->
                snapshot(fx).use { WorkspacesMapper().map(it, staging, MigrationReport()) }
                val dao = staging.workspaces.workspaceDao()
                val verses = dao.allWindows().map { dao.pageManager(it.id)!!.biblePage.verse }
                assertEquals(3, verses.size)
                for (v in verses) assertEquals(listOf("KJV", "2", "1", "1"), listOf(v.versification, "${v.bibleBook}", "${v.chapterNo}", "${v.verseNo}"))
                assertEquals(emptyList(), compareWithOracle(fx, LegacyCategory.WORKSPACES, staging))
            }
        }
    }

    @Test
    fun repairKeepsCanonicalTupleAndResetsEveryOtherCase() {
        fun pm(vararg v: Pair<String, LegacyValue?>) = LegacyRow(1, mapOf("bibleDocument" to LegacyValue.TextV("ESV")) + v, emptyMap())
        val canonical = pm(
            "bibleBookEncoding" to LegacyValue.TextV("jswordOrdinal"), "bibleVersification" to LegacyValue.TextV("KJVA"),
            "bibleBibleBook" to LegacyValue.IntV(42), "bibleChapterNo" to LegacyValue.IntV(4), "bibleVerseNo" to LegacyValue.IntV(5),
        )
        assertEquals(canonical.values, repairBibleBookEncoding(canonical))

        val genesis = mapOf(
            "bibleBookEncoding" to LegacyValue.TextV("jswordOrdinal"), "bibleVersification" to LegacyValue.TextV("KJV"),
            "bibleBibleBook" to LegacyValue.IntV(2), "bibleChapterNo" to LegacyValue.IntV(1), "bibleVerseNo" to LegacyValue.IntV(1),
            "bibleDocument" to LegacyValue.TextV("ESV"),
        )
        val unmarked = canonical.values - "bibleBookEncoding"
        val unknown = canonical.values + ("bibleBookEncoding" to LegacyValue.TextV("futureEncoding"))
        val incomplete = canonical.values + ("bibleVerseNo" to null)
        val nothing = pm().values
        for (case in listOf(unmarked, unknown, incomplete, nothing)) {
            val repaired = repairBibleBookEncoding(LegacyRow(1, case, emptyMap()))
            assertEquals(genesis + ("bibleDocument" to case["bibleDocument"]), repaired)
        }
    }

    // ---- Fidelity merge, global settings, history aliases, defaults: not present in any fixture, so built by hand ----

    private val ws1 = "13000000-0000-0000-0000-0000000000a1"
    private val win1 = "13000000-0000-0000-0000-0000000000a2"
    private val win2 = "13000000-0000-0000-0000-0000000000a3"
    private val label1 = "13000000-0000-0000-0000-000000000101"
    private val label2 = "13000000-0000-0000-0000-000000000102"

    private fun uuid(s: String) = LegacyValue.UuidV(s)

    private fun handBuilt(settings: Map<String, String>, history: List<LegacyRow> = emptyList()) = LegacyWorkspaceData(
        workspaces = listOf(
            LegacyRow(
                10,
                mapOf(
                    "id" to uuid(ws1), "name" to LegacyValue.TextV("W"), "orderNumber" to LegacyValue.IntV(0),
                    "workspaceSettings.autoAssignLabels" to LegacyValue.ArchiveV(setOf(LegacyUuid(label2), LegacyUuid(label1))),
                    "workspaceSettings.autoAssignPrimaryLabel" to uuid("13000000-0000-0000-0000-0000000009ff"),
                    "workspaceSettings.recentLabels" to LegacyValue.JsonV("""[{"lastAccess":721692801.5,"labelId":"$label1"}]"""),
                    "workspaceSettings.studyPadCursors" to LegacyValue.JsonV("""["$label2",3,"$label1",8]"""),
                    "workspaceSettings.hideCompareDocuments" to LegacyValue.ArchiveV(setOf("NET", "ESV")),
                    "textDisplaySettings.fontSize" to LegacyValue.IntV(20),
                ),
                emptyMap(),
            ),
        ),
        windows = listOf(
            LegacyRow(20, mapOf("id" to uuid(win1), "layoutState" to LegacyValue.TextV("split")), mapOf("workspace" to 10L, "pageManager" to 30L)),
            LegacyRow(21, mapOf("id" to uuid(win2), "layoutState" to LegacyValue.TextV("split")), mapOf("workspace" to 10L)),
        ),
        pageManagers = listOf(
            LegacyRow(
                30,
                mapOf(
                    "currentCategoryName" to LegacyValue.TextV("map"), "bibleDocument" to LegacyValue.TextV("KJV"),
                    "textDisplaySettings.fontSize" to LegacyValue.IntV(18),
                ),
                mapOf("window" to 20L),
            ),
            LegacyRow(31, emptyMap(), mapOf("window" to 99L)),
        ),
        historyItems = history,
        settings = settings,
    )

    private fun build(data: LegacyWorkspaceData, report: MigrationReport = MigrationReport()) = WorkspacesMapper().build(data, report)

    @Test
    fun fidelitySettingsAreMergedIntoTheRows() {
        val base = "remote_sync.workspaces.fidelity"
        val stage = build(
            handBuilt(
                mapOf(
                    "$base.page_manager.$win1" to """{"rawCurrentCategoryName":"MYNOTE","commentarySourceBookAndKey":"x","dictionaryAnchorOrdinal":4,"generalBookAnchorOrdinal":5,"mapAnchorOrdinal":6}""",
                    "$base.workspace_text_display.$ws1" to """{"autoTrackReading":true,"dayBackgroundImage":"img","nightBackgroundImageOpacity":55}""",
                    "$base.page_manager_text_display.$win1" to """{"scrollHelperLineStyle":2,"showPageButtons":false}""",
                    "$base.label_override.$ws1.$label1" to """{"workspaceID":"${ws1.uppercase()}","labelID":"${label1.uppercase()}","overrideMode":3}""",
                    "$base.global_text_display" to """{"id":"13000000-0000-0000-0000-0000000000b7","fidelity":{"showReadingProgress":true}}""",
                    "global_text_display_settings" to """{"fontSize":33,"marginLeft":2,"bookmarksHideLabels":["${label1.uppercase()}"],"enableVerseSelection":true}""",
                ),
            ),
        )
        val pm = stage.pageManagers.single { it.windowId.toString() == win1 }
        assertEquals("MYNOTE", pm.currentCategoryName)
        assertEquals("x", pm.commentaryPage!!.sourceBookAndKey)
        assertEquals(listOf(4, 5, 6), listOf(pm.dictionaryPage!!.anchorOrdinal, pm.generalBookPage!!.anchorOrdinal, pm.mapPage!!.anchorOrdinal))
        assertEquals(2, pm.textDisplaySettings!!.scrollHelperLineStyle)
        assertEquals(false, pm.textDisplaySettings!!.showPageButtons)
        assertEquals(18, pm.textDisplaySettings!!.fontSize)

        val w = stage.workspaces.single()
        assertEquals(true, w.textDisplaySettings!!.autoTrackReading)
        assertEquals("img", w.textDisplaySettings!!.colors!!.dayBackgroundImage)
        assertEquals(55, w.textDisplaySettings!!.colors!!.nightBackgroundImageOpacity)

        val override = stage.labelOverrides.single()
        assertEquals(listOf(ws1, label1, 3), listOf(override.workspaceId.toString(), override.labelId.toString(), override.overrideMode))

        val global = assertNotNull(stage.global)
        assertEquals("13000000-0000-0000-0000-0000000000b7", global.id.toString())
        assertEquals(33, global.textDisplaySettings.fontSize)
        assertEquals(2, global.textDisplaySettings.marginSize!!.marginLeft)
        assertEquals(true, global.textDisplaySettings.showReadingProgress)
        assertEquals(listOf(label1), global.textDisplaySettings.bookmarksHideLabels!!.map { it.toString() })
    }

    @Test
    fun workspaceSettingsAreNormalisedLikeTheSwiftExporter() {
        val w = build(handBuilt(emptyMap())).workspaces.single()
        val s = w.workspaceSettings!!
        assertEquals(listOf(label1, label2), s.autoAssignLabels.map { it.toString() })
        // The stored primary label is not an assigned label, so the first assigned one by id takes its place.
        assertEquals(label1, s.autoAssignPrimaryLabel.toString())
        assertEquals(listOf(label1 to 8, label2 to 3), s.studyPadCursors.map { it.key.toString() to it.value })
        assertEquals(listOf("ESV", "NET"), s.hideCompareDocuments.toList())
        // (721692801.5 + 978307200) seconds, truncated to whole milliseconds.
        assertEquals(1_700_000_001_500L, s.recentLabels.single().lastAccess)
        assertEquals(20, w.textDisplaySettings!!.fontSize)
        // NULL columns take the Swift defaults.
        assertTrue(s.autoPin)
        assertTrue(s.restoreButtonsVisible)
        assertEquals(100, s.speakSettings!!.playbackSettings.speed)
        assertEquals(10, s.speakSettings!!.lastSleepTimer)
    }

    @Test
    fun windowWithoutPageManagerGetsTheGenesisBibleRowAndOrphansAreSkipped() {
        val report = MigrationReport()
        val stage = build(handBuilt(emptyMap()), report)
        val bare = stage.pageManagers.single { it.windowId.toString() == win2 }
        assertEquals("BIBLE", bare.currentCategoryName)
        assertEquals(null, bare.biblePage.document)
        assertEquals(listOf("KJV", 2, 1, 1), bare.biblePage.verse.let { listOf(it.versification, it.bibleBook, it.chapterNo, it.verseNo) })
        assertEquals("MAPS", stage.pageManagers.single { it.windowId.toString() == win1 }.currentCategoryName)
        assertTrue(report.entries.contains(ReportEntry.Skipped(LegacyCategory.WORKSPACES, "page manager without an exported window", 1)))
    }

    @Test
    fun historyItemsAreNumberedInExporterOrderAfterTheHighestAlias() {
        fun item(pk: Long, window: Long, at: Long, key: String, id: String) = LegacyRow(
            pk,
            mapOf("createdAt" to LegacyValue.DateV(at), "document" to LegacyValue.TextV("KJV"), "key" to LegacyValue.TextV(key), "id" to uuid(id)),
            mapOf("window" to window),
        )
        val items = listOf(
            item(1, 21, 5000, "late-w2", "13000000-0000-0000-0000-000000000003"),
            item(2, 20, 9000, "late-w1", "13000000-0000-0000-0000-000000000002"),
            item(3, 20, 1000, "early-w1", "13000000-0000-0000-0000-000000000001"),
            item(4, 77, 1000, "orphan", "13000000-0000-0000-0000-000000000004"),
        )
        val report = MigrationReport()
        val aliases = mapOf("remote_sync.workspaces.fidelity.history_alias.41" to "13000000-0000-0000-0000-000000000002")
        val history = build(handBuilt(aliases, items), report).historyItems
        // Ordered by window id, then time; the aliased item keeps 41, the others continue from 42.
        assertEquals(listOf("early-w1" to 42L, "late-w1" to 41L, "late-w2" to 43L), history.map { it.key to it.id })
        assertTrue(report.entries.contains(ReportEntry.Skipped(LegacyCategory.WORKSPACES, "history item without an exported window", 1)))
    }

    @Test
    fun malformedFidelityAndJsonAreSkippedAndReportedNotFatal() {
        val base = "remote_sync.workspaces.fidelity"
        val report = MigrationReport()
        val stage = build(
            handBuilt(
                mapOf(
                    "$base.page_manager.$win1" to "not json",
                    "$base.page_manager.not-a-uuid" to "{}",
                    "$base.label_override.$ws1.$label1" to """{"workspaceID":"$ws1","labelID":"$label2","overrideMode":1}""",
                    "global_text_display_settings" to "[1,2",
                ),
            ),
            report,
        )
        assertEquals("MAPS", stage.pageManagers.single { it.windowId.toString() == win1 }.currentCategoryName)
        assertEquals(emptyList(), stage.labelOverrides)
        assertNull(stage.global)
        val skipped = report.entries.filterIsInstance<ReportEntry.Skipped>().associate { it.reason to it.rows }
        assertEquals(3, skipped["malformed workspace fidelity settings"])
        assertEquals(1, skipped["malformed global_text_display_settings JSON"])
    }

    @Test
    fun handBuiltStageRoundTripsThroughTheDaos() { runBlocking {
        val report = MigrationReport()
        val data = handBuilt(
            mapOf(
                "remote_sync.workspaces.fidelity.label_override.$ws1.$label1" to """{"workspaceID":"$ws1","labelID":"$label1","overrideMode":2}""",
                "global_text_display_settings" to """{"fontSize":33}""",
            ),
        )
        withStaging { staging, _ ->
            WorkspacesMapper().map(data, staging, report)
            val dao = staging.workspaces.workspaceDao()
            assertEquals(listOf(ws1), dao.allWorkspaces().map { it.id.toString() })
            assertEquals(2, dao.allWindows().size)
            assertEquals(listOf(2), dao.labelOverrides(IdType.fromString(ws1)).map { it.overrideMode })
            assertEquals(33, staging.workspaces.globalTextDisplaySettingsDao().get()!!.textDisplaySettings.fontSize)
        }
        val written = report.entries.filterIsInstance<ReportEntry.Written>().associate { it.table to it.rows }
        assertEquals(mapOf("Workspace" to 1, "Window" to 2, "PageManager" to 2, "HistoryItem" to 0, "WorkspaceLabelOverride" to 1, "GlobalTextDisplaySettings" to 1), written)
    } }

    @Test
    fun extraStagedAndMissingOracleRowsCanBeApprovedByPrimaryKey() {
        val fx = fixture("1.0.2", "18", "rich")
        runBlocking {
            withStaging { staging, _ ->
                snapshot(fx).use { WorkspacesMapper().map(it, staging, MigrationReport()) }
                val dao = staging.workspaces.workspaceDao()
                dao.insertWorkspace(WorkspaceEntities.Workspace(name = "extra"))
                val windowId = dao.allWindows().first().id
                dao.deleteHistoryItems(windowId)

                val raw = compareWithOracle(fx, LegacyCategory.WORKSPACES, staging)
                val keyOf = { line: String -> line.substringAfter("[").substringBefore("]: ") }
                val extra = raw.single { it.endsWith("only in staged database") }
                val missing = raw.filter { it.endsWith("missing in staged database") }
                assertTrue(missing.isNotEmpty())
                // Only an approval on the right side, table and key silences a row.
                val wrongSide = setOf(ApprovedRow("Workspace", keyOf(extra), RowSide.ONLY_IN_ORACLE, "x"))
                assertEquals(raw, compareWithOracle(fx, LegacyCategory.WORKSPACES, staging, emptySet(), wrongSide))
                val rows = setOf(ApprovedRow("Workspace", keyOf(extra), RowSide.ONLY_IN_STAGED, "extra row")) +
                    missing.map { ApprovedRow("HistoryItem", keyOf(it), RowSide.ONLY_IN_ORACLE, "history") }
                assertEquals(emptyList(), compareWithOracle(fx, LegacyCategory.WORKSPACES, staging, emptySet(), rows))
            }
        }
    }

    @Test
    fun cellComparisonIsExactExceptForKnownJsonAndApprovedNullVersusEmpty() {
        // JSON columns ignore object key order; array order and every other text column are exact.
        assertTrue(sameCell("t:{\"a\":1,\"b\":2}", "t:{\"b\":2,\"a\":1}", isJson = true, nullEqualsEmpty = false))
        assertTrue(!sameCell("t:{\"a\":1,\"b\":2}", "t:{\"b\":2,\"a\":1}", isJson = false, nullEqualsEmpty = false))
        assertTrue(!sameCell("t:[\"a\",\"b\"]", "t:[\"b\",\"a\"]", isJson = true, nullEqualsEmpty = false))
        assertTrue(!sameCell("t:a\u0000b", "t:a", isJson = false, nullEqualsEmpty = false))
        // NULL equals an empty collection only when approved, and nothing else equals NULL.
        assertTrue(!sameCell("null", "t:[]", isJson = true, nullEqualsEmpty = false))
        assertTrue(sameCell("null", "t:[]", isJson = true, nullEqualsEmpty = true))
        assertTrue(sameCell("t:{}", "null", isJson = true, nullEqualsEmpty = true))
        assertTrue(!sameCell("null", "t:[1]", isJson = true, nullEqualsEmpty = true))
        assertTrue(!sameCell("null", "t:", isJson = true, nullEqualsEmpty = true))
    }

    @Test
    fun unexpectedValueTypesAreReportedNotSilentlyDropped() {
        val data = handBuilt(emptyMap())
        val bad = LegacyWorkspaceData(
            workspaces = data.workspaces.map {
                it.copy(values = it.values + mapOf(
                    "textDisplaySettings.fontSize" to LegacyValue.TextV("big"),
                    "workspaceSettings.autoAssignLabels" to LegacyValue.ArchiveV(setOf(LegacyUuid(label1), 42L)),
                    "workspaceSettings.hideCompareDocuments" to LegacyValue.ArchiveV("not a collection"),
                ))
            },
            windows = data.windows, pageManagers = data.pageManagers, historyItems = data.historyItems, settings = data.settings,
        )
        val report = MigrationReport()
        val stage = build(bad, report)
        assertNull(stage.workspaces.single().textDisplaySettings!!.fontSize)
        val skipped = report.entries.filterIsInstance<ReportEntry.Skipped>().associate { it.reason to it.rows }
        assertEquals(1, skipped["unexpected value type TextV for textDisplaySettings.fontSize"])
        assertEquals(1, skipped["unexpected archive element in workspaceSettings.autoAssignLabels"])
        assertEquals(1, skipped["unexpected archive content for workspaceSettings.hideCompareDocuments"])
        val clean = MigrationReport()
        build(data, clean)
        assertTrue(clean.entries.filterIsInstance<ReportEntry.Skipped>().none { it.reason.startsWith("unexpected") })
    }

    // ---- Staging databases and the report ----

    @Test
    fun closedStagingLeavesOnlyTheMainFiles() { runBlocking {
        val dir = tempDir()
        val staging = StagingDatabases(dir.toOkioPath(), JvmLegacyDatabaseFactory())
        WorkspacesMapper().map(handBuilt(emptyMap()), staging, MigrationReport())
        staging.settings.booleanSettingDao()
        staging.close()
        assertEquals(listOf("workspaces.sqlite3"), staging.files.map { it.name })
        assertEquals(listOf("workspaces.sqlite3"), dir.list()!!.sorted())
        dir.deleteRecursively()
    } }

    @Test
    fun reportSerialisesEveryEntryKind() {
        val report = MigrationReport()
        report.written(LegacyCategory.WORKSPACES, "Workspace", 2)
        report.skipped(LegacyCategory.BOOKMARKS, "no \"module\"", 1)
        report.unknownKey("some.key")
        assertEquals(3, report.entries.size)
        assertEquals(
            """{"entries":[{"type":"written","category":"WORKSPACES","table":"Workspace","rows":2},""" +
                """{"type":"skipped","category":"BOOKMARKS","reason":"no \"module\"","rows":1},""" +
                """{"type":"unknownKey","key":"some.key"}]}""",
            report.toJson(),
        )
    }
}
