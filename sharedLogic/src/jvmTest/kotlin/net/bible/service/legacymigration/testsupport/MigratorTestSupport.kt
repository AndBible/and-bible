package net.bible.service.legacymigration.testsupport

import java.io.File
import java.security.MessageDigest
import net.bible.service.db.openSqlite
import net.bible.service.legacymigration.LegacyPlatform
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path

/** Thrown by [CrashingFileSystem] to model a process kill: an [Error], so the migrator's `catch (Exception)` does not see it. */
class SimulatedKill(message: String) : Error(message)

/**
 * A real file system whose [crashOnMove]th `atomicMove` call (1-based, counting state-file writes as well) dies with
 * [SimulatedKill], either before the move happens ([crashAfter] false) or right after it ([crashAfter] true).
 */
class CrashingFileSystem(
    delegate: FileSystem,
    private val crashOnMove: Int,
    private val crashAfter: Boolean,
) : ForwardingFileSystem(delegate) {
    var moves = 0
        private set

    override fun atomicMove(source: Path, target: Path) {
        moves++
        val crash = moves == crashOnMove
        if (crash && !crashAfter) throw SimulatedKill("killed before atomicMove #$moves ($source -> $target)")
        super.atomicMove(source, target)
        if (crash && crashAfter) throw SimulatedKill("killed after atomicMove #$moves ($source -> $target)")
    }
}

/** Counts the `atomicMove` calls of a run (a clean run's count bounds the crash points to try). */
class CountingFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
    var moves = 0
        private set

    override fun atomicMove(source: Path, target: Path) {
        moves++
        super.atomicMove(source, target)
    }
}

/** [platform] with another [fileSystem]. */
class PlatformWithFileSystem(platform: LegacyPlatform, override val fileSystem: FileSystem) : LegacyPlatform by platform

/**
 * Puts the legacy app's files of [fx] where the Swift app kept them below [platform]: the two stores with sidecars in
 * `Application Support`, the preserved AI file under `AndBible/AndroidDatabaseBackup/PreservedDatabases` (only when
 * [withPreserved]).
 */
fun installLegacyApp(fx: FixtureDir, platform: FakeLegacyPlatform, withPreserved: Boolean = false) {
    val support = File(platform.applicationSupport.toString())
    for (f in File(fx.root.toString()).listFiles().orEmpty()) {
        if (f.isFile && f.name.contains(".store")) f.copyTo(File(support, f.name), overwrite = true)
    }
    if (withPreserved) {
        val preserved = File(fx.root.toString(), "PreservedDatabases/ai_settings.sqlite3")
        check(preserved.isFile) { "fixture has no preserved AI file" }
        val target = File(support, "AndBible/AndroidDatabaseBackup/PreservedDatabases/ai_settings.sqlite3")
        target.parentFile.mkdirs()
        preserved.copyTo(target)
    }
}

/** sha256 of every file below [root] (relative path to hash), without the `-shm` index files (SQLite may touch those). */
fun hashTree(root: Path): Map<String, String> = File(root.toString()).walkTopDown()
    .filter { it.isFile && !it.name.endsWith("-shm") }
    .associate {
        it.relativeTo(File(root.toString())).path to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) }
    }

/** For every `*.sqlite3` file in [dir]: table name to row count (`sqlite_*` and Room's identity table excluded). */
fun databaseSummary(dir: Path): Map<String, Map<String, Int>> {
    val files = File(dir.toString()).listFiles { f -> f.isFile && f.name.endsWith(".sqlite3") }.orEmpty().sortedBy { it.name }
    return files.associate { file ->
        val c = openSqlite(file.path, readOnly = true)
        try {
            val tables = c.prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").let { s ->
                try { buildList { while (s.step()) add(s.getText(0)) } } finally { s.close() }
            }.filter { !it.startsWith("sqlite_") && it != "room_master_table" && it != "android_metadata" }
            file.name to tables.associateWith { t ->
                val s = c.prepare("SELECT COUNT(*) FROM `$t`")
                try { s.step(); s.getLong(0).toInt() } finally { s.close() }
            }
        } finally {
            c.close()
        }
    }
}

