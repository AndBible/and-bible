package net.bible.service.legacymigration.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.bible.android.database.CustomRepository
import net.bible.service.legacymigration.read.LegacySnapshot
import net.bible.service.legacymigration.stage.StagingDatabases
import okio.FileSystem
import okio.Path

/**
 * Migrates the user's custom repositories into the current `RepoDatabase`.
 *
 * The Swift app keeps no repository rows in its stores: custom sources live in `Documents/sword_install`, in
 * `InstallMgr.conf` (SWORD rows) and the sidecar `CustomRepositories.json` ([customRepositoriesJson], the metadata of
 * SWORD rows plus the MyBible rows). Port of `AndroidDatabaseBackupRepositoryMapper.writeDatabase` fed by
 * `RepositorySourceManager.loadSources` (config rows enriched by the sidecar records, source-only custom rows
 * backfilled as `sword-https` records with the `catalogDirectory/packages` package directory, MyBible records that are
 * not in the config appended), without the built-in sources. `InstallMgr.conf` is read from the directory of the JSON
 * file (so it is only consulted when [customRepositoriesJson] is given; a missing file means no config rows, as
 * Swift writes the defaults then). Row fields are Swift's `row(from:)`: description falls back to the source URL and then
 * the name, the package directory to the empty string.
 *
 * Differences, all deliberate and reported in the [MigrationReport]: an undecodable sidecar yields no records; a
 * record that fails Swift's `validateSourceFields` is dropped; a custom source whose type is neither `sword-https` nor
 * `mybible-https` (an FTP row) is skipped because Android cannot import it; a repeated name (unique index) keeps the
 * first. Swift's `URL(string:)` is approximated by a scheme-and-host check, so a manifest URL is kept as the stored text.
 * The staging database is only created when there is a row to write.
 */
