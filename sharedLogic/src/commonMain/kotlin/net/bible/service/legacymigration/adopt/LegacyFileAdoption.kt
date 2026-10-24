package net.bible.service.legacymigration.adopt

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.sqlite.SQLiteConnection
import net.bible.service.db.isCorruptionError
import net.bible.service.db.openSqlite
import net.bible.service.db.sqlitePrimaryResultCode
import okio.FileSystem
import okio.IOException
import okio.buffer
import okio.use
import okio.Path

/**
 * What [LegacyFileAdoption.adopt] did, as labels relative to the modules directory (`epub/<name>`) or, for [skipped] and
 * [failed], the old library's stable identifier.
 *
 * - [moved]: adopted into the new location by this run (a copy, see [LegacyFileAdoption]).
 * - [alreadyInPlace]: the target exists with the same bytes (a previous run, or the user installed the same book).
 * - [conflicts]: the target exists with different bytes; it is left as it is and nothing is overwritten.
 * - [skipped]: the entry cannot be adopted and never will be (no readable source file name, an unsafe name, a pointer to a
 *   missing generation). Not retried.
 * - [failed]: an I/O error while copying; worth another attempt on the next launch.
 */
data class AdoptionResult(
    val moved: List<String>,
    val alreadyInPlace: List<String>,
    val conflicts: List<String>,
    val skipped: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
) {
    /** True when a retry could not change anything: no entry failed on I/O. */
    val complete: Boolean get() = failed.isEmpty()
}

/**
 * Adopts the old Swift app's EPUB library into the new app's module folders, after the migration committed.
 *
 * ## Inventory (Swift app pinned in `.local/repos/and-bible-ios`, new app at HEAD)
 *
 * Old app: `ModuleStoreInstalledRegistrationReader.swift:82-87` and `ModuleStoreTransactionPublisher+Uninstall.swift:273-275`
 * fix the families under the SWORD root `Documents/sword`: `mods.d`, `modules`, `mybible`, `mysword`, `esword`, `epub`,
 * `ttf`, `background`, `prompts`. The module store is journaled (ADR 0011) but the journal is only a transient
 * `.module-recovery` / `.module-transaction-*` directory next to the live tree: the committed generation IS
 * `Documents/sword` itself, there is no `staging`/`current` pair to promote. EPUBs are the exception to "everything is under
 * `Documents/sword`": `EpubReader.swift:329-332` (`defaultLibraryRootURL`) keeps the reader's library in `Documents/epub`
 * with an immutable-generation layout (`EpubReaderGeneration.swift:55-92`): the package tree and the iOS-only SQLite index
 * of the current generation are in `.epub-generations/<id>/<gen>/{package,index.sqlite3}`, and `<id>.epub-current.json`
 * (`{schemaVersion, identifier, generationIdentifier}`) points at the current one. Books installed before generations
 * existed are a flat `<id>/` package with `<id>.index.sqlite3` (`migrateLegacyInstallIfNeeded`,
 * `EpubReaderGeneration.swift:320-345`). `<id>` is lossy (`stableIdentifier`, `EpubReaderLibrary.swift:350`: sanitised stem
 * plus a SHA-256 prefix); the original file name, which Android calls the display name and from which the book's initials
 * `Epub-...` derive (`EpubReader.initials(forDisplayFileName:)`, `EpubReader.swift:1086`), is the `source_file_name` row of
 * the index's `metadata` table (`EpubReader.swift:1101`).
 *
 * New app: `IosAppDirectories.modulesDir` = `Documents/sword` (`IosAppEnvironment.kt:67`); an EPUB is the extracted tree
 * `modulesDir/epub/<display name>/` (`InstallCommitter.kt:115`) which `addManuallyInstalledEpubBooks` scans
 * (`EpubBook.kt:243-245`) and registers under `epubInitials(<dir name>)`; MyBible, MySword and e-Sword are scanned in
 * `modulesDir/{mybible,mysword,esword}` (`MyBibleBook.kt:457`, `MySwordBook.kt:434`, `ESwordBook.kt:284`).
 *
 * ## Decision table
 *
 * | Old location | New location | Action |
 * |---|---|---|
 * | `Documents/sword/mods.d`, `modules` (SWORD) | same | identity, not touched (hashes unchanged) |
 * | `Documents/sword/mybible`, `mysword`, `esword`, `ttf`, `background`, `prompts` | same | identity, not touched |
 * | `Documents/sword/epub/<name>` (Android-layout registration) | same | identity, not touched |
 * | `Documents/epub/.epub-generations/<id>/<gen>/package` where `<id>.epub-current.json` names `<gen>` | `modulesDir/epub/<source_file_name>` | COPY the package tree |
 * | `Documents/epub/<id>` (flat, no published generation) | `modulesDir/epub/<source_file_name>` | COPY the package tree |
 * | stale generations, `*.index.sqlite3`, `*.epub-current.json` | nowhere | not adopted: the iOS index is not the new app's, which builds its own FTS database on first open |
 *
 * ## Copy, not move
 * Plan M-9 says the EPUBs move. They are copied instead: the migration's contract is that the old app's files stay intact so
 * the old app (and a rollback to it) keeps working; a move would turn a best-effort post-commit step into the one thing that
 * can damage the old install. The cost is the disk space of the extracted EPUBs until the user removes the old app.
 *
 * ## Rules
 * Idempotent and best-effort. A target that exists is never overwritten: same bytes is [AdoptionResult.alreadyInPlace],
 * different bytes is a [AdoptionResult.conflicts] entry. The copy is built in `modulesDir/.legacy-adopt-tmp` and renamed into
 * place, so a scan of `modulesDir/epub` never sees a half-copied book. A source file name that is empty, `.`/`..`, starts with
 * a dot or contains a path separator or NUL is skipped, never sanitised (a guessed name would change the book's initials and
 * orphan its bookmarks). One broken entry never stops the others.
 */
