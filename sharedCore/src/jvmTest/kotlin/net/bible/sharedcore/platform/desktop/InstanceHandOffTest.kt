package net.bible.sharedcore.platform.desktop

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
                result = peer.read(buffer)
                if (result == 0) Thread.sleep(10)
            }
            assertEquals(-1, result)
        }
    }

    @Test fun unavailableListenerIsNotAcknowledged() {
        assertFalse(InstanceHandOff.send(Files.createTempDirectory("handoff-missing"), emptyList()))
    }
}

fun main(args: Array<String>) {
    if (!InstanceHandOff.send(Paths.get(args[0]), listOf(Paths.get(args[1])))) kotlin.system.exitProcess(1)
}
