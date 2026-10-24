package net.bible.service.legacymigration.map

import androidx.room3.RoomDatabase
import androidx.room3.immediateTransaction
import net.bible.android.database.IdType
import net.bible.service.db.useReaderConnectionMarked
import net.bible.service.db.useWriterConnectionMarked
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.stage.StagingDatabases

/**
 * Migrates the application preferences into the current `SettingsDatabase`.
 *
 * Port of `AndroidDatabaseBackupSettingsMapper.writeDatabase`: the Swift exporter writes one row for every registered
 * non-action preference of `AppPreferenceRegistry` (also the ones the user never changed, at their registry default),
 * decoding the stored `Setting` string through `SettingsStore.getBool/getInt/getString/getStringSet` (a boolean is true
 * only for the literal `"true"`, an unparsable integer falls back to the default, a string set is trimmed, de-blanked
 * and sorted, `notes_content_type` falls back to `HTML`). [DEFINITIONS] is that registry. Differences, all deliberate:
 *
 * - **Device-local preferences are not written** (section 6.3 of the spec): `discrete_mode`, `show_calculator`,
 *   `calculator_pin` and `locale_pref` live in UserDefaults under Android's own key names and survive in place.
 *   The Swift export writes their values (in the oracle comparison they are approved missing rows). `BookmarkSortOrder`
 *   is also a UserDefaults preference; it is written from [userDefaults] (the registry default when absent).
 * - **Active workspace.** The Swift mapper never writes `current_workspace_id`, but Android reads the active workspace
 *   from that string setting (fallback: the first workspace). `active_workspace_id` is therefore written to it,
 *   lower-cased as `IdType.toString()`, only when the id names a workspace that is really in the staged
 *   `WorkspaceDatabase`; otherwise it is reported as skipped. **Ordering contract:** this mapper reads the staged
 *   workspaces, so `SETTINGS` must run after `WORKSPACES` (the mapper list of the migration runner orders them).
 * - **Keys Android reads but the Swift export does not write** are mapped when Swift stored them: `BibleBookSortOrder`
 *   (only ALPHABETICAL or BIBLE_BOOK, Android's `valueOf` would crash otherwise), the five `default-<category>` book
 *   initials (verbatim; Android falls back when the module is not installed) and `reading_plan` (only when that plan code
 *   was migrated into the staged reading plan database, so \`ReadingPlansMapper\` should run before; an unopened one holds none).
 * - **Device-local preferences that Swift kept in SwiftData** (`night_mode_pref3`, `night_mode`, `SpeakSettings`) are not
 *   written: Android keeps them in SharedPreferences. `buildExtras` carries them as `LegacyExtras.deviceLocalPrefs`.
 * - **Ordering:** a staged workspace database that was never opened means the mappers ran in the wrong order and is an
 *   [IllegalStateException]; a not-migrated active id is only reported.
 * - **Unusual values are reported, not silent:** a malformed boolean or integer (which Swift decodes to false or the
 *   default) and an invalid `notes_content_type` (stored as `HTML`) are counted in the [MigrationReport].
 * - **Unknown keys** (not in the [KeyLedger]) are reported with [MigrationReport.unknownKey]; the migration goes on.
 *
 * Rows are inserted with SQL in one writer transaction (text bound as bytes), like the other raw-row mappers.
 */
class SettingsMapper(private val userDefaults: () -> Map<String, Any?>) : CategoryMapper {
    override val category = LegacyCategory.SETTINGS

    /** [DEVICE_LOCAL]: stored in SwiftData by Swift, but Android keeps it in SharedPreferences (carried by `LegacyExtras.deviceLocalPrefs`). */
    enum class Storage { SWIFT_DATA, USER_DEFAULTS, ACTION, DEVICE_LOCAL }

    /** How a preference is decoded and which Android table it goes to. */
    enum class ValueType(val table: String) { BOOL("BooleanSetting"), INT("LongSetting"), STRING("StringSetting"), CSV("StringSetting"), ACTION("") }

    /** One registered preference: key, storage backend, value type and the registry's raw default (null = none). */
    class Def(val key: String, val storage: Storage, val type: ValueType, val default: String?)

