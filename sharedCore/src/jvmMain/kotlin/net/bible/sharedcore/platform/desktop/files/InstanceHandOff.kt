package net.bible.sharedcore.platform.desktop.files

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The OS-dependent parts of [InstanceHandOff], injectable so Windows behaviour is testable on Linux.
 *
 * @param osName `os.name`
 * @param tmpDir where the short fallback socket goes when the data dir path is too long for an AF_UNIX address
 * @param deleteFile removes a stale socket file; on Windows it fails while anything still has the file open
 * @param deleteRetries attempts at removing a stale socket before giving up (Windows: the dying process may hold it briefly)
 */
internal class HandOffPlatform(
    val osName: String = System.getProperty("os.name").orEmpty(),
    val tmpDir: Path = Paths.get(System.getProperty("java.io.tmpdir")),
    val deleteFile: (Path) -> Unit = { Files.deleteIfExists(it) },
    val deleteRetries: Int = 10,
    val retryDelayMs: Long = 100,
) {
    val windows: Boolean get() = osName.lowercase().startsWith("windows")
}

/** Bounded, acknowledged local hand-off; a callback owns the files before the acknowledgement is sent. */
object InstanceHandOff {
    private const val MAX_FILES = 128
    private const val MAX_PATH = 8192
    private const val TIMEOUT_MS = 2000L
    private const val ACK = 0x41434b31

    /** sun_path is about 108 bytes (NUL included); stay clear of it. Measured in UTF-8, the safe reading for non-ASCII. */
    private const val MAX_SOCKET_PATH_BYTES = 100

    /**
     * Where the socket lives: `<dataDir>/instance.sock`, next to the lock file. On Windows a `%APPDATA%` under a long or
     * non-ASCII profile name can exceed the AF_UNIX path limit; then the socket goes under [HandOffPlatform.tmpDir] with a
     * name derived from a hash of the data dir, so the listener and a second launch compute the same path.
     */
    internal fun socketPath(dataDir: Path, platform: HandOffPlatform = HandOffPlatform()): Path {
        val preferred = dataDir.resolve("instance.sock")
        if (!platform.windows || preferred.toString().toByteArray(StandardCharsets.UTF_8).size <= MAX_SOCKET_PATH_BYTES) return preferred
        val hash = MessageDigest.getInstance("SHA-256").digest(dataDir.toAbsolutePath().normalize().toString().toByteArray(StandardCharsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it) }
        return platform.tmpDir.resolve("andbible-$hash.sock")
    }

    private fun address(path: Path) = UnixDomainSocketAddress.of(path)

    fun listen(dataDir: Path, onFiles: (List<Path>) -> Unit): AutoCloseable = listen(dataDir, HandOffPlatform(), onFiles)

