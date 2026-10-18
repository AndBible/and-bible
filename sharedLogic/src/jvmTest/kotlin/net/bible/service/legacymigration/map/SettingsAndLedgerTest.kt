package net.bible.service.legacymigration.map

import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import androidx.sqlite.execSQL
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.bible.android.database.IdType
import net.bible.service.legacymigration.DeviceLocalPref
import net.bible.service.legacymigration.LegacyExtras
import net.bible.service.legacymigration.LegacySyncSettings
import net.bible.service.legacymigration.PrefType
import net.bible.service.legacymigration.buildExtras
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyStoreReader
import net.bible.service.legacymigration.stage.StagingDatabases
import net.bible.service.legacymigration.testsupport.ApprovedRow
import net.bible.service.legacymigration.testsupport.FakeLegacyPlatform
import net.bible.service.legacymigration.testsupport.FixtureDir
import net.bible.service.legacymigration.testsupport.JvmLegacyDatabaseFactory
import net.bible.service.legacymigration.testsupport.RowSide
import net.bible.service.legacymigration.testsupport.allFixtures
import net.bible.service.legacymigration.testsupport.compareWithOracleDetailed
import net.bible.service.legacymigration.testsupport.fixture
import net.bible.service.legacymigration.testsupport.openStore
import net.bible.service.llm.apiKeyName
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assume

class SettingsAndLedgerTest {
    private fun snapshot(fx: FixtureDir) = LegacySnapshot(
        LegacyStoreReader(File(fx.root.toFile(), "AndBible.store").path),
        LegacyStoreReader(File(fx.root.toFile(), "LocalStore.store").path),
    )

    private val stagings = mutableListOf<Pair<StagingDatabases, File>>()

    private fun newStaging(): StagingDatabases {
        val dir = Files.createTempDirectory("staging-").toFile()
        return StagingDatabases(dir.toOkioPath(), JvmLegacyDatabaseFactory()).also { stagings += it to dir }
    }

    @AfterTest
    fun cleanUp() {
        for ((staging, dir) in stagings) {
            try { staging.close() } finally { dir.deleteRecursively() }
        }
        stagings.clear()
    }

    private fun skipped(report: MigrationReport) = report.entries.filterIsInstance<ReportEntry.Skipped>()
    private fun unknownKeys(report: MigrationReport) = report.entries.filterIsInstance<ReportEntry.UnknownKey>().map { it.key }

    /** Adds or replaces `ZSETTING` rows of the (temporary) fixture copy. */
    private fun putSettings(fx: FixtureDir, values: Map<String, String>) {
        val db = openStore(File(fx.root.toFile(), "LocalStore.store"))
        try {
            fun q(s: String) = "'" + s.replace("'", "''") + "'"
            val ent = db.prepare("SELECT Z_ENT FROM Z_PRIMARYKEY WHERE Z_NAME = 'Setting'").use { it.step(); it.getLong(0) }
            for ((key, value) in values) {
                db.execSQL("DELETE FROM ZSETTING WHERE ZKEY = ${q(key)}")
                db.execSQL("INSERT INTO ZSETTING (Z_PK, Z_ENT, Z_OPT, ZKEY, ZVALUE) VALUES ((SELECT COALESCE(MAX(Z_PK), 0) + 1 FROM ZSETTING), $ent, 1, ${q(key)}, ${q(value)})")
            }
        } finally {
            db.close()
        }
    }

    private class Mapped(val staging: StagingDatabases, val report: MigrationReport)

    /** Runs WORKSPACES (unless [withWorkspaces] is false) and then SETTINGS over [fx]. */
    private fun mapSettings(fx: FixtureDir, withWorkspaces: Boolean = true, userDefaults: Map<String, Any?> = emptyMap(), withReadingPlans: Boolean = false): Mapped {
        val staging = newStaging()
        val report = MigrationReport()
        runBlocking {
            snapshot(fx).use { snap ->
                if (withWorkspaces) WorkspacesMapper().map(snap, staging, MigrationReport())
                if (withReadingPlans) ReadingPlansMapper().map(snap, staging, MigrationReport())
                SettingsMapper { userDefaults }.map(snap, staging, report)
            }
        }
        return Mapped(staging, report)
    }

    private fun stringSettings(staging: StagingDatabases): Map<String, String> =
        runBlocking { staging.settings.stringSettingDao().all().associate { it.key to it.value } }

    private fun boolSettings(staging: StagingDatabases): Map<String, Boolean> =
        runBlocking { staging.settings.booleanSettingDao().all().associate { it.key to it.value } }

    private fun longSettings(staging: StagingDatabases): Map<String, Long> =
        runBlocking { staging.settings.longSettingDao().all().associate { it.key to it.value } }

    // ---- Oracle comparison -------------------------------------------------------------------------------------

    /** The Swift export also writes the four UserDefaults-only device preferences (section 6.3): not migrated by I3a. */
    private val deviceLocalOracleRows = listOf(
        ApprovedRow("BooleanSetting", "key=t:discrete_mode", RowSide.ONLY_IN_ORACLE, "section 6.3: stays in NSUserDefaults"),
        ApprovedRow("BooleanSetting", "key=t:show_calculator", RowSide.ONLY_IN_ORACLE, "section 6.3: stays in NSUserDefaults"),
        ApprovedRow("StringSetting", "key=t:locale_pref", RowSide.ONLY_IN_ORACLE, "section 6.3: stays in NSUserDefaults"),
        ApprovedRow("StringSetting", "key=t:calculator_pin", RowSide.ONLY_IN_ORACLE, "section 6.3: stays in NSUserDefaults"),
        // Android keeps night_mode_pref3 in SharedPreferences: carried by LegacyExtras.deviceLocalPrefs instead.
        ApprovedRow("StringSetting", "key=t:night_mode_pref3", RowSide.ONLY_IN_ORACLE, "device-local on Android, see LegacyExtras.deviceLocalPrefs"),
    )

