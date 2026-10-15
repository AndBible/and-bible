package net.bible.sharedcore.platform.cloud

import okio.Buffer
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.use

/**
 * A [CloudFileStore] over a directory tree (iCloud Drive's ubiquity container on iOS). Ids are paths
 * relative to the root ("" is the root); folders are directories. [root] is null when signed out.
 * Symlinks are rejected, including inside recursively visited directories, to confine all access.
 */
class FileTreeCloudStore(
    private val fs: FileSystem,
    private val root: () -> Path?,
    private val coordination: FileCoordination = FileCoordination.PassThrough,
    private val downloadTimeoutMs: Long = 60_000,
    private val now: () -> Long,
) : CloudFileStore {
    override val signedIn: Boolean get() = root() != null
    override suspend fun signIn(): Boolean = signedIn
    override suspend fun signOut() = Unit

    private fun currentRoot(): Path {
        val path = root() ?: throw IOException("not signed in")
        if (!path.isAbsolute) throw IOException("cloud root must be absolute")
        val normalized = path.normalized()
        rejectSymlinks(normalized)
        return normalized
    }

    private fun rejectSymlinks(path: Path) {
        var current = path.root ?: throw IOException("expected absolute path")
        for (segment in path.segments) {
            current /= segment
            if (fs.metadataOrNull(current)?.symlinkTarget != null) {
                throw IOException("symlinks are not supported: $current")
            }
        }
    }

    private fun resolve(base: Path, id: String): Path {
        if (id.isNotEmpty() && (id.toPath().isAbsolute || '\\' in id || ':' in id ||
                id.split('/').any { it == ".." || it == "." || it.isEmpty() })) {
            throw IOException("invalid cloud id: $id")
        }
        return (if (id.isEmpty()) base else base / id).also { rejectSymlinks(it) }
    }

    private fun childId(parent: String, name: String): String {
        if (name.isEmpty() || '/' in name || '\\' in name || ':' in name || name == "." || name == "..") {
            throw IOException("invalid cloud name: $name")
        }
        return if (parent.isEmpty()) name else "$parent/$name"
    }

    // Coordinators may supply a different URL; it must satisfy the same confinement rules.
    private fun coordinatedPath(base: Path, path: Path): Path = resolve(base, path.relativeTo(base).toString())

    private fun describe(base: Path, id: String): CloudFile {
        val path = resolve(base, id)
        val metadata = fs.metadataOrNull(path) ?: throw FileNotFoundException(id)
        return CloudFile(id, path.name, if (metadata.isDirectory) 0 else metadata.size ?: 0,
            metadata.createdAtMillis ?: metadata.lastModifiedAtMillis ?: 0, id.substringBeforeLast('/', ""))
    }

    override suspend fun get(id: String): CloudFile = describe(currentRoot(), id)

    private fun children(base: Path, id: String): List<Path> {
        val path = resolve(base, id)
        if (fs.metadataOrNull(path) == null) return emptyList()
        return fs.list(path).onEach { rejectSymlinks(it) }
    }

    override suspend fun listFiles(
        parentsIds: List<String>?, name: String?, mimeType: String?, createdTimeAtLeast: Long?
    ): List<CloudFile> {
        val base = currentRoot()
        val files = mutableListOf<CloudFile>()
        fun visit(parent: String, recursive: Boolean) {
            for (path in children(base, parent)) {
                val id = path.relativeTo(base).toString()
                if (fs.metadata(path).isDirectory) {
                    if (recursive) visit(id, true)
                } else {
                    files += describe(base, id)
                }
            }
        }
        if (parentsIds == null) visit("", true) else parentsIds.distinct().forEach { visit(it, false) }
        return files.filter {
            (name == null || it.name == name) && matchesMime(it.name, mimeType) &&
                (createdTimeAtLeast == null || it.createdTime >= createdTimeAtLeast)
        }.sortedWith(compareBy<CloudFile> { it.createdTime }.thenBy { it.id })
    }

    private fun matchesMime(name: String, mimeType: String?): Boolean = when (mimeType) {
        null -> true
        "application/gzip", "application/x-gzip" -> name.endsWith(".gz")
        "application/zip" -> name.endsWith(".zip")
        "application/x-sqlite3" -> name.endsWith(".sqlite3")
        else -> false
    }

    override suspend fun getFolders(parentId: String): List<CloudFile> {
        val base = currentRoot()
        return children(base, parentId).filter { fs.metadata(it).isDirectory }
            .map { describe(base, it.relativeTo(base).toString()) }
    }

    override suspend fun download(id: String, sink: Sink, onProgress: DownloadProgressListener?) {
        sink.use { output ->
            val base = currentRoot()
            val path = resolve(base, id)
            coordination.ensureDownloaded(path, downloadTimeoutMs)
            coordination.read(path) { local ->
                val checked = coordinatedPath(base, local)
                if (!fs.exists(checked)) throw FileNotFoundException(id)
                fs.source(checked).use { input ->
                    val buffer = Buffer()
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer, 64L * 1024)
                        if (count == -1L) break
                        output.write(buffer, count)
                        total += count
                        onProgress?.invoke(total)
                    }
                }
            }
        }
    }

    override suspend fun createNewFolder(name: String, parentId: String?): CloudFile {
        val base = currentRoot()
        val id = childId(parentId ?: "", name)
        fs.createDirectories(resolve(base, id))
        return describe(base, id)
    }

    override suspend fun upload(name: String, file: Path, parentId: String): CloudFile {
        val base = currentRoot()
        val id = childId(parentId, name)
        val target = resolve(base, id)
        coordination.write(target) { local ->
            val checked = coordinatedPath(base, local)
            val temporary = resolve(base, checked.relativeTo(base).toString() + ".tmp-${now()}")
            if (fs.exists(temporary)) throw IOException("temporary file already exists: $temporary")
            try {
                fs.sink(temporary, mustCreate = true).use { output ->
                    fs.source(file).use { input ->
                        val buffer = Buffer()
                        while (true) {
                            val count = input.read(buffer, 64L * 1024)
                            if (count == -1L) break
                            output.write(buffer, count)
                        }
                    }
                }
                fs.atomicMove(temporary, checked)
            } finally {
                fs.delete(temporary, mustExist = false)
            }
        }
        return describe(base, id)
    }

    override suspend fun delete(id: String) {
        val base = currentRoot()
        val path = resolve(base, id)
        if (!fs.exists(path)) throw FileNotFoundException(id)
        // Preflight the whole subtree: recursive deletion must not escape through a link either.
        fun checkTree(path: Path) {
            rejectSymlinks(path)
            if (fs.metadata(path).isDirectory) fs.list(path).forEach { checkTree(it) }
        }
        checkTree(path)
        fs.deleteRecursively(path, mustExist = true)
    }
}