    override suspend fun map(snapshot: LegacySnapshot, staging: StagingDatabases, report: MigrationReport) {
        val settings = snapshot.settings
        // WorkspacesMapper always opens the workspace database, so an unopened one means SETTINGS ran before WORKSPACES.
        val workspaces = staging.openedDatabase("workspaces.sqlite3")
            ?: throw IllegalStateException("SettingsMapper must run after WorkspacesMapper (the staged workspace database is not open)")
        for (key in settings.keys) if (KeyLedger.classify(key) == null) report.unknownKey(key)
        for (key in userDefaults().keys) if (!isAppleSystemKey(key) && KeyLedger.classify(key) == null) report.unknownKey(key)

        val drops = Drops()
        val booleans = mutableListOf<List<Any?>>()
        val longs = mutableListOf<List<Any?>>()
        val strings = mutableListOf<List<Any?>>()
        val defaults = userDefaults()
        for (def in DEFINITIONS) {
            val raw = when (def.storage) {
                Storage.ACTION, Storage.DEVICE_LOCAL -> continue
                Storage.SWIFT_DATA -> settings[def.key]
                // Only BookmarkSortOrder of the UserDefaults preferences is written (section 6.3 for the others).
                Storage.USER_DEFAULTS -> if (def.key == BOOKMARK_SORT_ORDER) userDefaultsText(defaults[def.key]) else continue
            }
            when (def.type) {
                ValueType.BOOL -> {
                    if (raw != null && raw != "true" && raw != "false") drops.add("boolean setting with a value other than true or false, stored as false")
                    booleans += listOf(Txt(def.key), if (raw == null) (if (def.default == "true") 1L else 0L) else if (raw == "true") 1L else 0L)
                }
                ValueType.INT -> {
                    val parsed = raw?.toLongOrNull()
                    if (raw != null && parsed == null) drops.add("integer setting that is not a number, default used")
                    longs += listOf(Txt(def.key), parsed ?: def.default?.toLongOrNull() ?: 0L)
                }
                ValueType.STRING -> {
                    var value = raw ?: def.default ?: ""
                    if (def.key == NOTES_CONTENT_TYPE && value != "HTML" && value != "MARKDOWN") {
                        drops.add("notes_content_type other than HTML or MARKDOWN, stored as HTML")
                        value = "HTML"
                    }
                    strings += listOf(Txt(def.key), Txt(value))
                }
                ValueType.CSV -> strings += listOf(Txt(def.key), Txt(normalizeCsvSet(raw)))
                ValueType.ACTION -> Unit
            }
        }

        settings["active_workspace_id"]?.let { raw ->
            when (val id = migratedWorkspaceId(raw, workspaces)) {
                null -> drops.add("active_workspace_id does not name a migrated workspace, current workspace left to the app default")
                else -> strings += listOf(Txt(CURRENT_WORKSPACE_ID), Txt(id.toString()))
            }
        }

        // Rows the Swift exporter does not write but Android reads from the same keys; only what Swift stored, verbatim.
        settings["BibleBookSortOrder"]?.let { value ->
            if (value == "ALPHABETICAL" || value == "BIBLE_BOOK") strings += listOf(Txt("BibleBookSortOrder"), Txt(value))
            else drops.add("BibleBookSortOrder that is neither ALPHABETICAL nor BIBLE_BOOK")
        }
        for (key in DEFAULT_DOCUMENT_KEYS) settings[key]?.takeIf { it.isNotEmpty() }?.let { strings += listOf(Txt(key), Txt(it)) }
        for (key in settings.keys) {
            if (key.startsWith("default-") && key !in DEFAULT_DOCUMENT_KEYS) drops.add("default document of an unknown category")
        }
        settings["reading_plan"]?.takeIf { it.isNotEmpty() }?.let { code ->
            if (migratedPlanCode(code, staging)) strings += listOf(Txt("reading_plan"), Txt(code))
            else drops.add("selected reading plan that was not migrated, no plan selected")
        }

        staging.settings.useWriterConnectionMarked { transactor ->
            transactor.immediateTransaction {
                for ((def, rows) in listOf(BOOLEAN to booleans, LONG to longs, STRING to strings)) {
                    for (cells in rows) usePrepared(def.insertSql) { it.bindCells(cells); it.step() }
                }
            }
        }
        for ((def, rows) in listOf(BOOLEAN to booleans, LONG to longs, STRING to strings)) report.written(category, def.table, rows.size)
        drops.flushTo(report, category)
    }