    /** The Swift mapper never writes `current_workspace_id`; its exact value is asserted by [activeWorkspaceBecomesCurrentWorkspaceId]. */
    private val currentWorkspaceExtraRow =
        ApprovedRow("StringSetting", "key=t:current_workspace_id", RowSide.ONLY_IN_STAGED, "Android reads the active workspace from this key")

    @Test
    fun everyFixtureMatchesItsSettingsOracle() {
        val fixtures = allFixtures()
        assertEquals(24, fixtures.size)
        var booleanRows = 0
        var stringRows = 0
        var currentWorkspaceRows = 0
        for (fx in fixtures) {
            val label = "${fx.app}/${fx.ios}/${fx.scenario}"
            val mapped = mapSettings(fx)
            val result = runBlocking {
                compareWithOracleDetailed(
                    fx, LegacyCategory.SETTINGS, mapped.staging,
                    approvedRows = (deviceLocalOracleRows + currentWorkspaceExtraRow).toSet(),
                )
            }
            assertEquals(emptyList(), result.differences, label)
            assertEquals(emptyList(), unknownKeys(mapped.report), label)
            booleanRows += result.comparedRows["BooleanSetting"] ?: 0
            stringRows += result.comparedRows["StringSetting"] ?: 0
            if ("current_workspace_id" in stringSettings(mapped.staging)) currentWorkspaceRows++
        }
        // Not vacuous: every fixture's oracle has the registered preferences, and the active workspace is carried.
        assertTrue(booleanRows >= 24 * 28 && stringRows >= 24 * 18, "boolean rows $booleanRows string rows $stringRows")
        assertTrue(currentWorkspaceRows >= 12, "current_workspace_id written for $currentWorkspaceRows fixtures")
    }

    @Test
    fun deviceLocalPreferencesAreNotWritten() {
        val mapped = mapSettings(fixture("1.0.2", "18", "rich"), userDefaults = mapOf("discrete_mode" to true, "calculator_pin" to "4321", "locale_pref" to "fi"))
        val strings = stringSettings(mapped.staging)
        val booleans = boolSettings(mapped.staging)
        for (key in listOf("locale_pref", "calculator_pin")) assertFalse(key in strings, key)
        for (key in listOf("discrete_mode", "show_calculator")) assertFalse(key in booleans, key)
    }

    @Test
    fun settingsAndRepositoriesOracleOfEveryFixtureIsEmptyForRepositories() {
        for (fx in allFixtures()) {
            val staging = newStaging()
            val report = MigrationReport()
            runBlocking { snapshot(fx).use { RepositoriesMapper(null, FileSystem.SYSTEM).map(it, staging, report) } }
            val result = runBlocking { compareWithOracleDetailed(fx, LegacyCategory.REPOSITORIES, staging) }
            assertEquals(emptyList(), result.differences, fx.scenario)
            // The fixtures carry no custom repositories (the Swift files are outside the stores): the oracle tables are empty.
            assertEquals(0, result.comparedRows["CustomRepository"], fx.scenario)
            assertEquals(emptyList(), report.entries)
        }
    }

    // ---- Active workspace --------------------------------------------------------------------------------------

    private fun activeWorkspaceOf(fx: FixtureDir): String =
        snapshot(fx).use { it.settings.getValue("active_workspace_id") }

    @Test
    fun activeWorkspaceBecomesCurrentWorkspaceId() {
        val fx = fixture("1.0.2", "18", "rich")
        val active = activeWorkspaceOf(fx)
        val mapped = mapSettings(fx)
        // Swift writes the id upper case, Android's IdType.toString() is lower case.
        assertEquals(active.lowercase(), stringSettings(mapped.staging)["current_workspace_id"])
        assertEquals(IdType.fromString(active.lowercase()).toString(), stringSettings(mapped.staging)["current_workspace_id"])
        assertEquals(emptyList(), skipped(mapped.report))
    }

