package net.bible.service.legacymigration.extras

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.bible.service.legacymigration.DeviceLocalPref
import net.bible.service.legacymigration.LegacyExtras
import net.bible.service.legacymigration.PrefType
import net.bible.service.legacymigration.map.LegacyCategory
import net.bible.service.legacymigration.map.MigrationReport
import net.bible.service.legacymigration.map.ReportEntry

/** The Nextcloud connection as persisted in `extras.json`: everything of `LegacySyncSettings` except the password itself. */
data class StoredSync(
    val adapter: String,
    val serverUrl: String,
    val username: String,
    val folderPath: String,
    val interval: String?,
    val enabledCategories: Set<String>,
    val hadPassword: Boolean,
)

/**
 * What the second migration part (I3b) reads from `extras.json` after the migration was committed. Holds no secret: the
 * API keys and the Nextcloud password are in the secret store, [StoredSync.hadPassword] only says one was there.
 * [skippedUnverifiedBookmarks] is the number of bookmarks left out because their verse ordinals could not be trusted.
 */
data class StoredExtras(
    val sync: StoredSync?,
    val icloudSyncWasEnabled: Boolean,
    val unsentSyncPatches: Boolean,
    val deviceLocalPrefs: List<DeviceLocalPref>,
    val skippedUnverifiedBookmarks: Int,
)

/** Reads and writes `extras.json` in the migration directory. It is written before the commit point and survives the clean-up. */
object LegacyExtrasFile {
    const val NAME = "extras.json"

    private const val VERSION = 1

    /** Serialises [extras] and the relevant facts of [report]; never the API keys or the password. */
    fun encode(extras: LegacyExtras, report: MigrationReport): String {
        val skipped = report.entries.filterIsInstance<ReportEntry.Skipped>()
            .filter { it.category == LegacyCategory.BOOKMARKS && it.reason.startsWith("ordinal trust") }
            .sumOf { it.rows }
        return buildJsonObject {
            put("version", VERSION)
            put("sync", extras.sync?.let { s ->
                buildJsonObject {
                    put("adapter", s.adapter)
                    put("serverUrl", s.serverUrl)
                    put("username", s.username)
                    put("folderPath", s.folderPath)
                    put("interval", s.interval?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("enabledCategories", JsonArray(s.enabledCategories.map { JsonPrimitive(it) }))
                    put("hadPassword", s.password != null)
                }
            } ?: JsonNull)
            put("icloudSyncWasEnabled", extras.icloudSyncWasEnabled)
            put("unsentSyncPatches", extras.unsentSyncPatches)
            put("deviceLocalPrefs", JsonArray(extras.deviceLocalPrefs.map {
                buildJsonObject { put("key", it.key); put("type", it.type.name); put("value", it.value) }
            }))
            put("skippedUnverifiedBookmarks", skipped)
        }.toString()
    }

    /** Parses [encode]'s output; throws on anything malformed. */
    fun decode(text: String): StoredExtras {
        val o: JsonObject = Json.parseToJsonElement(text).jsonObject
        val sync = (o["sync"] as? JsonObject)?.let { s ->
            StoredSync(
                adapter = s.getValue("adapter").jsonPrimitive.content,
                serverUrl = s.getValue("serverUrl").jsonPrimitive.content,
                username = s.getValue("username").jsonPrimitive.content,
                folderPath = s.getValue("folderPath").jsonPrimitive.content,
                interval = s["interval"]?.jsonPrimitive?.contentOrNull,
                enabledCategories = s.getValue("enabledCategories").jsonArray.mapTo(LinkedHashSet()) { it.jsonPrimitive.content },
                hadPassword = s.getValue("hadPassword").jsonPrimitive.booleanOrNull!!,
            )
        }
        return StoredExtras(
            sync = sync,
            icloudSyncWasEnabled = o.getValue("icloudSyncWasEnabled").jsonPrimitive.booleanOrNull!!,
            unsentSyncPatches = o.getValue("unsentSyncPatches").jsonPrimitive.booleanOrNull!!,
            deviceLocalPrefs = o.getValue("deviceLocalPrefs").jsonArray.map {
                val p = it.jsonObject
                DeviceLocalPref(p.getValue("key").jsonPrimitive.content, PrefType.valueOf(p.getValue("type").jsonPrimitive.content), p.getValue("value").jsonPrimitive.content)
            },
            skippedUnverifiedBookmarks = o.getValue("skippedUnverifiedBookmarks").jsonPrimitive.intOrNull!!,
        )
    }
}