class RepositoriesMapper(
    private val customRepositoriesJson: Path?,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : CategoryMapper {
    override val category = LegacyCategory.REPOSITORIES

    override suspend fun map(snapshot: LegacySnapshot, staging: StagingDatabases, report: MigrationReport) {
        val rows = build(report)
        if (rows.isEmpty()) return
        staging.repositories.customRepositoryDao().insert(rows)
        report.written(category, "CustomRepository", rows.size)
    }

    /** One source as `SourceConfig` models it. */
    private class Source(
        val name: String, val type: String, val host: String, val catalogPath: String, val repositoryType: String,
        val description: String?, val packageDirectory: String?, val manifestUrl: String?, val sourceUrl: String?,
    )

    /** The target rows, in Swift's order. Dropped records and sources are counted in [report]. */
    internal fun build(report: MigrationReport): List<CustomRepository> {
        val json = customRepositoriesJson ?: return emptyList()
        val drops = Drops()
        val records = loadRecords(json, drops)
        val persisted = loadConfig(json.parent?.div(CONFIG_FILE))

        val recordNames = records.map { it.name }.toSet()
        val seen = recordNames.toMutableSet()
        val backfilled = persisted.mapNotNull { source ->
            if (source.type != "HTTP" || source.name in DEFAULT_NAMES || source.name in seen) return@mapNotNull null
            val url = "https://${source.host}${source.catalogPath}"
            if (!urlLike(url)) return@mapNotNull null
            seen += source.name
            val packages = (source.catalogPath.ifEmpty { "/" }.let { if (it.endsWith("/")) it else "$it/" }) + "packages"
            Source(source.name, "HTTP", source.host, source.catalogPath, SWORD, url, normalizedPath(packages).ifEmpty { null }, url, url)
                .takeIf { isValid(it) }
        }
        val allRecords = records + backfilled
        val byName = HashMap<String, Source>().also { map -> for (r in allRecords) if (r.name !in map) map[r.name] = r }

        val namesInConfig = HashSet<String>()
        val configSources = persisted.map { source ->
            namesInConfig += source.name
            val record = byName[source.name]?.takeIf { it.repositoryType == SWORD }
            if (record == null) source else Source(
                source.name, source.type, source.host, source.catalogPath, record.repositoryType, record.description,
                record.packageDirectory, record.manifestUrl, record.sourceUrl,
            )
        }
        val myBible = allRecords.filter { it.repositoryType == MYBIBLE && it.name !in namesInConfig }

        val rows = ArrayList<CustomRepository>()
        val usedNames = HashSet<String>()
        for (source in configSources + myBible) {
            if (source.name in DEFAULT_NAMES) continue
            if (source.repositoryType != SWORD && source.repositoryType != MYBIBLE) {
                drops.add("custom repository of an unsupported type (${source.repositoryType})"); continue
            }
            if (!usedNames.add(source.name)) { drops.add("custom repository with a name already in use"); continue }
            rows += CustomRepository(
                name = source.name,
                description = source.description ?: source.sourceUrl ?: source.name,
                type = source.repositoryType,
                host = source.host,
                catalogDirectory = source.catalogPath,
                packageDirectory = source.packageDirectory ?: "",
                manifestUrl = source.manifestUrl,
            )
        }
        drops.flushTo(report, category)
        return rows
    }

    /** `loadCustomRepositoryRecordStore`: valid sidecar records; an undecodable file gives none (counted). */
    private fun loadRecords(path: Path, drops: Drops): List<Source> {
        if (!fileSystem.exists(path)) return emptyList()
        val array = try {
            val root = Json.parseToJsonElement(fileSystem.read(path) { readUtf8() }) as JsonObject
            root.getValue("version") as JsonPrimitive
            root.getValue("repositories") as JsonArray
        } catch (e: Exception) {
            drops.add("CustomRepositories.json could not be decoded, custom repositories not migrated"); return emptyList()
        }
        val records = try {
            array.map { element ->
                val o = element as JsonObject
                fun text(key: String): String = (o.getValue(key) as JsonPrimitive).also { require(it.isString) }.content
                Source(
                    text("name"), "HTTP", text("host"), text("catalogDirectory"), text("type"), text("description"),
                    normalizedPath(text("packageDirectory")).ifEmpty { null }, text("manifestURL").takeIf { urlLike(it) }, text("sourceURL").takeIf { urlLike(it) },
                )
            }
        } catch (e: Exception) {
            drops.add("CustomRepositories.json could not be decoded, custom repositories not migrated"); return emptyList()
        }
        return records.filter { r ->
            val ok = (r.repositoryType == SWORD || r.repositoryType == MYBIBLE) && isValid(r)
            if (!ok) drops.add("invalid custom repository record dropped")
            ok
        }
    }

    /** `parseSourceLine` over `InstallMgr.conf`: `HTTPSource=`/`FTPSource=` rows with at least three `|`-separated fields. */
    private fun loadConfig(path: Path?): List<Source> {
        if (path == null || !fileSystem.exists(path)) return emptyList()
        val text = try { fileSystem.read(path) { readUtf8() } } catch (e: Exception) { return emptyList() }
        return text.split(Regex("\r\n|\r|\n")).mapNotNull { line ->
            val trimmed = line.trim { it == ' ' || it == '\t' }
            val type = when {
                trimmed.startsWith("HTTPSource=") -> "HTTP"
                trimmed.startsWith("FTPSource=") -> "FTP"
                else -> return@mapNotNull null
            }
            val parts = trimmed.substringAfter("=").split("|")
            if (parts.size < 3) null
            else Source(parts[0], type, parts[1], parts[2], if (type == "FTP") "ftp" else SWORD, null, null, null, null)
        }
    }

    /** `validateSourceFields`. */
    private fun isValid(s: Source): Boolean {
        fun hasSeparator(v: String) = '|' in v || '\n' in v || '\r' in v
        if (s.type != "HTTP" || s.name.isEmpty() || s.host.isEmpty() || hasSeparator(s.name) || '/' in s.name || '\\' in s.name ||
            hasSeparator(s.host) || hasSeparator(s.catalogPath)) return false
        if (s.repositoryType.isEmpty() || hasSeparator(s.repositoryType)) return false
        val manifest = s.manifestUrl?.let { parseUrl(it) }
        return when {
            s.manifestUrl != null -> manifest != null && manifest.first.lowercase() == "https" && manifest.second.isNotEmpty()
            else -> s.repositoryType != MYBIBLE
        }
    }

    private companion object {
        const val CONFIG_FILE = "InstallMgr.conf"
        const val SWORD = "sword-https"
        const val MYBIBLE = "mybible-https"

        /** `InstallManager.isDefaultSourceName`: Android's built-in repositories plus the legacy FTP CrossWire row. */
        val DEFAULT_NAMES = setOf(
            "CrossWire", "Crosswire Beta", "AndBible Extra", "AndBible", "AndBible Beta", "IBT",
            "Wycliffe (CrossWire)", "eBible", "Lockman (CrossWire)", "STEP Bible (Tyndale)",
        )

        /** `normalizedCatalogDirectory`: trimmed, with a leading slash unless empty. */
        fun normalizedPath(raw: String): String {
            val trimmed = raw.trim { it.isWhitespace() }
            return if (trimmed.isEmpty() || trimmed.startsWith("/")) trimmed else "/$trimmed"
        }

        /** `URL(string:)` is non-nil: a non-empty text without whitespace (approximation). */
        fun urlLike(text: String) = text.isNotEmpty() && text.none { it.isWhitespace() }

        private val URL_PATTERN = Regex("^([A-Za-z][A-Za-z0-9+.\\-]*)://([^/?#\\s]*)[^\\s]*$")

        /** Approximation of `URL(string:)`: scheme and host of a whitespace-free URL, or null. */
        fun parseUrl(text: String): Pair<String, String>? {
            val m = URL_PATTERN.matchEntire(text) ?: return null
            val authority = m.groupValues[2].substringAfterLast('@')
            val host = if (authority.startsWith("[")) authority.substringBefore("]") + "]" else authority.substringBefore(":")
            return m.groupValues[1] to host
        }
    }
}
