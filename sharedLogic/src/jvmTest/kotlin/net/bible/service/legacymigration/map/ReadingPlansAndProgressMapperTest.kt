package net.bible.service.legacymigration.map

import androidx.sqlite.execSQL
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.bible.android.common.isConvertibleTo
import net.bible.android.common.toV11n
import net.bible.android.database.IdType
import net.bible.android.database.progress.ReadingSource
import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyStoreReader
import net.bible.service.legacymigration.read.LegacyValue
import net.bible.service.legacymigration.stage.StagingDatabases
import net.bible.service.legacymigration.testsupport.FixtureDir
import net.bible.service.legacymigration.testsupport.JvmLegacyDatabaseFactory
import net.bible.service.legacymigration.testsupport.allFixtures
import net.bible.service.legacymigration.testsupport.compareWithOracleDetailed
import net.bible.service.legacymigration.testsupport.fixture
import net.bible.service.legacymigration.testsupport.openStore
import okio.Path.Companion.toOkioPath

class ReadingPlansAndProgressMapperTest {
    private fun snapshot(fx: FixtureDir) = LegacySnapshot(
        LegacyStoreReader(File(fx.root.toFile(), "AndBible.store").path),
        LegacyStoreReader(File(fx.root.toFile(), "LocalStore.store").path),
    )

    private inline fun <T> withStaging(block: (StagingDatabases) -> T): T {
        val dir = Files.createTempDirectory("staging-").toFile()
        val staging = StagingDatabases(dir.toOkioPath(), JvmLegacyDatabaseFactory())
        try {
            return block(staging)
        } finally {
            staging.close()
            dir.deleteRecursively()
        }
    }

    private fun label(fx: FixtureDir) = "${fx.app}/${fx.ios}/${fx.scenario}"

    private fun mapPlansAndCompare(fx: FixtureDir) = runBlocking {
        withStaging { staging ->
            val report = MigrationReport()
            snapshot(fx).use { ReadingPlansMapper().map(it, staging, report) }
            compareWithOracleDetailed(fx, LegacyCategory.READING_PLANS, staging) to report
        }
    }

    private fun mapProgressAndCompare(fx: FixtureDir) = runBlocking {
        withStaging { staging ->
            val report = MigrationReport()
            snapshot(fx).use { ProgressMapper().map(it, staging, report) }
            compareWithOracleDetailed(fx, LegacyCategory.PROGRESS, staging) to report
        }
    }

    private fun skipped(report: MigrationReport) = report.entries.filterIsInstance<ReportEntry.Skipped>()

    // ---- Oracle comparison -----------------------------------------------------------------------------------

    @Test
    fun everyFixtureMatchesItsOracleForReadingPlans() {
        val fixtures = allFixtures()
        assertEquals(24, fixtures.size)
        for (fx in fixtures) {
            val (result, report) = mapPlansAndCompare(fx)
            assertEquals(emptyList(), result.differences, label(fx))
            assertEquals(emptyList(), skipped(report), label(fx))
        }
    }

    @Test
    fun everyFixtureMatchesItsOracleForProgress() {
        for (fx in allFixtures()) {
            val (result, report) = mapProgressAndCompare(fx)
            assertEquals(emptyList(), result.differences, label(fx))
            assertEquals(emptyList(), skipped(report), label(fx))
        }
    }

    @Test
    fun comparisonIsNotVacuousForRichFixtures() {
        // `rich` and `preserved-ai` hold a started plan with a completed day and progress rows; the others hold none
        // (their oracle still has the default GlobalReadingProgressSettings row, which must compare equal too).
        for (fx in allFixtures()) {
            val rich = fx.scenario == "rich" || fx.scenario == "preserved-ai"
            val plans = mapPlansAndCompare(fx).first.comparedRows
            val progress = mapProgressAndCompare(fx).first.comparedRows
            if (rich) {
                assertEquals(1, plans["ReadingPlan"], label(fx))
                assertEquals(1, plans["ReadingPlanStatus"], label(fx))
                for (table in listOf("MemorizedVerse", "ChapterReadHistory", "MemorizationTarget")) {
                    assertTrue((progress[table] ?: 0) > 0, "${label(fx)}: no $table rows compared ($progress)")
                }
            } else {
                assertEquals(0, plans["ReadingPlan"], label(fx))
            }
            assertEquals(1, progress["GlobalReadingProgressSettings"], label(fx))
        }
    }