/** Creates a plain SQLite file at [file] with table `t` holding [rows] rows. */
fun writeSqlite(file: File, rows: Int) {
    val c = openSqlite(file.path)
    try {
        fun exec(sql: String) { val s = c.prepare(sql); try { s.step() } finally { s.close() } }
        exec("CREATE TABLE t (x INTEGER)")
        repeat(rows) { exec("INSERT INTO t VALUES ($it)") }
    } finally {
        c.close()
    }
}

/** Throws an [IOException] when `work/` or `state.json` of a migration directory is deleted after its `done.json` exists (post-commit cleanup). */
class FailingCleanupFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
    private fun fails(path: Path) =
        (path.name == "work" || path.name == "state.json") && path.parent?.let { metadataOrNull(it / "done.json") != null } == true

    override fun delete(path: Path, mustExist: Boolean) {
        if (fails(path)) throw okio.IOException("injected cleanup failure for $path")
        super.delete(path, mustExist)
    }

    override fun deleteRecursively(fileOrDirectory: Path, mustExist: Boolean) {
        if (fails(fileOrDirectory)) throw okio.IOException("injected cleanup failure for $fileOrDirectory")
        super.deleteRecursively(fileOrDirectory, mustExist)
    }
}

/** Throws an [IOException] for every deletion of a direct child of [dir] while [enabled] (models a rollback that cannot delete files). */
class FailingDeleteFileSystem(delegate: FileSystem, private val dir: Path) : ForwardingFileSystem(delegate) {
    @Volatile var enabled = true

    override fun delete(path: Path, mustExist: Boolean) {
        if (enabled && path.parent == dir && metadataOrNull(path) != null) throw okio.IOException("injected delete failure for $path")
        super.delete(path, mustExist)
    }
}

/** Runs each of [statements] against the SQLite file [file] (read-write). */
fun execSql(file: File, vararg statements: String) {
    val c = openSqlite(file.path)
    try {
        for (sql in statements) c.prepare(sql).let { try { it.step() } finally { it.close() } }
    } finally {
        c.close()
    }
}

/** Lets the move of `done.json` succeed and then throws an [IOException]: the rename happened but the call "failed". */
class ThrowAfterDoneMoveFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
    override fun atomicMove(source: Path, target: Path) {
        super.atomicMove(source, target)
        if (target.name == "done.json") throw okio.IOException("injected failure after the done.json rename")
    }
}

/** The first column of the first row of [sql] against [file], as text. */
fun readText(file: File, sql: String): String {
    val c = openSqlite(file.path, readOnly = true)
    try {
        return c.prepare(sql).let { try { check(it.step()); it.getText(0) } finally { it.close() } }
    } finally {
        c.close()
    }
}

/** A platform whose Keychain cannot be read (locked device, missing entitlement); its message carries a fake secret that must never be echoed. */
class ThrowingKeychainPlatform(platform: LegacyPlatform) : LegacyPlatform by platform {
    override fun legacyKeychain(service: String, account: String): String? =
        throw IllegalStateException("Legacy Keychain read failed: status -25308 secret-leak-check-4711")
}

/**
 * Makes the Core Data model fingerprint of [store] unknown by flipping one byte of a version hash inside `Z_METADATA`'s plist
 * bytes (the same mutation the reader's own unknown-model test uses).
 */
fun makeModelUnknown(store: File) {
    val c = openSqlite(store.path)
    try {
        val plist = c.prepare("SELECT Z_PLIST FROM Z_METADATA").let { try { it.step(); it.getBlob(0) } finally { it.close() } }
        val hash = c.versionHashes().values.first()
        val offset = (0..plist.size - hash.size).first { i -> hash.indices.all { plist[i + it] == hash[it] } }
        val mutated = plist.copyOf().also { it[offset] = (it[offset].toInt() xor 0x01).toByte() }
        c.prepare("UPDATE Z_METADATA SET Z_PLIST = ?").let { try { it.bindBlob(1, mutated); it.step() } finally { it.close() } }
    } finally {
        c.close()
    }
}