    internal fun listen(dataDir: Path, platform: HandOffPlatform, onFiles: (List<Path>) -> Unit): AutoCloseable {
        Files.createDirectories(dataDir)
        if (!platform.windows) Files.setPosixFilePermissions(dataDir, PosixFilePermissions.fromString("rwx------"))
        val path = socketPath(dataDir, platform)
        val windows = platform.windows
        // Windows has no file keys and no unix:mode; a socket file there is a reparse point, so identify it by its
        // timestamps and size instead.
        fun key(attrs: BasicFileAttributes): Any? =
            if (windows) listOf(attrs.creationTime(), attrs.lastModifiedTime(), attrs.size()) else attrs.fileKey()
        fun identity(): Any? = key(Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS))
        fun socketIdentity(): Any {
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            if (windows) {
                require(!attrs.isDirectory && !attrs.isSymbolicLink) { "Not a socket: $path" }
            } else {
                require(attrs.isOther && !attrs.isSymbolicLink) { "Not a socket: $path" }
                val mode = Files.getAttribute(path, "unix:mode", NOFOLLOW_LINKS) as Int
                require(mode and 0xf000 == 0xc000) { "Not a socket: $path" }
            }
            return requireNotNull(key(attrs))
        }
        fun deleteIfSame(key: Any) {
            if (runCatching { socketIdentity() == key }.getOrDefault(false)) runCatching { platform.deleteFile(path) }
        }
        // An open file cannot be deleted on Windows: the dying owner of a stale socket may hold it for a moment.
        fun deleteStale(key: Any) {
            var last: Exception? = null
            repeat(platform.deleteRetries.coerceAtLeast(1)) { attempt ->
                if (attempt > 0) Thread.sleep(platform.retryDelayMs)
                if (runCatching { socketIdentity() == key }.getOrDefault(false)) {
                    try { platform.deleteFile(path); return } catch (e: java.io.IOException) { last = e }
                } else return
            }
            throw java.io.IOException("Cannot remove the stale socket $path (still in use?)", last)
        }
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        try {
            try { server.bind(address(path)) } catch (original: Exception) {
                if (!Files.exists(path, NOFOLLOW_LINKS)) throw original
                val key = socketIdentity()
                val active = try {
                    SocketChannel.open(StandardProtocolFamily.UNIX).use { probe ->
                        probe.configureBlocking(false)
                        if (!probe.connect(address(path))) {
                            waitReady(probe, SelectionKey.OP_CONNECT, System.nanoTime() + TIMEOUT_MS * 1_000_000)
                            check(probe.finishConnect())
                        }
                    }
                    true
                } catch (_: java.net.ConnectException) { false }
                if (active) throw original
                check(identity() == key) { "Socket changed during stale probe" }
                deleteStale(key)
                server.bind(address(path))
            }
        } catch (e: Throwable) { server.close(); throw e }
        val boundKey = socketIdentity()
        try { if (!windows) Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")) }
        catch (e: Throwable) { server.close(); deleteIfSame(boundKey); throw e }
        val closed = AtomicBoolean(false)
        val guard = Any()
        var current: SocketChannel? = null
        Thread({
            while (!closed.get()) {
                val peer = try { server.accept() } catch (_: Exception) { break }
                synchronized(guard) {
                    if (closed.get()) { peer.close(); return@Thread }
                    current = peer
                }
                try {
                    peer.use {
                        it.configureBlocking(false)
                        val deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000
                        fun readInt(): Int = ByteBuffer.allocate(4).also { b -> transfer(it, b, true, deadline); b.flip() }.int
                        val count = readInt()
                        require(count in 0..MAX_FILES)
                        val files = (0 until count).map {
                            val n = readInt()
                            require(n in 1..MAX_PATH)
                            val b = ByteBuffer.allocate(n)
                            transfer(peer, b, true, deadline)
                            b.flip()
                            val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(b).toString()
                            Paths.get(text).also { p -> require(p.isAbsolute) }
                        }
                        onFiles(files)
                        transfer(peer, ByteBuffer.allocate(4).putInt(ACK).flip() as ByteBuffer, false, deadline)
                    }
                } catch (_: Exception) { runCatching { peer.close() } }
                finally { synchronized(guard) { current = null } }
            }
        }, "instance-handoff").apply { isDaemon = true; start() }
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) {
                server.close()
                synchronized(guard) { current?.close() }
                deleteIfSame(boundKey)
            }
        }
    }

    fun send(dataDir: Path, files: List<Path>): Boolean = send(dataDir, files, HandOffPlatform())

    internal fun send(dataDir: Path, files: List<Path>, platform: HandOffPlatform): Boolean {
        val socket = socketPath(dataDir, platform)
        val bytes = try {
            require(files.size <= MAX_FILES)
            files.map { it.toAbsolutePath().normalize().toString().toByteArray(StandardCharsets.UTF_8).also { b -> require(b.size in 1..MAX_PATH) } }
        } catch (_: Exception) { return false }
        val end = System.nanoTime() + 8_000_000_000L
        do {
            var connected = false
            try {
                SocketChannel.open(StandardProtocolFamily.UNIX).use { peer ->
                    peer.configureBlocking(false)
                    val deadline = minOf(end, System.nanoTime() + TIMEOUT_MS * 1_000_000)
                    if (!peer.connect(address(socket))) {
                        waitReady(peer, SelectionKey.OP_CONNECT, deadline)
                        check(peer.finishConnect())
                    }
                    connected = true
                    fun writeInt(n: Int) { val b = ByteBuffer.allocate(4).putInt(n); b.flip(); transfer(peer, b, false, deadline) }
                    writeInt(bytes.size)
                    bytes.forEach { writeInt(it.size); transfer(peer, ByteBuffer.wrap(it), false, deadline) }
                    val ack = ByteBuffer.allocate(4)
                    transfer(peer, ack, true, deadline)
                    ack.flip()
                    return ack.int == ACK
                }
            } catch (_: Exception) {
                if (connected || System.nanoTime() >= end) return false
                try { Thread.sleep(100) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); return false }
            }
        } while (System.nanoTime() < end)
        return false
    }

    private fun transfer(channel: SocketChannel, buffer: ByteBuffer, read: Boolean, deadline: Long) {
        while (buffer.hasRemaining()) {
            val n = if (read) channel.read(buffer) else channel.write(buffer)
            if (n < 0) error("peer closed")
            if (n == 0) waitReady(channel, if (read) SelectionKey.OP_READ else SelectionKey.OP_WRITE, deadline)
            check(System.nanoTime() < deadline) { "handoff timed out" }
        }
    }

    private fun waitReady(channel: SocketChannel, op: Int, deadline: Long) {
        Selector.open().use { selector ->
            channel.register(selector, op)
            val remaining = (deadline - System.nanoTime()) / 1_000_000
            check(remaining > 0 && selector.select(remaining) > 0) { "handoff timed out" }
        }
    }
}
