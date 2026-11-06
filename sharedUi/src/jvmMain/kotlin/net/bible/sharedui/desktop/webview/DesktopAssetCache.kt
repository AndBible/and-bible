package net.bible.sharedui.desktop.webview

import okio.BufferedSource
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * The one bounded, process-wide directory for page assets that have no file of their own (generated font and EPUB
 * stylesheets, streams of platforms that cannot serve by path). It is shared by every pane and routes object, holds at
 * most [MAX_FILES] files, is removed on exit, and the directories of dead processes are swept when it is first created,
 * so a crash cannot leave copies behind for long.
 */
internal object DesktopAssetCache {
    private const val MAX_FILES = 256
    private val lock = Any()
    private var dir: Path? = null
    private val byRoute = LinkedHashMap<String, Path>(16, 0.75f, true)
    private val routeBytes = HashMap<String, ByteArray>()
    private var counter = 0

    /** The cache directory, created (and stale sibling directories swept) on first use. */
    fun directory(): Path = synchronized(lock) {
        dir?.takeIf { Files.isDirectory(it) } ?: create().also { dir = it }
    }

    private fun create(): Path {
        val parent = Path.of(System.getProperty("java.io.tmpdir"), "andbible-page-assets-" + (System.getProperty("user.name") ?: "user"))
        Files.createDirectories(parent)
        Files.list(parent).use { it.toList() }.forEach { old ->
            val pid = old.fileName.toString().removePrefix("p").toLongOrNull() ?: return@forEach
            if (pid != ProcessHandle.current().pid() && !ProcessHandle.of(pid).isPresent) old.toFile().deleteRecursively()
        }
        val mine = parent.resolve("p" + ProcessHandle.current().pid())
        mine.toFile().deleteRecursively()
        Files.createDirectories(mine)
        Runtime.getRuntime().addShutdownHook(Thread { mine.toFile().deleteRecursively() })
        return mine
    }

    /** One reusable file per [route] holding [bytes]; rewritten (atomically) only when the content changed. */
    fun bytes(route: String, bytes: ByteArray): Path = synchronized(lock) {
        val existing = byRoute[route]?.takeIf { Files.isRegularFile(it) }
        if (existing != null && routeBytes[route]?.contentEquals(bytes) == true) return existing
        val file = existing ?: newFile("generated-${counter++}.css").also { remember(route, it) }
        publish(file) { Files.write(it, bytes) }
        routeBytes[route] = bytes
        file
    }

    /** A content-addressed copy of [source]: identical bytes from any pane or request share one file. */
    fun stream(source: BufferedSource): Path = synchronized(lock) {
        val staging = Files.createTempFile(directory(), "stream", ".tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            Files.newOutputStream(staging).use { out ->
                val buffer = ByteArray(64 * 1024)
                val input = source.inputStream()
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                }
            }
            val key = "sha-" + digest.digest().joinToString("") { "%02x".format(it) }
            val target = directory().resolve(key)
            if (!Files.isRegularFile(target)) {
                try { Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE) }
                catch (_: AtomicMoveNotSupportedException) { Files.move(staging, target) }
            }
            remember(key, target)
            target
        } finally {
            Files.deleteIfExists(staging)
        }
    }

    private fun newFile(name: String): Path = directory().resolve(name)

    private fun remember(key: String, file: Path) {
        byRoute[key] = file
        while (byRoute.size > MAX_FILES) {
            val eldest = byRoute.entries.first()
            byRoute.remove(eldest.key)
            routeBytes.remove(eldest.key)
            Files.deleteIfExists(eldest.value)
        }
    }

    private fun publish(file: Path, write: (Path) -> Unit) {
        val staging = Files.createTempFile(directory(), "stage", ".tmp")
        try {
            write(staging)
            try { Files.move(staging, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(staging, file, StandardCopyOption.REPLACE_EXISTING) }
        } finally {
            Files.deleteIfExists(staging)
        }
    }
}
