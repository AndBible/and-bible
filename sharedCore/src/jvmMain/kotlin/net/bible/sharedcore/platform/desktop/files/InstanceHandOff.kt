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
import java.util.concurrent.atomic.AtomicBoolean

/** Bounded, acknowledged local hand-off; a callback owns the files before the acknowledgement is sent. */
object InstanceHandOff {
    private const val MAX_FILES = 128
    private const val MAX_PATH = 8192
    private const val TIMEOUT_MS = 2000L
    private const val ACK = 0x41434b31

    private fun address(dir: Path) = UnixDomainSocketAddress.of(dir.resolve("instance.sock"))

    fun listen(dataDir: Path, onFiles: (List<Path>) -> Unit): AutoCloseable {
        Files.createDirectories(dataDir)
        Files.setPosixFilePermissions(dataDir, PosixFilePermissions.fromString("rwx------"))
        val path = dataDir.resolve("instance.sock")
        fun identity(): Any? = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS).fileKey()
        fun socketIdentity(): Any {
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
            require(attrs.isOther && !attrs.isSymbolicLink) { "Not a socket: $path" }
            val mode = Files.getAttribute(path, "unix:mode", NOFOLLOW_LINKS) as Int
            require(mode and 0xf000 == 0xc000) { "Not a socket: $path" }
            return requireNotNull(attrs.fileKey())
        }
        fun deleteIfSame(key: Any) {
            if (runCatching { socketIdentity() == key }.getOrDefault(false)) Files.deleteIfExists(path)
        }
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        try {
            try { server.bind(address(dataDir)) } catch (original: Exception) {
                if (!Files.exists(path, NOFOLLOW_LINKS)) throw original
                val key = socketIdentity()
                val active = try {
                    SocketChannel.open(StandardProtocolFamily.UNIX).use { probe ->
                        probe.configureBlocking(false)
                        if (!probe.connect(address(dataDir))) {
                            waitReady(probe, SelectionKey.OP_CONNECT, System.nanoTime() + TIMEOUT_MS * 1_000_000)
                            check(probe.finishConnect())
                        }
                    }
                    true
                } catch (_: java.net.ConnectException) { false }
                if (active) throw original
                check(identity() == key) { "Socket changed during stale probe" }
                deleteIfSame(key)
                server.bind(address(dataDir))
            }
        } catch (e: Throwable) { server.close(); throw e }
        val boundKey = socketIdentity()
        try { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")) }
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

    fun send(dataDir: Path, files: List<Path>): Boolean {
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
                    if (!peer.connect(address(dataDir))) {
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
