package net.bible.service.legacymigration.map

/** Where a legacy key lives. */
enum class LedgerSource { SETTING, USER_DEFAULTS, KEYCHAIN }

/**
 * What the migration does with a key. [MAPPED] covers keys the migration writes (the target says where) and keys
 * that deliberately stay where they are (device-local preferences that keep their Android name, section 6.3 of the
 * spec). [DROPPED] keys are never carried over, for the stated reason. [I3B] keys are carried by [LegacyExtras] and
 * applied by the second migration part (sync settings, Nextcloud credentials).
 */
enum class Disposition { MAPPED, DROPPED, I3B }

/**
 * One row of the key ledger: an exact [keyOrPrefix] or, with [isPrefix], every key starting with it.
 * [target] says where a mapped key goes; [reason] explains a dropped or deferred one.
 */
data class LedgerEntry(
    val keyOrPrefix: String,
    val isPrefix: Boolean,
    val source: LedgerSource,
    val disposition: Disposition,
    val target: String,
    val reason: String,
)

/**
 * The key ledger of the Swift app at the pinned commit: every `Setting` key (or prefix) and every UserDefaults key it
 * writes has one row, so that a key nobody decided about shows up (see `SettingsMapper`, which reports unknown keys).
 * A test re-derives the key list from the pinned Swift sources and fails on a key the ledger does not classify.
 *
 * [classify] returns the most specific row (an exact key beats a prefix, a longer prefix beats a shorter one).
 *
 * Deviation from the task brief, deliberate: the brief says every `remote_sync.*` prefix is dropped; the prefixes whose
 * rows other mappers consume (the fidelity rows, the preserved Android bookmark book and playback settings, the
 * preserved reading plan statuses and start dates) are `MAPPED` here, because the Swift exporter merges them into the
 * target rows and the workspace, bookmark and reading plan mappers do the same. All other `remote_sync` keys are
 * `DROPPED` with the brief's reason.
 */
object KeyLedger {
    private const val SYNC_REASON = "sync re-enabled by the user, U2"
    private const val STAYS = "NSUserDefaults key of the same name, survives in place (section 6.3); not migrated by I3a"

    private fun setting(key: String, target: String, prefix: Boolean = false) =
        LedgerEntry(key, prefix, LedgerSource.SETTING, Disposition.MAPPED, target, "")

    private fun defaults(key: String, target: String) =
        LedgerEntry(key, false, LedgerSource.USER_DEFAULTS, Disposition.MAPPED, target, "")

    private fun dropped(key: String, reason: String, prefix: Boolean = false, source: LedgerSource = LedgerSource.SETTING) =
        LedgerEntry(key, prefix, source, Disposition.DROPPED, "", reason)

    private fun i3b(key: String, target: String, prefix: Boolean = false, source: LedgerSource = LedgerSource.SETTING) =
        LedgerEntry(key, prefix, source, Disposition.I3B, target, "Nextcloud sync settings, applied by I3B with sync left off")

    /** The registered application preferences the Swift app keeps in SwiftData (the `Setting` table). */
    private val registeredSettings: List<LedgerEntry> =
        SettingsMapper.DEFINITIONS.filter { it.storage == SettingsMapper.Storage.SWIFT_DATA }
            .map { setting(it.key, "SettingsDatabase.${it.type.table}") }