    /** The id of the migrated workspace [raw] names, or null when [raw] is not a UUID or no such workspace was staged. */
    private suspend fun migratedWorkspaceId(raw: String, workspaces: RoomDatabase): IdType? {
        val id = try { IdType.fromString(raw.trim().lowercase()) } catch (e: Exception) { return null }
        val wanted = id.toByteArray() ?: return null
        val found = workspaces.useReaderConnectionMarked { c ->
            c.usePrepared("SELECT 1 FROM Workspace WHERE id = ?") { st -> st.bindBlob(1, wanted); st.step() }
        }
        return id.takeIf { found }
    }

    /** Whether the staged reading plan database holds a plan with [code] (an unopened database holds none). */
    private suspend fun migratedPlanCode(code: String, staging: StagingDatabases): Boolean {
        val plans = staging.openedDatabase("readingplans.sqlite3") ?: return false
        return plans.useReaderConnectionMarked { c ->
            c.usePrepared("SELECT 1 FROM ReadingPlan WHERE planCode = CAST(? AS TEXT)") { st -> st.bindBlob(1, code.encodeToByteArray()); st.step() }
        }
    }

    companion object {
        const val CURRENT_WORKSPACE_ID = "current_workspace_id"
        private const val BOOKMARK_SORT_ORDER = "BookmarkSortOrder"

        /** Android's `default-<BookCategory>` keys (`CurrentPageBase.getDefaultBook`) that the Swift app writes. */
        private val DEFAULT_DOCUMENT_KEYS = listOf("default-BIBLE", "default-COMMENTARY", "default-DICTIONARY", "default-GENERAL_BOOK", "default-MAPS")

        /** Keys the system itself puts into UserDefaults: not the app's, never reported as unknown. */
        private fun isAppleSystemKey(key: String) = key.startsWith("NS") || key.startsWith("Apple") || key.startsWith("com.apple.") || key.startsWith("AK")
        private const val NOTES_CONTENT_TYPE = "notes_content_type"

        private val BOOLEAN = TableDef("BooleanSetting", "boolean setting", listOf(Col("key", Kind.TEXT, null), Col("value", Kind.INT64, null)))
        private val LONG = TableDef("LongSetting", "long setting", listOf(Col("key", Kind.TEXT, null), Col("value", Kind.INT64, null)))
        private val STRING = TableDef("StringSetting", "string setting", listOf(Col("key", Kind.TEXT, null), Col("value", Kind.TEXT, null)))

        /** The INSERT statements, exposed for the test that compares their column lists with the staged tables. */
        internal val INSERT_STATEMENTS: List<String> = listOf(BOOLEAN, LONG, STRING).map { it.insertSql }

        /** `SettingsStore.readStoredValue` for UserDefaults: a boolean or integer becomes its text, a string stays. */
        private fun userDefaultsText(value: Any?): String? = when (value) {
            is Boolean -> if (value) "true" else "false"
            is Int, is Long -> value.toString()
            is String -> value
            else -> null
        }

        /** `AppPreferenceRegistry.decodeCSVSet` then `encodeCSVSet`: trimmed, non-empty members, sorted, comma-joined. */
        internal fun normalizeCsvSet(stored: String?): String =
            stored.orEmpty().split(",").map { it.trim { c -> c.isWhitespace() } }.filter { it.isNotEmpty() }.sorted().joinToString(",")

        /** `AppPreferenceRegistry` of the Swift app at the pin, in `AppPreferenceKey.allCases` order. */
        val DEFINITIONS: List<Def> = listOf(
        Def("strongs_greek_dictionary", Storage.SWIFT_DATA, ValueType.CSV, null),
        Def("strongs_hebrew_dictionary", Storage.SWIFT_DATA, ValueType.CSV, null),
        Def("robinson_greek_morphology", Storage.SWIFT_DATA, ValueType.CSV, null),
        Def("disabled_word_lookup_dictionaries", Storage.SWIFT_DATA, ValueType.CSV, null),
        Def("navigate_to_verse_pref", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("open_links_in_special_window_pref", Storage.SWIFT_DATA, ValueType.BOOL, "true"),
        Def("screen_keep_on_pref", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("double_tap_to_fullscreen", Storage.SWIFT_DATA, ValueType.BOOL, "true"),
        Def("auto_fullscreen_pref", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("toolbar_button_actions", Storage.SWIFT_DATA, ValueType.STRING, "default"),
        Def("disable_two_step_bookmarking", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("bible_view_swipe_mode", Storage.SWIFT_DATA, ValueType.STRING, "CHAPTER"),
        Def("volume_keys_scroll", Storage.SWIFT_DATA, ValueType.BOOL, "true"),
        Def("book_grid_ltr", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("book_grid_group_by_category", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("book_grid_show_long_name", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("book_grid_show_progress", Storage.SWIFT_DATA, ValueType.BOOL, "true"),
        Def("search_selected_translations", Storage.SWIFT_DATA, ValueType.STRING, null),
        Def("search_results_strongs_translations", Storage.SWIFT_DATA, ValueType.STRING, null),
        Def("epubSearch-SearchType", Storage.SWIFT_DATA, ValueType.STRING, null),
        Def("BookmarkSortOrder", Storage.USER_DEFAULTS, ValueType.STRING, "BIBLE_ORDER"),
        Def("bookmark_show_notes", Storage.SWIFT_DATA, ValueType.BOOL, "true"),
        Def("csv_export_unchecked_columns", Storage.SWIFT_DATA, ValueType.CSV, null),
        Def("night_mode_pref3", Storage.DEVICE_LOCAL, ValueType.STRING, "system"),
        Def("locale_pref", Storage.USER_DEFAULTS, ValueType.STRING, ""),
        Def("monochrome_mode", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("disable_animations", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("disable_click_to_edit", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("notes_content_type", Storage.SWIFT_DATA, ValueType.STRING, "HTML"),
        Def("font_size_multiplier", Storage.SWIFT_DATA, ValueType.INT, "100"),
        Def("full_screen_hide_buttons_pref", Storage.SWIFT_DATA, ValueType.BOOL, "true"),
        Def("hide_window_buttons", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("hide_bible_reference_overlay", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("show_active_window_indicator", Storage.SWIFT_DATA, ValueType.BOOL, "true"),
        Def("disable_bible_bookmark_modal_buttons", Storage.SWIFT_DATA, ValueType.CSV, null),
        Def("disable_gen_bookmark_modal_buttons", Storage.SWIFT_DATA, ValueType.CSV, null),
        Def("discrete_help", Storage.ACTION, ValueType.ACTION, null),
        Def("discrete_mode", Storage.USER_DEFAULTS, ValueType.BOOL, "false"),
        Def("show_calculator", Storage.USER_DEFAULTS, ValueType.BOOL, "false"),
        Def("calculator_pin", Storage.USER_DEFAULTS, ValueType.STRING, "1234"),
        Def("experimental_features", Storage.SWIFT_DATA, ValueType.CSV, null),
        Def("enable_bluetooth_pref", Storage.SWIFT_DATA, ValueType.BOOL, "true"),
        Def("speak_autoBookmark", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("speak_synchronize", Storage.SWIFT_DATA, ValueType.BOOL, "true"),
        Def("speak_replaceDivineName", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("speak_restoreSettingsFromBookmarks", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("request_sdcard_permission_pref", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("show_errorbox", Storage.SWIFT_DATA, ValueType.BOOL, "false"),
        Def("open_links", Storage.ACTION, ValueType.ACTION, null),
        Def("crash_app", Storage.ACTION, ValueType.ACTION, null),
        )
    }
}