    @Test
    fun plantedDefectIsReported() {
        val fx = fixture("1.0.2", "18", "rich")
        val differences = runBlocking {
            withStaging { staging ->
                snapshot(fx).use { snap ->
                    val data = LegacyReadingPlanData.from(snap)
                    val defect = LegacyReadingPlanData(
                        data.plans.map { it.copy(values = it.values + ("currentDay" to LegacyValue.IntV(9))) },
                        data.days, data.settings,
                    )
                    ReadingPlansMapper().map(defect, staging, MigrationReport())
                }
                compareWithOracleDetailed(fx, LegacyCategory.READING_PLANS, staging).differences
            }
        }
        assertTrue(differences.isNotEmpty(), "planted defect went unnoticed")
        // The plan row differs; a larger current day also turns the completed day into an implicit historic one (no status row).
        assertTrue(differences.any { it.contains("ReadingPlan[") && it.contains("planCurrentDay") }, differences.toString())
        assertTrue(differences.all { it.contains("ReadingPlan[") || it.contains("ReadingPlanStatus[") }, differences.toString())
    }

    // ---- Malformed settings ------------------------------------------------------------------------------------

    private fun updateLocalSetting(fx: FixtureDir, key: String, value: String) {
        val db = openStore(File(fx.root.toFile(), "LocalStore.store"))
        try {
            db.execSQL("UPDATE ZSETTING SET ZVALUE = '${value.replace("'", "''")}' WHERE ZKEY = '$key'")
        } finally {
            db.close()
        }
    }

    private fun <T> rows(staging: StagingDatabases, block: suspend (net.bible.android.database.progress.ProgressDao) -> T): T =
        runBlocking { block(staging.progress.progressDao()) }

    @Test
    fun malformedReadingProgressJsonIsReportedAndTheOtherProgressRowsAreStillWritten() {
        val fx = fixture("1.0.2", "18", "rich")
        updateLocalSetting(fx, "reading_progress_state_v1", "{\"history\": [ not json")
        withStaging { staging ->
            val report = MigrationReport()
            runBlocking { snapshot(fx).use { ProgressMapper().map(it, staging, report) } }
            assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.PROGRESS, "unreadable reading_progress_state_v1", 1)), skipped(report))
            rows(staging) { dao ->
                assertEquals(1, dao.allMemorizedVerses().size)
                assertEquals(1, dao.allMemorizationTargets().size)
                assertEquals(0, dao.getAllReadingTimestampsForCycle(1).size)
            }
            // The unreadable payload leaves the reading settings at their defaults, like an absent one.
            val settings = runBlocking { staging.progress.globalReadingProgressSettingsDao().get() }
            assertEquals(net.bible.android.database.progress.GlobalReadingProgressSettings(), settings)
        }
    }

    @Test
    fun malformedMemorizationJsonIsReportedAndTheReadingRowsAreStillWritten() {
        val fx = fixture("1.0.2", "18", "rich")
        updateLocalSetting(fx, "memorization_progress_state_v1", "[1, 2")
        withStaging { staging ->
            val report = MigrationReport()
            runBlocking { snapshot(fx).use { ProgressMapper().map(it, staging, report) } }
            assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.PROGRESS, "unreadable memorization_progress_state_v1", 1)), skipped(report))
            rows(staging) { dao ->
                assertEquals(0, dao.allMemorizedVerses().size)
                assertEquals(0, dao.allMemorizationTargets().size)
                assertEquals(1, dao.getHistoryForBook(2, 1).size)
            }
        }
    }

    // ---- Progress: distinct hand-built values ------------------------------------------------------------------

    private val historyId = "11111111-2222-3333-4444-555555555555"
    private val verseId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val targetId = "99999999-8888-7777-6666-555555555555"

    private val trust = """{"state":"verifiedMappingV1","mappingVersion":1,"provenance":"nativeMapping","sourceBookInitials":"KJVA","sourceVersification":"KJVA","sourceOrdinalStart":%d,"sourceOrdinalEnd":%d}"""

    private fun runProgress(settings: Map<String, String>, block: suspend (StagingDatabases, MigrationReport) -> Unit) = withStaging { staging ->
        val report = MigrationReport()
        runBlocking {
            ProgressMapper().map(LegacyProgressData(settings), staging, report)
            block(staging, report)
        }
    }

    @Test
    fun everyReadingProgressFieldIsMappedWithDistinctValues() {
        val reading = """{"history":[{"id":"$historyId","kjvBookOrdinal":45,"chapter":12,"cycle":3,"readAt":1700000123456,"bookInitials":"NIV","source":"AUTO_TTS"},
            {"id":"22222222-2222-2222-2222-222222222222","kjvBookOrdinal":2,"chapter":50,"cycle":1,"readAt":5,"bookInitials":"","source":"AUTO_SCROLL"}],
            "settings":{"autoTrackReading":true,"activeCycle":7,"autoMarkMemorized":false,"memorizeTypeFullWords":true,"memorizeWordVisibility":"dim",
            "memorizeErrorHeatmap":false,"memorizeScrambleHideUsed":true,"memorizeIncludeReference":false}}"""
        runProgress(mapOf("reading_progress_state_v1" to reading)) { staging, report ->
            val dao = staging.progress.progressDao()
            val first = dao.getChapterReadHistory(45, 12, 3).single()
            assertEquals(IdType.fromString(historyId), first.id)
            assertEquals(listOf(1700000123456L, "NIV", ReadingSource.AUTO_TTS), listOf(first.readAt, first.bookInitials, first.source))
            val second = dao.getChapterReadHistory(2, 50, 1).single()
            assertEquals(listOf(5L, "", ReadingSource.AUTO_SCROLL), listOf(second.readAt, second.bookInitials, second.source))
            val s = staging.progress.globalReadingProgressSettingsDao().get()!!
            assertEquals(
                listOf(true, false, true, "dim", false, true, false, 7),
                listOf(s.autoTrackReading, s.autoMarkMemorized, s.memorizeTypeFullWords, s.memorizeWordVisibility, s.memorizeErrorHeatmap, s.memorizeScrambleHideUsed, s.memorizeIncludeReference, s.activeCycle),
            )
            assertEquals(emptyList(), skipped(report))
        }
    }

    @Test
    fun memorizationRowsAreMappedAndOnlyTrustedOnesAreWritten() {
        val memorization = """{"memorizedVerses":[
            {"id":"$verseId","bookInitials":"","kjvOrdinal":1234,"memorizedAt":777,"ordinalTrust":${trust.format(1234, 1234)}},
            {"id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","bookInitials":"KJV","kjvOrdinal":4321,"memorizedAt":1},
            {"id":"cccccccc-cccc-cccc-cccc-cccccccccccc","bookInitials":"","kjvOrdinal":99999999,"memorizedAt":1},
            {"id":"dddddddd-dddd-dddd-dddd-dddddddddddd","kjvOrdinal":55,"memorizedAt":9}],
            "targetRows":[{"id":"$targetId","bookInitials":"","startOrdinal":100,"endOrdinal":250,"createdAt":888,"ordinalTrust":${trust.format(100, 250)}},
            {"id":"eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee","bookInitials":"","startOrdinal":300,"endOrdinal":200,"createdAt":1}]}"""
        runProgress(mapOf("memorization_progress_state_v1" to memorization)) { staging, report ->
            val dao = staging.progress.progressDao()
            // 1234 (explicit trust) and 55 (no trust object, empty initials: a legacy global KJVA row, trusted) are written;
            // the module-scoped row and the out-of-domain ordinal are not.
            val verses = dao.allMemorizedVerses().associate { it.kjvOrdinal to it }
            assertEquals(setOf(55, 1234), verses.keys)
            assertEquals(listOf(IdType.fromString(verseId), 777L), listOf(verses.getValue(1234).id, verses.getValue(1234).memorizedAt))
            assertEquals(9L, verses.getValue(55).memorizedAt)
            val target = dao.allMemorizationTargets().single()
            assertEquals(listOf(IdType.fromString(targetId), 100, 250, 888L), listOf(target.id, target.kjvOrdinalStart, target.kjvOrdinalEnd, target.createdAt))
            assertEquals(
                listOf(
                    ReportEntry.Skipped(LegacyCategory.PROGRESS, "memorized verse without trusted ordinals", 2),
                    ReportEntry.Skipped(LegacyCategory.PROGRESS, "memorization target without trusted ordinals", 1),
                ),
                skipped(report),
            )
        }
    }

    @Test
    fun memorizedVersesOfOneOrdinalKeepTheNewest() {
        val memorization = """{"memorizedVerses":[
            {"id":"$verseId","kjvOrdinal":10,"memorizedAt":5},{"id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","kjvOrdinal":10,"memorizedAt":50}]}"""
        runProgress(mapOf("memorization_progress_state_v1" to memorization)) { staging, _ ->
            val verse = staging.progress.progressDao().allMemorizedVerses().single()
            assertEquals(10 to 50L, verse.kjvOrdinal to verse.memorizedAt)
        }
    }

    @Test
    fun legacyRangeFormatIsExpandedIntoRows() {
        val memorization = """{"memorizedRanges":[{"bookInitials":"","startOrdinal":20,"endOrdinal":22}],
            "targetRanges":[{"bookInitials":"","startOrdinal":30,"endOrdinal":33}]}"""
        runProgress(mapOf("memorization_progress_state_v1" to memorization)) { staging, _ ->
            val dao = staging.progress.progressDao()
            assertEquals(listOf(20, 21, 22), dao.allMemorizedVerses().map { it.kjvOrdinal }.sorted())
            val target = dao.allMemorizationTargets().single()
            assertEquals(30 to 33, target.kjvOrdinalStart to target.kjvOrdinalEnd)
        }
    }

    @Test
    fun invalidHistoryRowsAndSettingsAreReportedNotSilentlyDropped() {
        val reading = """{"history":[
            {"id":"$historyId","kjvBookOrdinal":45,"chapter":12,"cycle":3,"readAt":9,"source":"MANUAL"},
            {"id":"22222222-2222-2222-2222-222222222222","kjvBookOrdinal":0,"chapter":1,"cycle":1,"readAt":9,"source":"MANUAL"},
            {"id":"33333333-3333-3333-3333-333333333333","kjvBookOrdinal":2,"chapter":0,"cycle":1,"readAt":9,"source":"MANUAL"},
            {"id":"44444444-4444-4444-4444-444444444444","kjvBookOrdinal":2,"chapter":1,"cycle":0,"readAt":9,"source":"MANUAL"},
            {"id":"55555555-5555-5555-5555-555555555555","kjvBookOrdinal":2,"chapter":1,"cycle":5000000000,"readAt":9,"source":"MANUAL"},
            {"id":"$historyId","kjvBookOrdinal":46,"chapter":1,"cycle":1,"readAt":9,"source":"MANUAL"}],
            "settings":{"autoTrackReading":false,"activeCycle":-4,"autoMarkMemorized":true,"memorizeTypeFullWords":false,"memorizeWordVisibility":"loud",
            "memorizeErrorHeatmap":true,"memorizeScrambleHideUsed":false,"memorizeIncludeReference":true}}"""
        runProgress(mapOf("reading_progress_state_v1" to reading)) { staging, report ->
            val dao = staging.progress.progressDao()
            assertEquals(listOf(IdType.fromString(historyId)), dao.getChapterReadHistory(45, 12, 3).map { it.id })
            assertEquals(0, dao.getChapterReadHistory(46, 1, 1).size)
            val s = staging.progress.globalReadingProgressSettingsDao().get()!!
            assertEquals(0 to "light", s.activeCycle to s.memorizeWordVisibility)
            assertEquals(
                listOf(
                    ReportEntry.Skipped(LegacyCategory.PROGRESS, "invalid reading history row", 3),
                    ReportEntry.Skipped(LegacyCategory.PROGRESS, "reading history value out of range", 1),
                    ReportEntry.Skipped(LegacyCategory.PROGRESS, "duplicate reading history id", 1),
                    ReportEntry.Skipped(LegacyCategory.PROGRESS, "invalid reading progress settings value", 2),
                ),
                skipped(report),
            )
        }
    }

    @Test
    fun scriptureBookDomainIsExactlyTheEightyKjvaBooks() {
        val valid = (-1..200).filter { ProgressMapper.isKjvaBookOrdinal(it) }
        assertEquals(80, valid.size, valid.toString())
        assertTrue(ProgressMapper.isKjvaBookOrdinal(2), "Genesis")
        assertTrue(!ProgressMapper.isKjvaBookOrdinal(0) && !ProgressMapper.isKjvaBookOrdinal(1), "introductions")
    }

    @Test
    fun absentSettingsStillProduceTheDefaultSettingsRow() {
        runProgress(emptyMap()) { staging, report ->
            assertEquals(net.bible.android.database.progress.GlobalReadingProgressSettings(), staging.progress.globalReadingProgressSettingsDao().get())
            assertEquals(emptyList(), skipped(report))
        }
    }

    // ---- Reading plans: distinct hand-built values -------------------------------------------------------------

    private val planId = "01234567-89ab-cdef-0123-456789abcdef"

    private fun planRow(pk: Long, id: String?, code: String, currentDay: Long, start: Long = 1_000L) = LegacyRow(
        pk,
        buildMap<String, LegacyValue?> {
            if (id != null) put("id", LegacyValue.UuidV(id))
            put("planCode", LegacyValue.TextV(code)); put("currentDay", LegacyValue.IntV(currentDay)); put("startDate", LegacyValue.DateV(start))
        },
        emptyMap(),
    )

    private fun dayRow(pk: Long, plan: Long, day: Long, completed: Boolean, readings: String) = LegacyRow(
        pk,
        mapOf("dayNumber" to LegacyValue.IntV(day), "isCompleted" to LegacyValue.BoolV(completed), "readings" to LegacyValue.TextV(readings)),
        mapOf("plan" to plan),
    )

    private fun statusKey(code: String, day: Int) =
        "remote_sync.readingplans.android_status.${Base64.getUrlEncoder().withoutPadding().encodeToString(code.toByteArray())}.$day"

    private fun runPlans(data: LegacyReadingPlanData, block: suspend (StagingDatabases, MigrationReport) -> Unit) = withStaging { staging ->
        val report = MigrationReport()
        runBlocking {
            ReadingPlansMapper().map(data, staging, report)
            block(staging, report)
        }
    }

    private fun syntheticId(plan: String, day: Int): IdType {
        val digest = MessageDigest.getInstance("SHA-256").digest("reading-plan-status|$plan|$day".toByteArray())
        return IdType.fromByteArray(digest.copyOf(16))!!
    }

    @Test
    fun planAndStatusFieldsAreMappedWithDistinctValues() {
        val remote = "fedcba98-7654-3210-fedc-ba9876543210"
        val envelope = """{"planCode":"p1","dayNumber":6,"readingStatusJSON":"{\"chapterReadArray\":[{\"isRead\":false,\"readingNumber\":1},{\"isRead\":true,\"readingNumber\":2}]}","remoteStatusID":"${remote.uppercase()}"}"""
        val bare = """{"chapterReadArray":[{"isRead":true,"readingNumber":4}]}"""
        val data = LegacyReadingPlanData(
            plans = listOf(planRow(1, planId, "p1", currentDay = 5, start = 1_234_567_890_123L)),
            days = listOf(
                dayRow(10, 1, 3, completed = true, readings = "Gen 1,Gen 2"), // historic ordinal day: implicit, no row
                dayRow(11, 1, 5, completed = true, readings = "Exod 1, Exod 2 ,,Exod 4,"), // 4 readings (the interior blank keeps its slot)
                dayRow(12, 1, 6, completed = false, readings = "Lev 1,Lev 2"), // preserved partial status
                dayRow(13, 1, 7, completed = false, readings = "Num 1"), // preserved bare payload, not completed
                dayRow(14, 1, 8, completed = false, readings = "Deut 1"), // nothing
            ),
            settings = mapOf(statusKey("p1", 6) to envelope, statusKey("p1", 7) to bare),
        )
        runPlans(data) { staging, report ->
            val dao = staging.readingPlans.readingPlanDao()
            val plan = dao.getPlan("p1")!!
            assertEquals(listOf(IdType.fromString(planId), 5, 1_234_567_890_123L), listOf(plan.id, plan.planCurrentDay, plan.planStartDate.getTime()))
            assertEquals(null, dao.getStatus("p1", 3))
            assertEquals(null, dao.getStatus("p1", 8))
            val synthesized = dao.getStatus("p1", 5)!!
            assertEquals(syntheticId(planId, 5), synthesized.id)
            assertEquals("""{"chapterReadArray":[{"isRead":true,"readingNumber":1},{"isRead":true,"readingNumber":2},{"isRead":true,"readingNumber":3},{"isRead":true,"readingNumber":4}]}""", synthesized.readingStatus)
            val preserved = dao.getStatus("p1", 6)!!
            assertEquals(IdType.fromString(remote), preserved.id)
            assertEquals("""{"chapterReadArray":[{"isRead":false,"readingNumber":1},{"isRead":true,"readingNumber":2}]}""", preserved.readingStatus)
            val bareRow = dao.getStatus("p1", 7)!!
            assertEquals(listOf(syntheticId(planId, 7), bare), listOf(bareRow.id, bareRow.readingStatus))
            assertEquals(emptyList(), skipped(report))
        }
    }

    @Test
    fun dateBasedPlanKeepsStatusRowsOfDaysBeforeTheCurrentDay() {
        val data = LegacyReadingPlanData(
            plans = listOf(planRow(1, planId, "dated", currentDay = 9)),
            days = listOf(dayRow(10, 1, 2, completed = true, readings = "Jan-2;Gen 1,Gen 2")),
            settings = emptyMap(),
        )
        runPlans(data) { staging, _ ->
            val status = staging.readingPlans.readingPlanDao().getStatus("dated", 2)!!
            assertEquals(2, Regex("readingNumber").findAll(status.readingStatus).count())
        }
    }

    @Test
    fun exactStartDateSettingWinsOverTheLegacyDate() {
        val data = LegacyReadingPlanData(
            plans = listOf(planRow(1, planId, "p", currentDay = 1, start = 5)),
            days = emptyList(),
            settings = mapOf("remote_sync.readingplans.start_date_millis.$planId" to "1600000000123"),
        )
        runPlans(data) { staging, report ->
            assertEquals(1_600_000_000_123L, staging.readingPlans.readingPlanDao().getPlan("p")!!.planStartDate.getTime())
            assertEquals(emptyList(), skipped(report))
        }
    }

    @Test
    fun unusableRowsAndSettingsAreReported() {
        val data = LegacyReadingPlanData(
            plans = listOf(
                planRow(1, planId, "dup", currentDay = 1),
                planRow(2, "ffffffff-0000-0000-0000-000000000001", "dup", currentDay = 2),
                planRow(3, null, "noid", currentDay = 1),
                planRow(4, "ffffffff-0000-0000-0000-000000000002", "huge", currentDay = 5_000_000_000L),
            ),
            days = listOf(dayRow(10, 1, 1, completed = false, readings = "")),
            settings = mapOf(
                statusKey("dup", 1) to "not json",
                "remote_sync.readingplans.android_status.@@@.1" to "{}",
                "remote_sync.readingplans.start_date_millis.$planId" to "12x",
            ),
        )
        runPlans(data) { staging, report ->
            val dao = staging.readingPlans.readingPlanDao()
            assertEquals(1, dao.getPlan("dup")!!.planCurrentDay)
            assertEquals(null, dao.getPlan("noid"))
            assertEquals(null, dao.getPlan("huge"))
            assertEquals(
                setOf(
                    ReportEntry.Skipped(LegacyCategory.READING_PLANS, "reading plan without id", 1),
                    ReportEntry.Skipped(LegacyCategory.READING_PLANS, "reading plan current day out of range", 1),
                    ReportEntry.Skipped(LegacyCategory.READING_PLANS, "duplicate reading plan code", 1),
                    ReportEntry.Skipped(LegacyCategory.READING_PLANS, "malformed reading plan status setting", 2),
                    ReportEntry.Skipped(LegacyCategory.READING_PLANS, "malformed reading plan start date setting", 1),
                ),
                skipped(report).toSet(),
            )
        }
    }

    @Test
    fun statusEnvelopeForAnotherPlanOrDayIsRejected() {
        val mismatched = """{"planCode":"other","dayNumber":1,"readingStatusJSON":"{\"chapterReadArray\":[]}"}"""
        val data = LegacyReadingPlanData(
            plans = listOf(planRow(1, planId, "p", currentDay = 1)),
            days = listOf(dayRow(10, 1, 1, completed = false, readings = "A")),
            settings = mapOf(statusKey("p", 1) to mismatched),
        )
        runPlans(data) { staging, report ->
            assertEquals(null, staging.readingPlans.readingPlanDao().getStatus("p", 1))
            assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.READING_PLANS, "malformed reading plan status setting", 1)), skipped(report))
        }
    }

    @Test
    fun nothingToWriteCreatesNoReadingPlanDatabase() {
        runPlans(LegacyReadingPlanData(emptyList(), emptyList(), emptyMap())) { staging, _ ->
            assertEquals(null, staging.openedDatabase("readingplans.sqlite3"))
            assertNotNull(staging)
        }
    }

    // ---- Review follow-ups -------------------------------------------------------------------------------------

    private fun kjvaOrdinal(v11n: String, ordinal: Int): Int? = runCatching {
        val verse = org.crosswire.jsword.passage.Verse(org.crosswire.jsword.versification.system.Versifications.instance().getVersification(v11n)!!, ordinal)
        if (verse.isConvertibleTo(net.bible.android.database.bookmarks.KJVA)) verse.toV11n(net.bible.android.database.bookmarks.KJVA).ordinal else null
    }.getOrNull()

    private fun differingVerse(): Triple<String, Int, Int> {
        for (name in listOf("Synodal", "SynodalProt", "Vulg", "Luther", "German", "Catholic", "Catholic2", "NRSV", "Leningrad", "MT", "Segond", "Calvin")) {
            if (org.crosswire.jsword.versification.system.Versifications.instance().getVersification(name) == null) continue
            for (ordinal in listOf(1000, 5000, 20000, 25000, 30000, 31000)) {
                val kjva = kjvaOrdinal(name, ordinal) ?: continue
                if (kjva != ordinal) return Triple(name, ordinal, kjva)
            }
        }
        error("no built-in versification with differing ordinals found")
    }

    private fun verseJson(id: String, kjva: Int, state: String, provenance: String, v11n: String, source: Int) =
        """{"id":"$id","bookInitials":"","kjvOrdinal":$kjva,"memorizedAt":1,"ordinalTrust":{"state":"$state","mappingVersion":1,"provenance":"$provenance","sourceBookInitials":"ESV","sourceVersification":"$v11n","sourceOrdinalStart":$source,"sourceOrdinalEnd":$source}}"""

    @Test
    fun verifiedMappingRowsAreRederivedThroughJsword() {
        val (name, ordinal, kjva) = differingVerse()
        val memorization = """{"memorizedVerses":[
            ${verseJson(verseId, kjva, "verifiedMappingV1", "nativeMapping", name, ordinal)},
            ${verseJson("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", ordinal, "verifiedMappingV1", "nativeMapping", name, ordinal)}]}"""
        runProgress(mapOf("memorization_progress_state_v1" to memorization)) { staging, report ->
            // Trusted when the stored KJVA ordinal is the mapped one, untrusted when it is the unmapped source ordinal.
            assertEquals(listOf(kjva), staging.progress.progressDao().allMemorizedVerses().map { it.kjvOrdinal })
            assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.PROGRESS, "memorized verse without trusted ordinals", 1)), skipped(report))
        }
    }

    @Test
    fun verifiedAndroidRowNeedsAndroidImportProvenance() {
        val memorization = """{"memorizedVerses":[
            ${verseJson(verseId, 70, "verifiedAndroid", "androidImport", "KJVA", 70)},
            ${verseJson("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 71, "verifiedAndroid", "nativeMapping", "KJVA", 71)}]}"""
        runProgress(mapOf("memorization_progress_state_v1" to memorization)) { staging, report ->
            assertEquals(listOf(70), staging.progress.progressDao().allMemorizedVerses().map { it.kjvOrdinal })
            assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.PROGRESS, "memorized verse without trusted ordinals", 1)), skipped(report))
        }
    }

    @Test
    fun sourceOrdinalPastTheSourceCanonIsNotTrusted() {
        val past = org.crosswire.jsword.versification.system.Versifications.instance().getVersification("KJV")!!.maximumOrdinal() + 50
        val max = BookmarksMapper.MAX_KJVA_ORDINAL.toInt()
        val memorization = """{"memorizedVerses":[${verseJson(verseId, max, "verifiedMappingV1", "nativeMapping", "KJV", past)}]}"""
        runProgress(mapOf("memorization_progress_state_v1" to memorization)) { staging, _ ->
            assertEquals(0, staging.progress.progressDao().allMemorizedVerses().size)
        }
    }

    @Test
    fun invalidLegacyRangesAreReported() {
        val memorization = """{"memorizedRanges":[{"bookInitials":"","startOrdinal":0,"endOrdinal":3},{"bookInitials":"","startOrdinal":9,"endOrdinal":4},{"bookInitials":"","startOrdinal":5,"endOrdinal":5}]}"""
        runProgress(mapOf("memorization_progress_state_v1" to memorization)) { staging, report ->
            assertEquals(listOf(5), staging.progress.progressDao().allMemorizedVerses().map { it.kjvOrdinal })
            assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.PROGRESS, "invalid memorized verse range", 2)), skipped(report))
        }
    }

    @Test
    fun idLessTargetsGetADeterministicIdAndIdenticalOnesCollapseWithAReason() {
        val memorization = """{"targetRows":[
            {"bookInitials":"","startOrdinal":100,"endOrdinal":250,"createdAt":888},
            {"bookInitials":"","startOrdinal":100,"endOrdinal":250,"createdAt":888},
            {"bookInitials":"","startOrdinal":300,"endOrdinal":310,"createdAt":5}]}"""
        runProgress(mapOf("memorization_progress_state_v1" to memorization)) { staging, report ->
            val targets = staging.progress.progressDao().allMemorizationTargets()
            assertEquals(listOf(100, 300), targets.map { it.kjvOrdinalStart })
            val digest = MessageDigest.getInstance("SHA-256").digest("memorization-target||100|250|888".toByteArray())
            assertEquals(IdType.fromByteArray(digest.copyOf(16)), targets.first().id)
            assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.PROGRESS, "identical memorization targets without id collapsed", 1)), skipped(report))
        }
    }

    @Test
    fun orphanDaysAndRepeatedIdsAreReportedAndDoNotAbortTheWrite() {
        val remote = "fedcba98-7654-3210-fedc-ba9876543210"
        fun envelope(day: Int) = """{"planCode":"p","dayNumber":$day,"readingStatusJSON":"{\"chapterReadArray\":[]}","remoteStatusID":"$remote"}"""
        val data = LegacyReadingPlanData(
            plans = listOf(planRow(1, planId, "p", currentDay = 1), planRow(2, planId, "q", currentDay = 4)),
            days = listOf(
                dayRow(10, 2, 1, completed = false, readings = "A"),
                dayRow(11, 2, 2, completed = false, readings = "B"),
                dayRow(12, 99, 1, completed = true, readings = "C"), // no such plan
            ),
            settings = mapOf(statusKey("q", 1) to envelope(1).replace("\"p\"", "\"q\""), statusKey("q", 2) to envelope(2).replace("\"p\"", "\"q\"")),
        )
        runPlans(data) { staging, report ->
            val dao = staging.readingPlans.readingPlanDao()
            // The later plan row of the repeated plan id wins; of the two statuses sharing one id the later day wins.
            assertEquals(4, dao.getPlan("q")!!.planCurrentDay)
            assertEquals(null, dao.getPlan("p"))
            assertEquals(null, dao.getStatus("q", 1))
            assertEquals(IdType.fromString(remote), dao.getStatus("q", 2)!!.id)
            assertEquals(
                setOf(
                    ReportEntry.Skipped(LegacyCategory.READING_PLANS, "duplicate reading plan id", 1),
                    ReportEntry.Skipped(LegacyCategory.READING_PLANS, "reading plan day without plan", 1),
                    ReportEntry.Skipped(LegacyCategory.READING_PLANS, "duplicate reading plan status id", 1),
                ),
                skipped(report).toSet(),
            )
        }
    }
}