    @Test
    fun activeWorkspaceThatWasNotMigratedIsSkippedAndReported() {
        val fx = fixture("1.0.2", "18", "rich")
        val reason = "active_workspace_id does not name a migrated workspace, current workspace left to the app default"
        // Workspaces were migrated but the active id is another one.
        val other = fixture("1.0.2", "18", "rich")
        putSettings(other, mapOf("active_workspace_id" to "99999999-9999-9999-9999-999999999999"))
        val mapped = mapSettings(other)
        assertFalse("current_workspace_id" in stringSettings(mapped.staging))
        assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.SETTINGS, reason, 1)), skipped(mapped.report))

        // Not a UUID at all: same outcome, no exception.
        val garbage = fixture("1.0.2", "18", "rich")
        putSettings(garbage, mapOf("active_workspace_id" to "not a uuid"))
        val garbageMapped = mapSettings(garbage)
        assertFalse("current_workspace_id" in stringSettings(garbageMapped.staging))
        assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.SETTINGS, reason, 1)), skipped(garbageMapped.report))
    }

    @Test
    fun settingsBeforeWorkspacesIsAnOrderingFailure() {
        // Not an ordinary skipped entry: WorkspacesMapper always opens the workspace database, so this is a wiring error.
        val e = assertFailsWith<IllegalStateException> { mapSettings(fixture("1.0.2", "18", "rich"), withWorkspaces = false) }
        assertTrue("after WorkspacesMapper" in e.message.orEmpty(), e.message)
    }

    // ---- Rows Android reads under the same keys, device-local preferences -------------------------------------------

    @Test
    fun bibleBookSortOrderDefaultDocumentsAndSelectedPlanAreMappedOnlyWhenSwiftStoredThem() {
        // Nothing stored: nothing invented.
        val none = mapSettings(fixture("1.0.2", "18", "rich"), withReadingPlans = true)
        val noneStrings = stringSettings(none.staging)
        for (key in listOf("BibleBookSortOrder", "default-BIBLE", "default-GENERAL_BOOK", "reading_plan")) assertFalse(key in noneStrings, key)

        val fx = fixture("1.0.2", "18", "rich")
        val planCode = migratedPlanCodes(mapSettings(fx, withReadingPlans = true).staging).first()
        putSettings(
            fx,
            mapOf(
                "BibleBookSortOrder" to "ALPHABETICAL", "default-BIBLE" to "KJV", "default-COMMENTARY" to "MHC", "default-DICTIONARY" to "StrongsGreek",
                "default-GENERAL_BOOK" to "", "default-MAPS" to "MapsX", "reading_plan" to planCode,
            ),
        )
        val mapped = mapSettings(fx, withReadingPlans = true)
        val strings = stringSettings(mapped.staging)
        assertEquals("ALPHABETICAL", strings["BibleBookSortOrder"])
        assertEquals("KJV", strings["default-BIBLE"])
        assertEquals("MHC", strings["default-COMMENTARY"])
        assertEquals("StrongsGreek", strings["default-DICTIONARY"])
        assertEquals("MapsX", strings["default-MAPS"])
        assertFalse("default-GENERAL_BOOK" in strings) // an empty value is no default
        assertEquals(planCode, strings["reading_plan"])
        assertEquals(emptyList(), skipped(mapped.report))
        assertEquals(emptyList(), unknownKeys(mapped.report))
    }

    private fun migratedPlanCodes(staging: StagingDatabases): List<String> = runBlocking {
        staging.readingPlans.useReaderConnection { c ->
            c.usePrepared("SELECT planCode FROM ReadingPlan ORDER BY planCode") { st -> buildList { while (st.step()) add(st.getText(0)) } }
        }
    }

    @Test
    fun invalidSortOrderUnknownDefaultKeyAndUnmigratedPlanAreReported() {
        val fx = fixture("1.0.2", "18", "rich")
        putSettings(fx, mapOf("BibleBookSortOrder" to "alphabetical", "default-NEWS" to "X", "reading_plan" to "no-such-plan"))
        val mapped = mapSettings(fx, withReadingPlans = true)
        val strings = stringSettings(mapped.staging)
        for (key in listOf("BibleBookSortOrder", "default-NEWS", "reading_plan")) assertFalse(key in strings, key)
        assertEquals(
            setOf(
                ReportEntry.Skipped(LegacyCategory.SETTINGS, "BibleBookSortOrder that is neither ALPHABETICAL nor BIBLE_BOOK", 1),
                ReportEntry.Skipped(LegacyCategory.SETTINGS, "default document of an unknown category", 1),
                ReportEntry.Skipped(LegacyCategory.SETTINGS, "selected reading plan that was not migrated, no plan selected", 1),
            ),
            skipped(mapped.report).toSet(),
        )
        // Without a ReadingPlansMapper run no plan is staged either.
        val noPlans = fixture("1.0.2", "18", "rich")
        putSettings(noPlans, mapOf("reading_plan" to "anything"))
        assertFalse("reading_plan" in stringSettings(mapSettings(noPlans).staging))
    }

    @Test
    fun deviceLocalPreferencesStaySharedPreferencesAndTravelInTheExtras() {
        val fx = fixture("1.0.2", "18", "rich")
        val speak = """{"numPagesToSpeakId":3,"queue":true}"""
        putSettings(fx, mapOf("night_mode_pref3" to "manual", "night_mode" to "true", "SpeakSettings" to speak))
        // Not in the SettingsDatabase (Android reads them from realSharedPreferences) ...
        val mapped = mapSettings(fx)
        assertFalse("night_mode_pref3" in stringSettings(mapped.staging))
        assertFalse("night_mode" in boolSettings(mapped.staging) || "night_mode_pref" in boolSettings(mapped.staging))
        assertFalse("SpeakSettings" in stringSettings(mapped.staging))
        // ... but in the extras, with Android's keys and types.
        val extras = snapshot(fx).use { buildExtras(it, FakeLegacyPlatform.from(fx), MigrationReport()) }
        assertEquals(
            listOf(
                DeviceLocalPref("night_mode_pref3", PrefType.STRING, "manual"),
                DeviceLocalPref("night_mode_pref", PrefType.BOOLEAN, "true"),
                DeviceLocalPref("SpeakSettings", PrefType.STRING, speak),
            ),
            extras.deviceLocalPrefs,
        )
        // night_mode is true only for the literal "true"; absent values are not invented.
        val other = fixture("1.0.2", "18", "fresh")
        putSettings(other, mapOf("night_mode" to "1"))
        assertEquals(
            listOf(DeviceLocalPref("night_mode_pref", PrefType.BOOLEAN, "false")),
            snapshot(other).use { buildExtras(it, FakeLegacyPlatform.from(other), MigrationReport()) }.deviceLocalPrefs,
        )
        assertEquals(emptyList(), snapshot(fixture("1.0.2", "18", "fresh")).use { buildExtras(it, FakeLegacyPlatform.from(fixture("1.0.2", "18", "fresh")), MigrationReport()) }.deviceLocalPrefs)
    }

    @Test
    fun unknownUserDefaultsKeysAreReportedExceptAppleSystemKeys() {
        val mapped = mapSettings(
            fixture("1.0.2", "18", "rich"),
            userDefaults = mapOf(
                "discrete_mode" to true, "AppleLanguages" to listOf("fi"), "NSInterfaceStyle" to "x", "AppleLocale" to "fi_FI",
                "com.apple.keyboard" to 1, "AKLastIDMSEnvironment" to 0, "zz_new_default" to 1,
            ),
        )
        assertEquals(listOf("zz_new_default"), unknownKeys(mapped.report))
    }

    @Test
    fun secretsNeverAppearInToStringOrTheReport() {
        val fx = fixture("1.0.2", "18", "rich")
        val report = MigrationReport()
        val extras = snapshot(fx).use { buildExtras(it, FakeLegacyPlatform.from(fx), report) }
        val secrets = listOf("test-ai-key-not-a-secret", "test-gdrive-password-not-a-secret")
        val sync = assertNotNull(extras.sync)
        // The data is there ...
        assertEquals(secrets[1], sync.password)
        assertEquals(secrets[0], extras.aiKeys.values.single())
        // ... but not in any printed form.
        for (text in listOf(extras.toString(), sync.toString(), report.toJson(), LegacyExtras(extras.aiKeys, sync, false, false, emptyList()).toString())) {
            for (secret in secrets) assertFalse(secret in text, "secret printed in: $text")
        }
        assertTrue("<redacted>" in sync.toString())
        assertTrue("a2000000-0000-0000-0000-000000000001" in extras.toString()) // ids are fine
        assertEquals("none", LegacySyncSettings("NEXT_CLOUD", "u", "n", "", null, emptySet(), null).toString().substringAfter("password=").trimEnd(')'))
    }

    // ---- Unknown keys and value decoding ------------------------------------------------------------------------

    @Test
    fun unknownSettingKeyIsReported() {
        val fx = fixture("1.0.2", "18", "rich")
        putSettings(fx, mapOf("zz_new_key" to "whatever"))
        val mapped = mapSettings(fx)
        assertEquals(listOf("zz_new_key"), unknownKeys(mapped.report))
        // The migration went on: the registered preferences are there.
        assertTrue(boolSettings(mapped.staging).size >= 24)
        assertFalse("zz_new_key" in stringSettings(mapped.staging))
    }

    @Test
    fun storedPreferenceValuesAreDecodedLikeTheSwiftSettingsStore() {
        val fx = fixture("1.0.2", "18", "rich")
        putSettings(
            fx,
            mapOf(
                "navigate_to_verse_pref" to "true", // default false
                "open_links_in_special_window_pref" to "false", // default true
                "speak_synchronize" to "TRUE", // not the literal "true": false, and reported
                "font_size_multiplier" to "250",
                "strongs_greek_dictionary" to " b, a ,,c ,b",
                "notes_content_type" to "RTF",
                "toolbar_button_actions" to "",
                "bible_view_swipe_mode" to "PAGE",
            ),
        )
        val mapped = mapSettings(fx, userDefaults = mapOf("BookmarkSortOrder" to "DATE_CREATED"))
        val booleans = boolSettings(mapped.staging)
        assertEquals(true, booleans["navigate_to_verse_pref"])
        assertEquals(false, booleans["open_links_in_special_window_pref"])
        assertEquals(false, booleans["speak_synchronize"])
        assertEquals(250L, longSettings(mapped.staging)["font_size_multiplier"])
        val strings = stringSettings(mapped.staging)
        assertEquals("a,b,b,c", strings["strongs_greek_dictionary"]) // sorted, trimmed, blanks dropped; no de-duplication (Swift)
        assertEquals("HTML", strings["notes_content_type"])
        assertEquals("", strings["toolbar_button_actions"]) // a stored empty string is kept, not replaced by the default
        assertEquals("PAGE", strings["bible_view_swipe_mode"])
        assertEquals("DATE_CREATED", strings["BookmarkSortOrder"])
        assertEquals(
            setOf(
                ReportEntry.Skipped(LegacyCategory.SETTINGS, "boolean setting with a value other than true or false, stored as false", 1),
                ReportEntry.Skipped(LegacyCategory.SETTINGS, "notes_content_type other than HTML or MARKDOWN, stored as HTML", 1),
            ),
            skipped(mapped.report).toSet(),
        )
    }

    @Test
    fun malformedIntegerFallsBackToTheDefaultAndIsReported() {
        val fx = fixture("1.0.2", "18", "rich")
        putSettings(fx, mapOf("font_size_multiplier" to "huge"))
        val mapped = mapSettings(fx)
        assertEquals(100L, longSettings(mapped.staging)["font_size_multiplier"])
        assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.SETTINGS, "integer setting that is not a number, default used", 1)), skipped(mapped.report))
    }

    @Test
    fun bookmarkSortOrderUserDefaultBooleanAndIntegerShapesBecomeText() {
        // SettingsStore.readStoredValue: only a string is kept as is; a number or boolean is turned into its text.
        val mapped = mapSettings(fixture("1.0.2", "18", "rich"), userDefaults = mapOf("BookmarkSortOrder" to 3))
        assertEquals("3", stringSettings(mapped.staging)["BookmarkSortOrder"])
        val absent = mapSettings(fixture("1.0.2", "18", "rich"))
        assertEquals("BIBLE_ORDER", stringSettings(absent.staging)["BookmarkSortOrder"])
    }

    @Test
    fun insertStatementsMatchTheStagedTableColumns() {
        val staging = newStaging()
        runBlocking { snapshot(fixture("1.0.2", "18", "rich")).use { run { WorkspacesMapper().map(it, staging, MigrationReport()); SettingsMapper { emptyMap() }.map(it, staging, MigrationReport()) } } }
        for (sql in SettingsMapper.INSERT_STATEMENTS) {
            val table = sql.substringAfter("INSERT INTO ").substringBefore(" ")
            val listed = sql.substringAfter("(").substringBefore(")").split(", ")
            assertEquals(columnsOf(staging.settings, table), listed, table)
        }
    }

    private fun columnsOf(db: RoomDatabase, table: String): List<String> = runBlocking {
        db.useReaderConnection { c ->
            c.usePrepared("SELECT name FROM pragma_table_info('$table') ORDER BY cid") { st -> buildList { while (st.step()) add(st.getText(0)) } }
        }
    }

    @Test
    fun registryHasTheSwiftPreferenceCount() {
        // AppPreferenceKey.allCases at the pin: 50 keys, 3 of them action rows.
        assertEquals(50, SettingsMapper.DEFINITIONS.size)
        assertEquals(3, SettingsMapper.DEFINITIONS.count { it.storage == SettingsMapper.Storage.ACTION })
        assertEquals(50, SettingsMapper.DEFINITIONS.map { it.key }.toSet().size)
    }

    // ---- Key ledger --------------------------------------------------------------------------------------------

    @Test
    fun ledgerDecidesTheSyncKeys() {
        for (key in listOf(
            "remote_sync.accepted_baseline.bookmarks", "remote_sync.log_entries.bookmarks.x", "remote_sync.pending_mutations.progress.y",
            "remote_sync.row_fingerprints.mydocuments.z", "remote_sync.workspaces.pending_upload", "remote_sync_device_identifier",
            "remote_sync_status.bookmarks", "globalLastSynchronized",
        )) {
            val entry = assertNotNull(KeyLedger.classify(key), key)
            assertEquals(Disposition.DROPPED, entry.disposition, key)
            assertEquals("sync re-enabled by the user, U2", entry.reason, key)
        }
        // Rows other mappers consume are mapped, not dropped.
        for (key in listOf(
            "remote_sync.workspaces.fidelity.workspace.1", "remote_sync.bookmarks.android_book.13", "remote_sync.bookmarks.android_playback_settings.bible.1",
            "remote_sync.readingplans.android_status.x", "remote_sync.readingplans.start_date_millis.y",
        )) assertEquals(Disposition.MAPPED, KeyLedger.classify(key)?.disposition, key)
        // Nextcloud connection settings and the password are for I3B.
        for (key in listOf("sync_adapter", "gdrive_server_url", "gdrive_username", "gdrive_folder_path", "gdrive_sync_interval", "gdrive_password", "sync_enable_bookmarks", "gdrive_bookmarks")) {
            assertEquals(Disposition.I3B, KeyLedger.classify(key)?.disposition, key)
        }
        assertEquals(LedgerSource.KEYCHAIN, KeyLedger.classify("gdrive_password")?.source)
        // Device-local preferences stay in place (section 6.3).
        for (key in listOf("discrete_mode", "show_calculator", "calculator_pin", "locale_pref", "AppleLanguages")) {
            val entry = assertNotNull(KeyLedger.classify(key), key)
            assertEquals(Disposition.MAPPED, entry.disposition, key)
            assertTrue("section 6.3" in entry.target, key)
        }
        assertNull(KeyLedger.classify("zz_new_key"))
        assertNull(KeyLedger.classify("remote"))
    }

    @Test
    fun ledgerPrefersTheMostSpecificRow() {
        assertEquals("remote_sync.workspaces.fidelity.", KeyLedger.classify("remote_sync.workspaces.fidelity.x")?.keyOrPrefix)
        assertEquals("remote_sync.", KeyLedger.classify("remote_sync.workspaces.pending_upload")?.keyOrPrefix)
        assertEquals("gdrive_password", KeyLedger.classify("gdrive_password")?.keyOrPrefix)
        assertEquals("gdrive_", KeyLedger.classify("gdrive_readingplans")?.keyOrPrefix)
        assertEquals("active_workspace_id", KeyLedger.classify("active_workspace_id")?.keyOrPrefix)
        assertTrue(KeyLedger.entries.map { it.keyOrPrefix to it.isPrefix }.let { it.size == it.toSet().size }, "duplicate ledger rows")
    }

    @Test
    fun everyRegisteredPreferenceIsInTheLedger() {
        for (def in SettingsMapper.DEFINITIONS) assertNotNull(KeyLedger.classify(def.key), def.key)
    }

    /** The clone of the Swift app that the freeze check reads, or null when the container has none. */
    private fun swiftClone(): File? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            File(dir, ".local/repos/and-bible-ios").takeIf { File(it, "Package.swift").isFile }?.let { return it }
            dir = dir.parentFile
        }
        return null
    }

    @Test
    fun ledgerCoversSwiftKeys() {
        val clone = swiftClone()
        Assume.assumeTrue("Swift clone .local/repos/and-bible-ios not found: key ledger freeze check skipped", clone != null)
        val sources = clone!!.walkTopDown()
            .onEnter { it.name !in setOf("Tests", ".build", ".git", "node_modules") }
            .filter { it.isFile && it.extension == "swift" }
            .toList()
        assertTrue(sources.size > 300, "only ${sources.size} Swift files found under $clone")

        val found = sortedMapOf<String, String>() // key to where it was found
        val literal = "\"([^\"\\\\]+)\""
        val storeCall = Regex("""\.(?:setString|getString|setBool|getBool|setInt|getInt|setDouble|getDouble|remove|entries)\(\s*(?:withPrefix:\s*)?$literal""")
        val defaultsKey = Regex("""forKey:\s*$literal""")
        val appStorage = Regex("""@AppStorage\($literal""")
        val keyConstant = Regex("""static let (\w*Key\w*)\s*(?::\s*\w+)?\s*=\s*$literal""")
        // Constants named ...Key that are not stored settings: dictionary keys, Info.plist, thread dictionary and UI test environment names.
        val notStored = setOf("libraryChangeKindUserInfoKey", "multiRenderedKey", "strongsRenderedKey", "infoPlistKey", "threadDictionaryKey")
        for (file in sources) {
            val text = file.readText()
            if (file.name == "AppPreferenceRegistry.swift") {
                Regex("""case \w+ = $literal""").findAll(text).forEach { found[it.groupValues[1]] = "AppPreferenceKey" }
            }
            storeCall.findAll(text).forEach { found[it.groupValues[1]] = "${file.name} settings call" }
            appStorage.findAll(text).forEach { found[it.groupValues[1]] = "${file.name} @AppStorage" }
            text.lines().filter { "efaults" in it }.forEach { line ->
                defaultsKey.findAll(line).forEach { found[it.groupValues[1]] = "${file.name} UserDefaults" }
            }
            // `private enum Keys { static let backend = "sync_adapter" ... }` of the *Store files.
            if (file.name.endsWith("Store.swift") && "enum Keys" in text) {
                val block = text.substringAfter("enum Keys").substringBefore("\n    }")
                // A block with a `scopePrefix` holds suffixes of keys under that prefix (RemoteSyncStateStore): only the prefix is a key.
                Regex("""static let (\w+)\s*=\s*$literal""").findAll(block)
                    .filter { "scopePrefix" !in block || it.groupValues[1] == "scopePrefix" }
                    .forEach { found[it.groupValues[2]] = "${file.name} Keys" }
            }
            keyConstant.findAll(text).forEach {
                val (name, value) = it.destructured
                if (name !in notStored && !name.endsWith("EnvironmentKey") && !value.startsWith("UITEST_")) found[value] = "${file.name} $name"
            }
        }
        // Not vacuous: the scan sees the registry, a literal Setting call, a UserDefaults key and a constant.
        for (key in listOf("gdrive_sync_interval", "remote_sync_device_identifier", "night_mode_pref3", "speak_autoBookmark", "lastSpeakRef", "AppleLanguages", "icloud_sync_enabled", "active_workspace_id", "reading_progress_state_v1")) {
            assertTrue(key in found, "scan did not find $key (found ${found.size} keys)")
        }
        assertTrue(found.size >= 70, "only ${found.size} keys found")
        val unclassified = found.filterKeys { KeyLedger.classify(it) == null }
        assertTrue(unclassified.isEmpty(), "keys the KeyLedger does not classify: $unclassified")
    }

    // ---- Extras ------------------------------------------------------------------------------------------------

    @Test
    fun extrasFromRichSidecar() {
        val fx = fixture("1.0.2", "18", "rich")
        val platform = FakeLegacyPlatform.from(fx)
        val report = MigrationReport()
        val extras = snapshot(fx).use { buildExtras(it, platform, report) }

        // The AI key sits under the provider id, and the new secret store names it apiKeyName(id), like the sidecar's account.
        val providerId = IdType.fromString("a2000000-0000-0000-0000-000000000001")
        assertEquals("llm_api_key_a2000000-0000-0000-0000-000000000001", apiKeyName(providerId))
        assertEquals(mapOf(providerId to "test-ai-key-not-a-secret"), extras.aiKeys)

        val sync = assertNotNull(extras.sync)
        assertEquals("NEXT_CLOUD", sync.adapter)
        assertEquals("https://cloud.example.invalid/remote.php/dav/files/fixture", sync.serverUrl)
        assertEquals("fixture-user", sync.username)
        assertEquals("AndBibleFixture", sync.folderPath)
        assertEquals(null, sync.interval)
        assertEquals(setOf("bookmarks"), sync.enabledCategories)
        assertEquals("test-gdrive-password-not-a-secret", sync.password)
        assertTrue(extras.icloudSyncWasEnabled)
        assertFalse(extras.unsentSyncPatches)
        assertEquals(emptyList(), extras.userPlanFiles)
        assertEquals(emptyList(), report.entries)
        // buildExtras only reads: the new secret store is untouched.
        assertNull(platform.secrets.get(apiKeyName(providerId)))
    }

    @Test
    fun unsentSyncPatchesAreSeenInAnyCategoryOutboxDirectory() {
        val fx = fixture("1.0.2", "18", "rich")
        val platform = FakeLegacyPlatform.from(fx)
        val outbox = platform.applicationSupport / "AndBible" / "RemoteSyncOutbox"
        fun unsent() = snapshot(fx).use { buildExtras(it, platform, MigrationReport()) }.unsentSyncPatches

        assertFalse(unsent()) // no outbox at all
        File(outbox.toString(), "Workspaces").mkdirs()
        File(outbox.toString(), "bookmarks").mkdirs()
        assertFalse(unsent()) // empty category directories are not unsent patches
        File(outbox.toString(), "Progress").mkdirs()
        File(outbox.toString(), "Progress/0001.patch").writeText("x")
        assertTrue(unsent())
    }

    @Test
    fun pendingInitialUploadsCountAsUnsentSyncWork() {
        val fx = fixture("1.0.2", "18", "rich")
        val platform = FakeLegacyPlatform.from(fx)
        val uploads = File(platform.applicationSupport.toString(), "RemoteSyncInitialUploads")
        fun unsent() = snapshot(fx).use { buildExtras(it, platform, MigrationReport()) }.unsentSyncPatches

        assertFalse(unsent()) // directory absent
        uploads.mkdirs()
        assertFalse(unsent()) // directory empty
        File(uploads, "bookmarks-pending.sqlite3.gz").writeText("x")
        assertTrue(unsent())
    }

    @Test
    fun aiKeysAreMappedByTheAdopterNotDeferred() {
        val entry = KeyLedger.classify("llm_api_key_a2000000-0000-0000-0000-000000000001")
        assertEquals(Disposition.MAPPED, entry?.disposition)
        assertEquals(LedgerSource.KEYCHAIN, entry?.source)
        assertTrue(entry!!.target.contains("apiKeyName"))
    }

    @Test
    fun extrasWithoutNextcloudAreEmptyAndDefaultsAreSafe() {
        val fx = fixture("1.0.2", "18", "fresh")
        val extras = snapshot(fx).use { buildExtras(it, FakeLegacyPlatform.from(fx), MigrationReport()) }
        assertEquals(LegacyExtras(emptyMap(), null, false, false, emptyList()), extras)

        // An iCloud user who once typed Nextcloud settings has no sync settings to carry.
        val rich = fixture("1.0.2", "18", "rich")
        putSettings(rich, mapOf("sync_adapter" to "ICLOUD"))
        assertNull(snapshot(rich).use { buildExtras(it, FakeLegacyPlatform.from(rich), MigrationReport()) }.sync)
    }

    @Test
    fun syncSettingsReadLegacyToggleKeysIntervalAndMissingPassword() {
        val fx = fixture("1.0.2", "18", "rich")
        putSettings(
            fx,
            mapOf(
                "sync_enable_bookmarks" to "false", // the current key wins over the legacy one
                "gdrive_bookmarks" to "true",
                "gdrive_workspaces" to "true", // legacy only
                "gdrive_llmprocessing" to "true", // legacy AI toggle
                "sync_enable_progress" to "yes", // anything but "true" is off
                "gdrive_sync_interval" to " 600 ",
            ),
        )
        val platform = FakeLegacyPlatform(fx.root, userDefaults = mapOf("icloud_sync_enabled" to "YES"))
        val report = MigrationReport()
        val extras = snapshot(fx).use { buildExtras(it, platform, report) }
        val sync = assertNotNull(extras.sync)
        assertEquals(setOf("workspaces", "ai_settings"), sync.enabledCategories)
        assertEquals("600", sync.interval)
        assertNull(sync.password)
        assertTrue(extras.icloudSyncWasEnabled)
        assertEquals(emptyList(), extras.aiKeys.entries.toList()) // no keychain items

        val negative = fixture("1.0.2", "18", "rich")
        putSettings(negative, mapOf("gdrive_sync_interval" to "-5"))
        val negativeReport = MigrationReport()
        val negativeExtras = snapshot(negative).use { buildExtras(it, FakeLegacyPlatform.from(negative), negativeReport) }
        assertNull(negativeExtras.sync?.interval)
        assertEquals(listOf(ReportEntry.Skipped(LegacyCategory.SETTINGS, "invalid sync interval, app default used", 1)), skipped(negativeReport))
    }

    @Test
    fun userPlanFilesAreTheFilesOfTheReadingPlanDirectory() {
        val fx = fixture("1.0.2", "18", "fresh")
        val platform = FakeLegacyPlatform.from(fx)
        val dir = File(platform.documents.toString(), "jsword/readingplan").also { it.mkdirs() }
        File(dir, "b_plan.properties").writeText("x")
        File(dir, "a_plan.properties").writeText("x")
        File(dir, "nested").mkdirs()
        val extras = snapshot(fx).use { buildExtras(it, platform, MigrationReport()) }
        assertEquals(listOf("a_plan.properties", "b_plan.properties"), extras.userPlanFiles.map { it.name })
    }

    // ---- Repositories (hand-built: the fixtures carry no custom repositories) ---------------------------------------

    private fun record(name: String, type: String = "sword-https", host: String = "example.org", catalog: String = "/sword", pkg: String = "/sword/zip",
        manifest: String = "https://example.org/manifest.json", description: String = "Desc") =
        """{"name":"$name","description":"$description","type":"$type","host":"$host","catalogDirectory":"$catalog","packageDirectory":"$pkg","manifestURL":"$manifest","sourceURL":"https://$host$catalog"}"""

    private class Sword(val json: okio.Path)

    private fun sworddir(json: String?, conf: String?): Sword {
        val dir = Files.createTempDirectory("sword_install-").toFile().also { tempDirs += it }
        if (json != null) File(dir, "CustomRepositories.json").writeText(json)
        if (conf != null) File(dir, "InstallMgr.conf").writeText(conf)
        return Sword((File(dir, "CustomRepositories.json")).toOkioPath())
    }

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun removeTempDirs() { tempDirs.forEach { it.deleteRecursively() }; tempDirs.clear() }

    private fun rowsOf(m: RepositoriesMapper, report: MigrationReport) =
        m.build(report).map { listOf(it.name, it.description, it.type, it.host, it.catalogDirectory, it.packageDirectory, it.manifestUrl) }

    @Test
    fun customRepositoriesFollowSwiftLoadSourcesAndExport() {
        val json = """{"version":1,"repositories":[
            ${record("My SWORD")},
            ${record("MyBible One", type = "mybible-https", host = "mb.example.org", catalog = "/manifest.json", pkg = "", manifest = "https://mb.example.org/manifest.json", description = "MB")},
            ${record("CrossWire", host = "crosswire.org", catalog = "/ftpmirror/pub/sword/raw")},
            ${record("Bad|Name")},
            ${record("HttpOnly", type = "mybible-https", manifest = "http://example.org/m.json")},
            ${record("Weird", type = "ftp")}
        ]}"""
        val conf = """
            [Sources]
            HTTPSource=CrossWire|crosswire.org|/ftpmirror/pub/sword/raw
            HTTPSource=My SWORD|example.org|/sword
            HTTPSource=Legacy Only|legacy.example.org|/cat
            FTPSource=Custom FTP|ftp.example.org|/pub
            HTTPSource=Legacy Only|other.example.org|/x
        """.trimIndent()
        val report = MigrationReport()
        val rows = rowsOf(RepositoriesMapper(sworddir(json, conf).json, FileSystem.SYSTEM), report)
        assertEquals(
            listOf(
                // A config row enriched by its record: host and catalog from the config, the rest from the record.
                listOf("My SWORD", "Desc", "sword-https", "example.org", "/sword", "/sword/zip", "https://example.org/manifest.json"),
                // A config row without a record is backfilled: URL as description and manifest, catalog/packages as package directory.
                listOf("Legacy Only", "https://legacy.example.org/cat", "sword-https", "legacy.example.org", "/cat", "/cat/packages", "https://legacy.example.org/cat"),
                // A MyBible record that is not in the config is appended; its empty package directory stays empty.
                listOf("MyBible One", "MB", "mybible-https", "mb.example.org", "/manifest.json", "", "https://mb.example.org/manifest.json"),
            ),
            rows,
        )
        assertEquals(
            setOf(
                ReportEntry.Skipped(LegacyCategory.REPOSITORIES, "invalid custom repository record dropped", 3),
                ReportEntry.Skipped(LegacyCategory.REPOSITORIES, "custom repository of an unsupported type (ftp)", 1),
                ReportEntry.Skipped(LegacyCategory.REPOSITORIES, "custom repository with a name already in use", 1),
            ),
            skipped(report).toSet(),
        )
    }

    @Test
    fun customRepositoriesAreWrittenWithSequentialIds() {
        val json = """{"version":1,"repositories":[${record("B Repo")},${record("A Repo", host = "a.example.org")}]}"""
        val conf = "HTTPSource=B Repo|example.org|/sword\nHTTPSource=A Repo|a.example.org|/sword\n"
        val staging = newStaging()
        val report = MigrationReport()
        runBlocking { snapshot(fixture("1.0.2", "18", "fresh")).use { RepositoriesMapper(sworddir(json, conf).json, FileSystem.SYSTEM).map(it, staging, report) } }
        val all = runBlocking { staging.repositories.customRepositoryDao().all() }
        assertEquals(listOf(1L to "B Repo", 2L to "A Repo"), all.map { it.id to it.name }) // config order, not alphabetical
        assertEquals(listOf(ReportEntry.Written(LegacyCategory.REPOSITORIES, "CustomRepository", 2)), report.entries)
    }

    @Test
    fun repositoriesWithoutSidecarOrConfigYieldNothing() {
        assertEquals(emptyList(), rowsOf(RepositoriesMapper(null, FileSystem.SYSTEM), MigrationReport()))
        val missing = MigrationReport()
        assertEquals(emptyList(), rowsOf(RepositoriesMapper(sworddir(null, null).json, FileSystem.SYSTEM), missing))
        assertEquals(emptyList(), missing.entries)
        // A SWORD record whose name is not in the (absent) config is not a source; a MyBible record is.
        val json = """{"version":1,"repositories":[${record("My SWORD")},${record("MB", type = "mybible-https", pkg = "")}]}"""
        assertEquals(listOf("MB"), rowsOf(RepositoriesMapper(sworddir(json, null).json, FileSystem.SYSTEM), MigrationReport()).map { it[0] })
    }

    @Test
    fun undecodableSidecarIsReportedAndConfigRowsAreStillBackfilled() {
        for (bad in listOf("{not json", """{"version":1}""", """{"version":1,"repositories":[{"name":"x"}]}""")) {
            val report = MigrationReport()
            val rows = rowsOf(RepositoriesMapper(sworddir(bad, "HTTPSource=Legacy Only|legacy.example.org|/cat\n").json, FileSystem.SYSTEM), report)
            assertEquals(listOf("Legacy Only"), rows.map { it[0] }, bad)
            assertEquals(
                listOf(ReportEntry.Skipped(LegacyCategory.REPOSITORIES, "CustomRepositories.json could not be decoded, custom repositories not migrated", 1)),
                skipped(report), bad,
            )
        }
    }

    @Test
    fun unreadableInstallMgrConfIsReported() {
        val sword = sworddir("""{"version":1,"repositories":[${record("MB", type = "mybible-https", pkg = "")}]}""", null)
        File(sword.json.parent!!.toString(), "InstallMgr.conf").mkdirs() // exists, but cannot be read as a file
        val report = MigrationReport()
        assertEquals(listOf("MB"), rowsOf(RepositoriesMapper(sword.json, FileSystem.SYSTEM), report).map { it[0] })
        assertEquals(
            listOf(ReportEntry.Skipped(LegacyCategory.REPOSITORIES, "InstallMgr.conf could not be read, SWORD custom repositories not migrated", 1)),
            skipped(report),
        )
    }
}