    val entries: List<LedgerEntry> = buildList {
        addAll(registeredSettings)

        // Registered preferences stored in UserDefaults.
        add(defaults("BookmarkSortOrder", "SettingsDatabase.StringSetting (UserDefaults value, default BIBLE_ORDER)"))
        for (key in listOf("locale_pref", "discrete_mode", "show_calculator", "calculator_pin")) add(defaults(key, STAYS))
        add(defaults("AppleLanguages", STAYS))
        // Action rows of the Settings screen: no stored value.
        for (key in listOf("discrete_help", "open_links", "crash_app")) {
            add(dropped(key, "action row of the Settings screen, no stored value", source = LedgerSource.USER_DEFAULTS))
        }

        // Rows other mappers consume.
        add(setting("active_workspace_id", "SettingsDatabase.StringSetting current_workspace_id (only when the workspace was migrated)"))
        add(setting("global_text_display_settings", "WorkspaceDatabase GlobalTextDisplaySettings"))
        add(setting("reading_progress_state_v1", "ProgressDatabase"))
        add(setting("memorization_progress_state_v1", "ProgressDatabase"))
        add(setting("remote_sync.workspaces.fidelity.", "WorkspaceDatabase rows (merged by WorkspacesMapper)", prefix = true))
        add(setting("remote_sync.bookmarks.android_book.", "BookmarkDatabase rows (merged by BookmarksMapper)", prefix = true))
        add(setting("remote_sync.bookmarks.android_playback_settings.", "BookmarkDatabase rows (merged by BookmarksMapper)", prefix = true))
        add(setting("remote_sync.readingplans.android_status", "ReadingPlanDatabase rows (merged by ReadingPlansMapper)", prefix = true))
        add(setting("remote_sync.readingplans.start_date_millis", "ReadingPlanDatabase rows (merged by ReadingPlansMapper)", prefix = true))

        // Sync: connection settings go to I3B through LegacyExtras.
        add(i3b("sync_adapter", "LegacySyncSettings.adapter"))
        add(i3b("gdrive_server_url", "LegacySyncSettings.serverUrl"))
        add(i3b("gdrive_username", "LegacySyncSettings.username"))
        add(i3b("gdrive_folder_path", "LegacySyncSettings.folderPath"))
        add(i3b("gdrive_sync_interval", "LegacySyncSettings.interval"))
        add(i3b("sync_enable_", "LegacySyncSettings.enabledCategories", prefix = true))
        add(i3b("gdrive_llmprocessing", "LegacySyncSettings.enabledCategories (legacy AI toggle)"))
        add(i3b("gdrive_password", "LegacySyncSettings.password", source = LedgerSource.KEYCHAIN))
        add(LedgerEntry("llm_api_key_", true, LedgerSource.KEYCHAIN, Disposition.MAPPED, "new SecretStore apiKeyName(id) (adopter)", "API key, written to the new secret store by the adopter"))
        add(defaults("icloud_sync_enabled", "LegacyExtras.icloudSyncWasEnabled (only for the one-time iCloud notice)"))
        // Legacy per-category toggles `gdrive_<category>` (read-only fallback of the Swift app).
        add(i3b("gdrive_", "LegacySyncSettings.enabledCategories (legacy category toggles)", prefix = true))

        // Sync bookkeeping and device-local sync state.
        for (prefix in listOf("remote_sync.", "remote_sync_")) add(dropped(prefix, SYNC_REASON, prefix = true))
        add(dropped("remote_sync", SYNC_REASON))
        add(dropped("globalLastSynchronized", SYNC_REASON))

        // Playback state and per-device UI state: not part of the Swift Android export.
        val notExported = "not exported by the Swift backup (device-local playback state)"
        for (key in listOf("lastSpeakBook", "lastSpeakRef", "SpeakLastProviderCheckpoint", "SpeakProviderCheckpoint",
            "SpeakBibleBook", "SpeakBibleProvider", "SpeakBibleVerse", "SpeakGenBook", "SpeakGenKey", "SpeakLocale", "speak_speed")) {
            add(dropped(key, notExported))
        }
        val uiState = "per-device UI state, not exported by the Swift backup"
        add(dropped("recent_labels", uiState))
        // Android reads the same key, but the format of the Swift payload was not verified against Android's LastTypesSerializer.
        add(dropped("lastDisplaySettings", "Android reads the same key; Swift payload format not verified (follow-up)"))
        // Rows Android reads under the same key, mapped only when Swift stored them (SettingsMapper).
        add(setting("reading_plan", "SettingsDatabase.StringSetting reading_plan (only when that plan was migrated)"))
        add(setting("default-", "SettingsDatabase.StringSetting, same key (the five default-<category> keys)", prefix = true))
        add(setting("BibleBookSortOrder", "SettingsDatabase.StringSetting (ALPHABETICAL or BIBLE_BOOK)"))
        // Device-local Android preferences (SharedPreferences) that Swift kept in SwiftData: carried by LegacyExtras.
        val deviceLocal = "extras.deviceLocalPrefs (applied by I3b/adopter to SharedPreferences)"
        add(setting("night_mode_pref3", deviceLocal))
        add(setting("night_mode", "$deviceLocal as night_mode_pref (boolean)"))
        add(setting("SpeakSettings", deviceLocal))
        for (key in listOf(
            "search-last-used", "speak-last-used", "reading_progress_last_tab", "reading_progress_mem_overview",
            "labels_list_filter_searchInsideTextButtonActive", "labels_list_search_mode", "backup_workflow_backup_target",
            "backup_workflow_restore_target", "downloads.selectedDocumentFilterIndex", "_uitest_consumed_preference_seed_session",
        )) {
            add(dropped(key, uiState, source = LedgerSource.USER_DEFAULTS))
        }
    }

    private val exact: Map<String, LedgerEntry> = entries.filter { !it.isPrefix }.associateBy { it.keyOrPrefix }
    private val prefixes: List<LedgerEntry> = entries.filter { it.isPrefix }.sortedByDescending { it.keyOrPrefix.length }

    /** The ledger row that decides [key], or null when the ledger does not know it. */
    fun classify(key: String): LedgerEntry? = exact[key] ?: prefixes.firstOrNull { key.startsWith(it.keyOrPrefix) }
}
