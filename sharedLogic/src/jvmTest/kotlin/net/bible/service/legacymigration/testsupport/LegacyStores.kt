package net.bible.service.legacymigration.testsupport

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import net.bible.service.legacymigration.plist.BinaryPlist
import net.bible.service.legacymigration.plist.LegacyFormatException
import net.bible.service.legacymigration.plist.PlistData
import net.bible.service.legacymigration.plist.PlistDict
import net.bible.service.legacymigration.schema.modelFingerprint

/** The Core Data store files (`*.store`) of an unpacked fixture, sorted by name. */
fun FixtureDir.storeFiles(): List<File> =
    root.toFile().listFiles { f -> f.isFile && f.name.endsWith(".store") }.orEmpty().sortedBy { it.name }

/** Opens a store of an unpacked fixture. The fixture is a private temp copy, so the WAL may be checkpointed. */
fun openStore(file: File): SQLiteConnection = BundledSQLiteDriver().open(file.path)

/** Runs [sql] with text/blob/long cells fetched through [read]; returns the rows. */
fun <T> SQLiteConnection.query(sql: String, vararg args: Any, read: (androidx.sqlite.SQLiteStatement) -> T): List<T> {
    val statement = prepare(sql)
    try {
        args.forEachIndexed { i, a ->
            when (a) {
                is String -> statement.bindText(i + 1, a)
                is Long -> statement.bindLong(i + 1, a)
                is Int -> statement.bindLong(i + 1, a.toLong())
                else -> error("unsupported bind ${a::class}")
            }
        }
        val rows = mutableListOf<T>()
        while (statement.step()) rows += read(statement)
        return rows
    } finally {
        statement.close()
    }
}

/** `NSStoreModelVersionHashes` of `Z_METADATA`, entity name to raw hash bytes. */
fun SQLiteConnection.versionHashes(): Map<String, ByteArray> {
    val plist = query("SELECT Z_PLIST FROM Z_METADATA") { it.getBlob(0) }.single()
    val root = BinaryPlist.parse(plist) as? PlistDict ?: throw LegacyFormatException("Z_METADATA plist is not a dictionary")
    val hashes = root.entries["NSStoreModelVersionHashes"] as? PlistDict
        ?: throw LegacyFormatException("Z_METADATA has no NSStoreModelVersionHashes")
    return hashes.entries.mapValues { (name, v) ->
        (v as? PlistData)?.bytes ?: throw LegacyFormatException("version hash of $name is not data")
    }
}

/** The Kotlin model fingerprint of a store. */
fun SQLiteConnection.fingerprint(): String = modelFingerprint(versionHashes())

/** Names of all tables, from `sqlite_master`. */
fun SQLiteConnection.tableNames(): List<String> =
    query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name") { it.getText(0) }

/** Column names of [table] in table order. */
fun SQLiteConnection.columnNames(table: String): List<String> =
    query("SELECT name FROM pragma_table_info('$table')") { it.getText(0) }
