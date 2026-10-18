package net.bible.service.legacymigration

import net.bible.android.database.IdType
import net.bible.service.legacymigration.map.LegacyCategory
import net.bible.service.legacymigration.map.MigrationReport
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.read.LegacyValue
import net.bible.service.llm.apiKeyName
import okio.Path

/**
 * The Nextcloud connection of the old app, carried to the second migration part (I3B). Sync stays off after the
 * migration (U2); [enabledCategories] are the `RemoteSyncCategory` raw values the user had switched on (`bookmarks`,
 * `workspaces`, `readingplans`, `mydocuments`, `progress`, `ai_settings`), [interval] the stored seconds (null: the app
 * default), [password] the Keychain `gdrive_password` (null when absent).
 */
data class LegacySyncSettings(
    val adapter: String,
    val serverUrl: String,
    val username: String,
    val folderPath: String,
    val interval: String?,
    val enabledCategories: Set<String>,
    val password: String?,
) {
    /** The generated `toString` would print the password; this one only says whether there is one. */
    override fun toString(): String =
        "LegacySyncSettings(adapter=$adapter, serverUrl=$serverUrl, username=$username, folderPath=$folderPath, interval=$interval, " +
            "enabledCategories=$enabledCategories, password=${if (password == null) "none" else "<redacted>"})"
}

/** The value type of an Android `SharedPreferences` entry. */
enum class PrefType { STRING, BOOLEAN, INT, LONG, FLOAT }

/**
 * A device-local Android preference (it lives in `CommonUtils.realSharedPreferences`, not in the `SettingsDatabase`) that
 * the Swift app kept in SwiftData. [value] is the text form of the value of [type] (`true`/`false` for a boolean). The
 * adopter applies these to the SharedPreferences.
 */
data class DeviceLocalPref(val key: String, val type: PrefType, val value: String)

/**
 * Everything the migration carries outside the staged databases: the AI API keys by provider id (the adopter stores
 * them in the new secret store under `apiKeyName(id)`), the Nextcloud settings, whether CloudKit sync was on (for the
 * one-time iCloud notice), whether the old sync outbox held unsent patches (for the Nextcloud notice) and the user's
 * own reading plan files, and the device-local preferences ([deviceLocalPrefs]) that Android keeps in SharedPreferences.
 * The secrets in it never appear in `toString`.
 */
data class LegacyExtras(
    val aiKeys: Map<IdType, String>,
    val sync: LegacySyncSettings?,
    val icloudSyncWasEnabled: Boolean,
    val unsentSyncPatches: Boolean,
    val userPlanFiles: List<Path>,
    val deviceLocalPrefs: List<DeviceLocalPref> = emptyList(),
) {
    /** The generated `toString` would print the API keys; this one prints the provider ids and counts only. */
    override fun toString(): String =
        "LegacyExtras(aiKeys=${aiKeys.keys} (values redacted), sync=$sync, icloudSyncWasEnabled=$icloudSyncWasEnabled, " +
            "unsentSyncPatches=$unsentSyncPatches, userPlanFiles=${userPlanFiles.size} files, deviceLocalPrefs=$deviceLocalPrefs)"
}

private const val AI_SERVICE = "org.andbible.BibleCore.AICredentials"
private const val SYNC_SERVICE = "org.andbible.remote-sync"
private val SYNC_CATEGORIES = listOf("bookmarks", "workspaces", "readingplans", "mydocuments", "progress", "ai_settings")

/**
 * Collects the [LegacyExtras] of [snapshot] and [platform]. Reads only; never writes the new secret store.
 *
 * Ports of `AICredentialStore.credential(for:)` (account `llm_api_key_<lower-case uuid>`, an empty value is no key),
 * `RemoteSyncSettingsStore` (`sync_adapter`, `gdrive_server_url`, `gdrive_username`, `gdrive_folder_path`,
 * `gdrive_sync_interval`, `sync_enable_<category>` with the legacy `gdrive_<category>` fallback and Keychain
 * `gdrive_password`; the connection counts only when the adapter is `NEXT_CLOUD` and both the server URL and the
 * username are non-empty, as `loadWebDAVConfiguration`) and `UserDefaults.bool(forKey: "icloud_sync_enabled")`.
 *
 * Where the old app kept things: the unsent sync patches (and pending initial uploads in `RemoteSyncInitialUploads`) are files below `Application Support/AndBible/RemoteSyncOutbox/<one
 * directory per category>` (the directory names differ per category in the Swift sources: `bookmarks`, `Workspaces`,
 * `ReadingPlans`, `mydocuments`, `Progress`, `ai-settings`), so any regular file under that directory counts. User plan
 * files are the files directly in `Documents/jsword/readingplan`.
 */
