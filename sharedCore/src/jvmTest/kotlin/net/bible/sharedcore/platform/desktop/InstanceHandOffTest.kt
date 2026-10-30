package net.bible.sharedcore.platform.desktop

import net.bible.sharedcore.platform.desktop.files.HandOffPlatform
import net.bible.sharedcore.platform.desktop.files.InstanceHandOff
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstanceHandOffTest {
    @Test fun handOffToRunningInstance() {
        val dir = Files.createTempDirectory("handoff")
        val received = LinkedBlockingQueue<List<Path>>()
        InstanceHandOff.listen(dir) { received.add(it) }.use {
            val file = dir.resolve("document.zip")
            val java = Paths.get(System.getProperty("java.home"), "bin", "java")
            val process = ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                "net.bible.sharedcore.platform.desktop.InstanceHandOffTestKt", dir.toString(), file.toString())
                .redirectErrorStream(true).start()
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "child JVM timed out")
            assertEquals(0, process.exitValue(), process.inputStream.bufferedReader().readText())
            assertEquals(listOf(file), received.poll(5, TimeUnit.SECONDS))
        }
    }

    @Test fun acknowledgementWaitsForAcceptance() {
        val dir = Files.createTempDirectory("handoff-ack")
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            InstanceHandOff.listen(dir) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }.use {
                val sent = executor.submit<Boolean> { InstanceHandOff.send(dir, listOf(dir.resolve("book.zip"))) }
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                assertFalse(sent.isDone, "send acknowledged before acceptance completed")
                release.countDown()
                assertTrue(sent.get(3, TimeUnit.SECONDS))
            }
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun staleSocketIsReplaced() {
        val dir = Files.createTempDirectory("handoff-stale")
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use {
            it.bind(UnixDomainSocketAddress.of(dir.resolve("instance.sock")))
        }
        val received = LinkedBlockingQueue<List<Path>>()
        InstanceHandOff.listen(dir) { received.add(it) }.use {
            val file = dir.resolve("document.zip")
            assertTrue(InstanceHandOff.send(dir, listOf(file)))
            assertEquals(listOf(file), received.poll(5, TimeUnit.SECONDS))
        }
    }

    @Test fun activeSocketIsNotReplaced() {
        val dir = Files.createTempDirectory("handoff-active")
        val received = LinkedBlockingQueue<List<Path>>()
        InstanceHandOff.listen(dir) { received.add(it) }.use {
            assertTrue(runCatching { InstanceHandOff.listen(dir) {} }.isFailure)
            assertTrue(InstanceHandOff.send(dir, emptyList()))
            assertEquals(emptyList(), received.poll(5, TimeUnit.SECONDS))
        }
    }

    @Test fun regularFileAndSymlinkAreNeverReplaced() {
        for (symlink in listOf(false, true)) {
            val dir = Files.createTempDirectory("handoff-file")
            val target = dir.resolve("target")
            Files.writeString(target, "keep")
            val socket = dir.resolve("instance.sock")
            if (symlink) Files.createSymbolicLink(socket, target) else Files.writeString(socket, "keep")
            assertTrue(runCatching { InstanceHandOff.listen(dir) {} }.isFailure)
            assertEquals("keep", Files.readString(socket))
            if (symlink) assertTrue(Files.isSymbolicLink(socket))
        }
    }

    @Test fun listenerCloseDoesNotRemoveReplacementSocket() {
        val dir = Files.createTempDirectory("handoff-replace")
        val socket = dir.resolve("instance.sock")
        val first = InstanceHandOff.listen(dir) {}
        Files.delete(socket)
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { replacement ->
            replacement.bind(UnixDomainSocketAddress.of(socket))
            first.close()
            assertTrue(Files.exists(socket))
        }
    }

    @Test fun listenerSocketIsPrivate() {
        val dir = Files.createTempDirectory("handoff-mode")
        InstanceHandOff.listen(dir) {}.use {
            val permissions = Files.getPosixFilePermissions(dir.resolve("instance.sock"))
            assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), permissions)
        }
    }

    @Test fun malformedFrameIsRejectedAndListenerContinues() {
        val dir = Files.createTempDirectory("handoff-malformed")
        val received = LinkedBlockingQueue<List<Path>>()
        InstanceHandOff.listen(dir) { received.add(it) }.use {
            java.nio.channels.SocketChannel.open(StandardProtocolFamily.UNIX).use { peer ->
                peer.connect(UnixDomainSocketAddress.of(dir.resolve("instance.sock")))
                val invalidCount = java.nio.ByteBuffer.allocate(4).putInt(Int.MAX_VALUE).flip() as java.nio.ByteBuffer
                while (invalidCount.hasRemaining()) peer.write(invalidCount)
            }
            val file = dir.resolve("valid.zip")
            assertTrue(InstanceHandOff.send(dir, listOf(file)))
            assertEquals(listOf(file), received.poll(5, TimeUnit.SECONDS))
            assertTrue(received.isEmpty())
        }
    }

    @Test fun closeUnblocksStalledPeer() {
        val dir = Files.createTempDirectory("handoff-stalled")
        val listener = InstanceHandOff.listen(dir) {}
        java.nio.channels.SocketChannel.open(StandardProtocolFamily.UNIX).use { peer ->
            peer.connect(UnixDomainSocketAddress.of(dir.resolve("instance.sock")))
            val start = System.nanoTime()
            listener.close()
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000)
            peer.configureBlocking(false)
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            val buffer = java.nio.ByteBuffer.allocate(1)
            var result = 0
            while (result == 0 && System.nanoTime() < until) {
                // The listener's close shows up as end-of-stream or, depending on timing, as a connection reset (RST).
                result = try { peer.read(buffer) } catch (e: java.net.SocketException) { -1 }
                if (result == 0) Thread.sleep(10)
            }
            assertEquals(-1, result)
        }
    }

    @Test fun unavailableListenerIsNotAcknowledged() {
        assertFalse(InstanceHandOff.send(Files.createTempDirectory("handoff-missing"), emptyList()))
    }

    private fun windows(tmp: Path, deleteFile: (Path) -> Unit = { Files.deleteIfExists(it) }) =
        HandOffPlatform(osName = "Windows 11", tmpDir = tmp, deleteFile = deleteFile, deleteRetries = 4, retryDelayMs = 1)

    private fun staleSocket(dir: Path) {
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { it.bind(UnixDomainSocketAddress.of(dir.resolve("instance.sock"))) }
    }

    @Test fun staleSocketReplacedOnWindowsSemantics() {
        // Windows cannot delete a file that is still open (a scanner or the dying process holds the stale socket for a
        // moment): the delete fails a few times, then succeeds. The hand-off must retry, not fail the start.
        val dir = Files.createTempDirectory("handoff-win-stale")
        staleSocket(dir)
        var attempts = 0
        val platform = windows(dir) { path ->
            if (++attempts <= 2) throw java.nio.file.FileSystemException(path.toString(), null, "The process cannot access the file because it is being used by another process")
            Files.deleteIfExists(path)
        }
        val received = LinkedBlockingQueue<List<Path>>()
        var atBind = -1
        InstanceHandOff.listen(dir, platform) { received.add(it) }.use {
            atBind = attempts
            val file = dir.resolve("document.zip")
            assertTrue(InstanceHandOff.send(dir, listOf(file), platform))
            assertEquals(listOf(file), received.poll(5, TimeUnit.SECONDS))
        }
        assertEquals(3, atBind, "two failed deletes, then the third succeeded")
    }

    @Test fun staleSocketThatNeverBecomesDeletableFailsClearly() {
        val dir = Files.createTempDirectory("handoff-win-locked")
        staleSocket(dir)
        var attempts = 0
        val platform = windows(dir) { throw java.nio.file.FileSystemException(it.toString(), null, "in use").also { attempts++ } }
        val failure = kotlin.test.assertFailsWith<java.io.IOException> { InstanceHandOff.listen(dir, platform) {} }
        assertEquals(4, attempts)
        assertTrue("instance.sock" in failure.message.orEmpty(), failure.message)
    }

    @Test fun longWindowsDataDirUsesShortSocketPath() {
        // sun_path holds about 108 bytes; a %APPDATA% under a long, non-ASCII profile name can exceed it.
        val tmp = Files.createTempDirectory("hs")
        val data = Files.createDirectories(Files.createTempDirectory("Jänis").resolve("Ö".repeat(60)).resolve("AndBible"))
        assertTrue(data.resolve("instance.sock").toString().toByteArray().size > 107)
        val platform = windows(tmp)
        val socket = InstanceHandOff.socketPath(data, platform)
        assertTrue(socket.toString().toByteArray().size <= 100, socket.toString())
        assertEquals(tmp, socket.parent)
        assertEquals(socket, InstanceHandOff.socketPath(data, platform), "deterministic: the sender finds the listener")
        assertTrue(socket != InstanceHandOff.socketPath(data.resolveSibling("Other"), platform))
        val received = LinkedBlockingQueue<List<Path>>()
        InstanceHandOff.listen(data, platform) { received.add(it) }.use {
            val file = data.resolve("document.zip")
            assertTrue(InstanceHandOff.send(data, listOf(file), platform))
            assertEquals(listOf(file), received.poll(5, TimeUnit.SECONDS))
        }
        assertFalse(Files.exists(socket), "the socket file is removed on close")
        // A short data dir keeps the socket next to the lock file, on every OS.
        val short = Files.createTempDirectory("sd")
        assertEquals(short.resolve("instance.sock"), InstanceHandOff.socketPath(short, platform))
        assertEquals(data.resolve("instance.sock"), InstanceHandOff.socketPath(data, HandOffPlatform(osName = "Linux")))
    }
}

fun main(args: Array<String>) {
    if (!InstanceHandOff.send(Paths.get(args[0]), listOf(Paths.get(args[1])))) kotlin.system.exitProcess(1)
}
