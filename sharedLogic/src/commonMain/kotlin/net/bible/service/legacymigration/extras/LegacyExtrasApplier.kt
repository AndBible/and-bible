package net.bible.service.legacymigration.extras

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.bible.service.legacymigration.PrefType
import net.bible.service.legacymigration.state.atomicWriteFile
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.settings.DeviceLocalPrefs
import okio.FileSystem
import okio.Path

/** The one-time messages the user gets after a migration; shown in this order, each at most once. */
enum class LegacyNotice { ICLOUD_SYNC_MOVED, NEXTCLOUD_SYNC_OFF, NEXTCLOUD_UNSENT, BOOKMARKS_SKIPPED }

/**
 * Applies the migrated `extras.json` (device-local prefs and Nextcloud connection) and keeps the queue of one-time
 * notices. Progress lives in `applied.json` next to it (`{"beforeDatabases", "afterReady", "shown"}`), written with
 * [atomicWriteFile] without an fsync callback: the applier has no platform and every step is idempotent.
 *
 * Each apply step writes once and then records it, so a value the user changes later is never overwritten by a
 * restart. Not written here, by design: the Nextcloud password (already in the secret store), any `sync_enable_*`
 * key (sync stays off) and `gdrive_sync_interval` (no Android key). A missing or unreadable `extras.json` means
 * there is nothing to apply and no notices.
 */
class LegacyExtrasApplier(private val fs: FileSystem, private val dir: Path, private val devicePrefs: DeviceLocalPrefs) {
    private val extrasPath = dir / LegacyExtrasFile.NAME
    private val appliedPath = dir / "applied.json"

    private val extras: StoredExtras? by lazy {
        try {
            if (fs.exists(extrasPath)) LegacyExtrasFile.decode(fs.read(extrasPath) { readUtf8() }) else null
        } catch (e: Exception) {
            null
        }
    }

    private class Applied(val beforeDatabases: Boolean, val afterReady: Boolean, val shown: Set<String>)

    private fun readApplied(): Applied = try {
        if (!fs.exists(appliedPath)) Applied(false, false, emptySet())
        else {
            val o: JsonObject = Json.parseToJsonElement(fs.read(appliedPath) { readUtf8() }).jsonObject
            Applied(
                o["beforeDatabases"]?.jsonPrimitive?.booleanOrNull ?: false,
                o["afterReady"]?.jsonPrimitive?.booleanOrNull ?: false,
                (o["shown"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
            )
        }
    } catch (e: Exception) {
        Applied(false, false, emptySet())
    }

    private fun writeApplied(a: Applied) {
        atomicWriteFile(fs, appliedPath, buildJsonObject {
            put("beforeDatabases", a.beforeDatabases)
            put("afterReady", a.afterReady)
            put("shown", JsonArray(a.shown.map { JsonPrimitive(it) }))
        }.toString())
    }

    /** Device-local prefs (H7/H8) and the Nextcloud connection fields (H9); runs once. `night_mode_pref3` becomes `system` when the old app had none. */
    fun applyBeforeDatabases() {
        val e = extras ?: return
        val applied = readApplied()
        if (applied.beforeDatabases) return
        for (p in e.deviceLocalPrefs) when (p.type) {
            PrefType.STRING -> devicePrefs.setString(p.key, p.value)
            PrefType.BOOLEAN -> devicePrefs.setBoolean(p.key, p.value.toBoolean())
            // DeviceLocalPrefs has no numeric setters and none of the carried keys is numeric.
            PrefType.INT, PrefType.LONG, PrefType.FLOAT -> Unit
        }
        if (e.deviceLocalPrefs.none { it.key == NIGHT_MODE_KEY }) devicePrefs.setString(NIGHT_MODE_KEY, "system")
        e.sync?.let {
            devicePrefs.setString("cloud_sync_server_url", it.serverUrl)
            devicePrefs.setString("cloud_sync_username", it.username)
            devicePrefs.setString("cloud_sync_folder_path", it.folderPath)
        }
        writeApplied(Applied(true, applied.afterReady, applied.shown))
    }

    /** Selects the Nextcloud adapter once the settings store is ready; sync itself stays off. Runs once. */
    fun applyAfterReady(settings: AppSettings) {
        val e = extras ?: return
        val applied = readApplied()
        if (applied.afterReady) return
        if (e.sync != null) settings.setString("sync_adapter", "NEXT_CLOUD")
        writeApplied(Applied(applied.beforeDatabases, true, applied.shown))
    }

    /** The notices still to show, in enum order. */
    fun pendingNotices(): List<LegacyNotice> {
        val e = extras ?: return emptyList()
        val shown = readApplied().shown
        return LegacyNotice.entries.filter { n ->
            n.name !in shown && when (n) {
                LegacyNotice.ICLOUD_SYNC_MOVED -> e.icloudSyncWasEnabled
                LegacyNotice.NEXTCLOUD_SYNC_OFF -> e.sync != null
                LegacyNotice.NEXTCLOUD_UNSENT -> e.unsentSyncPatches
                LegacyNotice.BOOKMARKS_SKIPPED -> e.skippedUnverifiedBookmarks > 0
            }
        }
    }

    fun markShown(notice: LegacyNotice) {
        val a = readApplied()
        writeApplied(Applied(a.beforeDatabases, a.afterReady, a.shown + notice.name))
    }

    /** Bookmarks left out because their verse ordinals could not be trusted. */
    val skippedBookmarks: Int get() = extras?.skippedUnverifiedBookmarks ?: 0

    private companion object {
        const val NIGHT_MODE_KEY = "night_mode_pref3"
    }
}