class LegacyFileAdoption(
    private val fs: FileSystem,
    private val oldDocuments: Path,
    private val newModulesDir: Path,
    private val openIndex: (Path) -> SQLiteConnection = { openSqlite(it.toString(), readOnly = true) },
) {
    private val epubLibrary = oldDocuments / "epub"
    private val tempRoot = newModulesDir / ".legacy-adopt-tmp"

    /** One book of the old library: where its package tree is and where the index naming it is. */
    private class Entry(val id: String, val packageDir: Path, val index: Path)

    fun adopt(): AdoptionResult {
        if (fs.metadataOrNull(epubLibrary)?.isDirectory != true) return AdoptionResult(emptyList(), emptyList(), emptyList())
        val moved = mutableListOf<String>()
        val inPlace = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<String>()
        try {
            for (entry in entries(skipped, failed)) {
                val name = try {
                    sourceFileName(entry.index)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed += entry.id
                    continue
                }
                if (name == null || !isSafeName(name)) { skipped += entry.id; continue }
                val label = "epub/$name"
                val target = newModulesDir / "epub" / name
                try {
                    when {
                        fs.exists(target) -> if (sameTree(entry.packageDir, target)) inPlace += label else conflicts += label
                        else -> { copyInto(entry, target); moved += label }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed += entry.id
                }
            }
        } finally {
            runCatching { fs.deleteRecursively(tempRoot, mustExist = false) }
        }
        return AdoptionResult(moved, inPlace, conflicts, skipped.sorted(), failed.sorted())
    }

    /** Published generations first, then flat packages without one; ids whose pointer or generation is unusable go to [skipped]. */
    private fun entries(skipped: MutableList<String>, failed: MutableList<String>): List<Entry> {
        val names = fs.list(epubLibrary).map { it.name }.sorted()
        val result = mutableListOf<Entry>()
        val published = mutableSetOf<String>()
        for (name in names.filter { it.endsWith(POINTER_SUFFIX) }) {
            val id = name.removeSuffix(POINTER_SUFFIX)
            published += id
            val text = try {
                fs.read(epubLibrary / name) { readUtf8() }
            } catch (e: IOException) {
                failed += id // transient: the pointer exists but cannot be read now
                continue
            }
            // A pointer that is not the expected JSON is permanent damage, not a transient error.
            val generation = try {
                val json = Json.parseToJsonElement(text).jsonObject
                check(json["identifier"]?.jsonPrimitive?.content == id)
                json["generationIdentifier"]!!.jsonPrimitive.content
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: IllegalStateException) {
                null
            } catch (e: NullPointerException) {
                null
            }
            val dir = generation?.takeIf { isSafeName(it) }?.let { epubLibrary / GENERATIONS / id / it }
            if (dir == null || !isSafeName(id) || fs.metadataOrNull(dir / "package")?.isDirectory != true) { skipped += id; continue }
            result += Entry(id, dir / "package", dir / "index.sqlite3")
        }
        for (name in names) {
            if (name.startsWith(".") || name in published || fs.metadataOrNull(epubLibrary / name)?.isDirectory != true) continue
            result += Entry(name, epubLibrary / name, epubLibrary / "$name.index.sqlite3")
        }
        return result
    }

    /**
     * The `source_file_name` of the book's index. Null (permanent: the entry is skipped) when the index is missing, has no
     * such row or table, or is corrupt or not a database. Any other failure (CANTOPEN, IOERR, BUSY, ...) is rethrown: it may
     * pass, so the caller counts the entry as failed and the adoption is retried.
     */
    private fun sourceFileName(index: Path): String? {
        if (!fs.exists(index)) return null
        return try {
            val db = openIndex(index)
            try {
                db.prepare("SELECT value FROM metadata WHERE key = 'source_file_name'").use { if (it.step()) it.getText(0) else null }
            } finally {
                db.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isCorruptionError(e) || sqlitePrimaryResultCode(e) == SQLITE_ERROR) null else throw e
        }
    }

    private fun isSafeName(name: String) =
        name.isNotEmpty() && !name.startsWith(".") && name.none { it == '/' || it == '\\' || it == '\u0000' }

    /** Copies [entry]'s package tree to a temporary folder and renames it to [target]. Symbolic links are not followed. */
    private fun copyInto(entry: Entry, target: Path) {
        val temp = tempRoot / entry.id
        fs.deleteRecursively(temp, mustExist = false)
        fs.createDirectories(temp)
        for (source in fs.listRecursively(entry.packageDir)) {
            val meta = fs.metadata(source)
            if (meta.symlinkTarget != null) continue
            val destination = temp / source.relativeTo(entry.packageDir).toString()
            if (meta.isDirectory) fs.createDirectories(destination)
            else { destination.parent?.let { fs.createDirectories(it) }; fs.copy(source, destination) }
        }
        fs.createDirectories(target.parent!!)
        fs.atomicMove(temp, target)
    }

    /** Same set of regular files with the same bytes (empty directories and symbolic links do not count). */
    private fun sameTree(a: Path, b: Path): Boolean {
        val left = files(a)
        val right = files(b)
        if (left.keys != right.keys) return false
        return left.all { (relative, path) -> sameBytes(path, right.getValue(relative)) }
    }

    /** Sizes first, then a chunked comparison, so a large EPUB is never held in memory twice. */
    private fun sameBytes(a: Path, b: Path): Boolean {
        if (fs.metadata(a).size != fs.metadata(b).size) return false
        var remaining = fs.metadata(a).size ?: return false
        return fs.source(a).buffer().use { left ->
            fs.source(b).buffer().use { right ->
                var equal = true
                while (equal && remaining > 0) {
                    val n = minOf(CHUNK, remaining)
                    left.require(n)
                    right.require(n)
                    equal = left.readByteString(n) == right.readByteString(n)
                    remaining -= n
                }
                equal
            }
        }
    }

    private fun files(root: Path): Map<String, Path> =
        fs.listRecursively(root).filter { fs.metadata(it).let { m -> m.isRegularFile && m.symlinkTarget == null } }
            .associateBy { it.relativeTo(root).toString() }

    private companion object {
        const val POINTER_SUFFIX = ".epub-current.json"
        const val CHUNK = 64L * 1024
        const val SQLITE_ERROR = 1 // e.g. no such table: the index is not the old app's
        const val GENERATIONS = ".epub-generations"
    }
}
