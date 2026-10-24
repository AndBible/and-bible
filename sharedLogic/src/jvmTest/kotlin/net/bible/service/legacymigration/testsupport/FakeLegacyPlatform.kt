package net.bible.service.legacymigration.testsupport

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.bible.service.legacymigration.LegacyPlatform
import net.bible.sharedcore.platform.secrets.SecretStore
import okio.FileSystem
import okio.Path

/** An in-memory [SecretStore] for tests. */
class InMemorySecretStore : SecretStore {
    private val values = linkedMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/**
 * A [LegacyPlatform] over the real file system, built from a fixture directory and its `sidecar.json`
 * (`userDefaults` object and `keychain` list of `service`/`account`/`value`). The platform directories are created below
 * `platform/` of the fixture (a private temp copy): `ApplicationSupport`, `Documents` and `databases`. Pass
 * [userDefaults] or [keychain] to replace what the sidecar says.
 */
class FakeLegacyPlatform(
    fixtureRoot: Path,
    private val userDefaults: Map<String, Any?> = emptyMap(),
    private val keychain: Map<Pair<String, String>, String> = emptyMap(),
) : LegacyPlatform {
    override val fileSystem: FileSystem = FileSystem.SYSTEM
    private val base: Path = (fixtureRoot / "platform").also { File(it.toString()).mkdirs() }
    override val applicationSupport: Path = (base / "ApplicationSupport").also { File(it.toString()).mkdirs() }
    override val documents: Path = (base / "Documents").also { File(it.toString()).mkdirs() }
    override val databasesDir: Path = (base / "databases").also { File(it.toString()).mkdirs() }
    override val secrets: SecretStore = InMemorySecretStore()

    override fun userDefaults(): Map<String, Any?> = userDefaults
    override fun legacyKeychain(service: String, account: String): String? = keychain[service to account]

    companion object {
        /** Builds the platform of [fixture] from its `sidecar.json`. */
        fun from(fixture: FixtureDir): FakeLegacyPlatform {
            val sidecar = Json.parseToJsonElement(File(fixture.root.toFile(), "sidecar.json").readText()).jsonObject
            val defaults = (sidecar["userDefaults"] as? JsonObject).orEmpty().mapValues { (_, v) -> plain(v) }
            val keychain = (sidecar["keychain"] as? JsonArray).orEmpty().associate {
                val o = it.jsonObject
                (o.getValue("service").jsonPrimitive.content to o.getValue("account").jsonPrimitive.content) to o.getValue("value").jsonPrimitive.content
            }
            return FakeLegacyPlatform(fixture.root, defaults, keychain)
        }

        private fun plain(v: kotlinx.serialization.json.JsonElement): Any? = when {
            v is JsonPrimitive && v.isString -> v.content
            v is JsonPrimitive -> v.booleanOrNull ?: v.longOrNull ?: v.doubleOrNull
            v is JsonArray -> v.map { plain(it) }
            else -> null
        }
    }
}