fun buildExtras(snapshot: LegacySnapshot, platform: LegacyPlatform, report: MigrationReport): LegacyExtras {
    val aiKeys = LinkedHashMap<IdType, String>()
    for (row in snapshot.cloud.rows("LLMProviderConfig")) {
        val raw = (row.values["id"] as? LegacyValue.UuidV)?.value
        if (raw == null) {
            report.skipped(LegacyCategory.SETTINGS, "AI provider without a UUID id, its API key was not read", 1)
            continue
        }
        val id = IdType.fromString(raw.lowercase())
        val account = apiKeyName(id)
        val key = platform.legacyKeychain(AI_SERVICE, account)
        if (!key.isNullOrEmpty()) aiKeys[id] = key
    }

    val settings = snapshot.settings
    val adapter = settings["sync_adapter"]?.takeIf { it.isNotEmpty() }
    val serverUrl = settings["gdrive_server_url"].orEmpty().trim()
    val username = settings["gdrive_username"].orEmpty().trim()
    val sync = if (adapter == "NEXT_CLOUD" && serverUrl.isNotEmpty() && username.isNotEmpty()) {
        val rawInterval = settings["gdrive_sync_interval"]?.trim()
        val interval = rawInterval?.takeIf { (it.toLongOrNull() ?: -1) >= 0 }
        if (rawInterval != null && interval == null) report.skipped(LegacyCategory.SETTINGS, "invalid sync interval, app default used", 1)
        LegacySyncSettings(
            adapter = adapter,
            serverUrl = serverUrl,
            username = username,
            folderPath = settings["gdrive_folder_path"].orEmpty().trim(),
            interval = interval,
            enabledCategories = SYNC_CATEGORIES.filterTo(LinkedHashSet()) { isSyncEnabled(settings, it) },
            password = platform.legacyKeychain(SYNC_SERVICE, "gdrive_password")?.takeIf { it.isNotEmpty() },
        )
    } else {
        null
    }

    return LegacyExtras(
        aiKeys = aiKeys,
        sync = sync,
        icloudSyncWasEnabled = boolValue(platform.userDefaults()["icloud_sync_enabled"]),
        unsentSyncPatches = hasUnsentPatches(platform, report),
        userPlanFiles = userPlanFiles(platform),
        deviceLocalPrefs = deviceLocalPrefs(settings),
    )
}

/** `RemoteSyncSettingsStore.isSyncEnabled`: the current key decides when present, else the legacy `gdrive_` key. */
private fun isSyncEnabled(settings: Map<String, String>, category: String): Boolean {
    settings["sync_enable_$category"]?.let { return it == "true" }
    val legacy = if (category == "ai_settings") "gdrive_llmprocessing" else "gdrive_$category"
    return settings[legacy] == "true"
}

/** `UserDefaults.bool(forKey:)`: a boolean, a non-zero number or a string such as `YES`/`true`. */
private fun boolValue(value: Any?): Boolean = when (value) {
    is Boolean -> value
    is Number -> value.toDouble() != 0.0
    is String -> value.lowercase().let { it == "yes" || it == "true" || it == "1" }
    else -> false
}

/**
 * Unsent sync work of the old app: any regular file in the per-category outbox directories below
 * `Application Support/AndBible/RemoteSyncOutbox`, or in `Application Support/RemoteSyncInitialUploads` (prepared
 * initial uploads, `<category>-pending.sqlite3.gz` plus metadata, `RemoteSyncInitialBackupUploadService.defaultRetryDirectory`).
 * A directory that exists but cannot be listed is reported and counted as unsent (the notice errs on the safe side).
 */
private fun hasUnsentPatches(platform: LegacyPlatform, report: MigrationReport): Boolean {
    val fs = platform.fileSystem
    val directories = listOf(
        platform.applicationSupport / "AndBible" / "RemoteSyncOutbox",
        platform.applicationSupport / "RemoteSyncInitialUploads",
    )
    for (directory in directories) {
        if (!fs.exists(directory)) continue
        val unsent = try {
            fs.listRecursively(directory).any { fs.metadataOrNull(it)?.isRegularFile == true }
        } catch (e: Exception) {
            report.skipped(LegacyCategory.SETTINGS, "sync outbox could not be listed, treated as holding unsent patches", 1)
            true
        }
        if (unsent) return true
    }
    return false
}

/**
 * The Swift `Setting` rows that Android keeps in `realSharedPreferences` (`AppSettingsServiceImpl.useRealShared`,
 * `ScreenSettings`, `AndroidSpeakSettingsStore`), taken only when the Swift app stored them:
 * `night_mode_pref3` (string `system`/`automatic`/`manual`, same key and values), the manual night mode toggle `night_mode`
 * to Android's boolean `night_mode_pref`, and `SpeakSettings` (the Android-shaped JSON `SpeakSettings.androidJSON()` writes,
 * a string under the same key, carried verbatim). The other SharedPreferences keys of Android (`discrete_mode`,
 * `show_calculator`, `calculator_pin`, `locale_pref`) stay in NSUserDefaults (section 6.3).
 */
private fun deviceLocalPrefs(settings: Map<String, String>): List<DeviceLocalPref> = buildList {
    settings["night_mode_pref3"]?.let { add(DeviceLocalPref("night_mode_pref3", PrefType.STRING, it)) }
    settings["night_mode"]?.let { add(DeviceLocalPref("night_mode_pref", PrefType.BOOLEAN, if (it == "true") "true" else "false")) }
    settings["SpeakSettings"]?.takeIf { it.isNotEmpty() }?.let { add(DeviceLocalPref("SpeakSettings", PrefType.STRING, it)) }
}

private fun userPlanFiles(platform: LegacyPlatform): List<Path> {
    val fs = platform.fileSystem
    val dir = platform.documents / "jsword" / "readingplan"
    return (fs.listOrNull(dir) ?: emptyList()).filter { fs.metadataOrNull(it)?.isRegularFile == true }.sortedBy { it.name }
}
