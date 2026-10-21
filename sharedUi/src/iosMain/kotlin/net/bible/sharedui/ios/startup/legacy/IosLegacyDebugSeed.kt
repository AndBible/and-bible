package net.bible.sharedui.ios.startup.legacy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedcore.platform.secrets.IosKeychainSecretStore
import net.bible.sharedui.assembly.IosDebugLaunchArgs
import net.bible.sharedui.ios.startup.LEGACY_SETTLED_KEY
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import kotlin.native.Platform

/** One typed `userDefaults` entry of a fixture's `sidecar.json`. */
internal sealed interface SeedDefault {
    val key: String
    data class Bool(override val key: String, val value: Boolean) : SeedDefault
    data class Integer(override val key: String, val value: Long) : SeedDefault
    data class Real(override val key: String, val value: Double) : SeedDefault
    data class Text(override val key: String, val value: String) : SeedDefault
}

/** One `keychain` item of a fixture's `sidecar.json`: a generic password of [service] + [account]. */
internal data class SeedKeychainItem(val service: String, val account: String, val value: String)

internal data class LegacySeedSidecar(val defaults: List<SeedDefault>, val keychain: List<SeedKeychainItem>)

/**
 * Parses a fixture `sidecar.json`. A JSON boolean stays a boolean, an integral number a [SeedDefault.Integer], any
 * other number a [SeedDefault.Real] and a string a [SeedDefault.Text]; nulls, arrays and objects are skipped (the Swift
 * app wrote none). A keychain item lacking service, account or value is skipped. Throws on malformed JSON.
 */
internal fun parseLegacySeedSidecar(text: String): LegacySeedSidecar {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: error("sidecar.json is not an object")
    val defaults = (root["userDefaults"] as? JsonObject).orEmpty().mapNotNull { (key, element) ->
        val p = element as? JsonPrimitive ?: return@mapNotNull null
        if (p is JsonNull) return@mapNotNull null
        when {
            p.isString -> SeedDefault.Text(key, p.content)
            p.content == "true" || p.content == "false" -> SeedDefault.Bool(key, p.content == "true")
            p.content.toLongOrNull() != null -> SeedDefault.Integer(key, p.content.toLong())
            p.content.toDoubleOrNull() != null -> SeedDefault.Real(key, p.content.toDouble())
            else -> null
        }
    }
    val keychain = (root["keychain"] as? JsonArray).orEmpty().mapNotNull { element ->
        val o = element as? JsonObject ?: return@mapNotNull null
        fun field(name: String) = (o[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val service = field("service") ?: return@mapNotNull null
        val account = field("account") ?: return@mapNotNull null
        val value = field("value") ?: return@mapNotNull null
        SeedKeychainItem(service, account, value)
    }
    return LegacySeedSidecar(defaults, keychain)
}

/** True for the legacy store files a seed copies: `AndBible.store*` and `LocalStore.store*` (main, `-wal`, `-shm`). */
internal fun isLegacyStoreFile(name: String): Boolean = name.startsWith("AndBible.store") || name.startsWith("LocalStore.store")

/**
 * Debug-only simulator seeding for the legacy-migration end to end (plan decision M-7): `-seedLegacyFixture <dir>`
 * makes the app itself put an unpacked fixture scenario (stores, the sidecar's UserDefaults and Keychain items) where the
 * old Swift app would have left them, and reset the new app's own migration state, so the next gates see "a fresh upgrade".
 *
 * Runs blocking, on the calling thread, from `MainViewController()` BEFORE the startup gates read `legacy_migration_settled`
 * and the discrete/locale preferences. That is acceptable only because it is debug-only and gated on the argument: it copies
 * about 1 MB and touches a few Keychain items, and a seed that finished after the first composition would race the gates.
 */
internal object IosLegacyDebugSeed {
    private const val SEEDED_KEYS = "i3b_seeded_default_keys"
    private val WELL_KNOWN_KEYS = listOf("discrete_mode", "show_calculator", "calculator_pin", "icloud_sync_enabled", "locale_pref")

    @kotlin.concurrent.Volatile
    private var done = false

    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    fun seedOnce() {
        if (!Platform.isDebugBinary) return
        val dir = IosDebugLaunchArgs.legacySeedDir() ?: return
        if (done) return // the Swift host may build the view controller more than once
        done = true
        try {
            seed(dir)
            nsLog("I3B-seed $dir")
        } catch (e: Throwable) {
            nsLog("I3B-seed FAILED $dir: ${e.stackTraceToString()}")
        }
    }

    private fun seed(dir: String) {
        val fs = FileSystem.SYSTEM
        val support = (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String).toPath()
        val source = dir.toPath()
        val sidecar = parseLegacySeedSidecar(fs.read(source / "sidecar.json") { readUtf8() })

        // 1. The new app's own migration state.
        fs.deleteRecursively(support / "LegacyMigration", mustExist = false)
        fs.deleteRecursively(support / "databases", mustExist = false)
        val defaults = NSUserDefaults.standardUserDefaults
        defaults.removeObjectForKey(LEGACY_SETTLED_KEY)

        // 2. The old stores.
        fs.createDirectories(support)
        for (existing in fs.listOrNull(support).orEmpty()) if (isLegacyStoreFile(existing.name)) fs.delete(existing)
        for (file in fs.list(source)) if (isLegacyStoreFile(file.name)) fs.copy(file, support / file.name)

        // 3. UserDefaults: forget what an earlier seed wrote, then write this scenario's entries with their JSON types.
        @Suppress("UNCHECKED_CAST")
        val previous = (defaults.stringArrayForKey(SEEDED_KEYS) as? List<String>).orEmpty()
        for (key in previous + WELL_KNOWN_KEYS) defaults.removeObjectForKey(key)
        for (entry in sidecar.defaults) when (entry) {
            is SeedDefault.Bool -> defaults.setBool(entry.value, forKey = entry.key)
            is SeedDefault.Integer -> defaults.setInteger(entry.value, forKey = entry.key)
            is SeedDefault.Real -> defaults.setDouble(entry.value, forKey = entry.key)
            is SeedDefault.Text -> defaults.setObject(entry.value, forKey = entry.key)
        }
        defaults.setObject(sidecar.defaults.map { it.key }, forKey = SEEDED_KEYS)
        defaults.synchronize()

        // 4. Keychain: delete a stale item first, then add. `IosKeychainSecretStore(service)` is the same plain generic
        // password (service + account, no access group) that `IosLegacyPlatform.legacyKeychain` queries.
        for (item in sidecar.keychain) {
            val store = IosKeychainSecretStore(item.service)
            store.remove(item.account)
            store.put(item.account, item.value)
        }
    }
}
