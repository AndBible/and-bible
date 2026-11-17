package net.bible.desktop

import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecondLaunchTest {
    @Test fun secondLaunchSendsActivateEvenWithoutFiles() {
        val dir = Files.createTempDirectory("second-launch")
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { server ->
                server.bind(UnixDomainSocketAddress.of(dir.resolve("instance.sock")))
                val request = executor.submit<List<Int>> {
                    server.accept().use { peer ->
                        fun readInt(): Int {
                            val frame = ByteBuffer.allocate(4)
                            while (frame.hasRemaining()) check(peer.read(frame) >= 0)
                            frame.flip()
                            return frame.int
                        }
                        val first = readInt()
                        val values = if (first == -1) listOf(first, readInt()) else listOf(first)
                        val ack = ByteBuffer.allocate(4).putInt(0x41434b31).flip() as ByteBuffer
                        while (ack.hasRemaining()) peer.write(ack)
                        values
                    }
                }
                assertEquals(0, handOffSecondLaunch(dir, emptyList()) { error("Successful activation reported as failure") })
                assertEquals(listOf(0), request.get(3, TimeUnit.SECONDS))
            }
        } finally {
            executor.shutdownNow()
            dir.toFile().deleteRecursively()
        }
    }

    @Test fun graphicalFailureIsVisibleInADialogAndStderr() {
        val strings = net.bible.sharedui.assembly.hostStringTable("en")
        val errors = mutableListOf<String>()
        val dialogs = mutableListOf<Pair<String, String>>()
        reportSecondLaunchFailure(strings, headless = false, stderr = errors::add, present = { title, text -> dialogs.add(title to text) })
        assertEquals(listOf(strings.text("desktop_activation_failed")), errors)
        assertEquals(listOf(strings.text("error_occurred") to errors.single()), dialogs)
    }

    @Test fun headlessFailureStillReportsToStderrWithoutOpeningADialog() {
        val errors = mutableListOf<String>()
        reportSecondLaunchFailure(net.bible.sharedui.assembly.hostStringTable("en"), headless = true,
            stderr = errors::add, present = { _, _ -> error("No dialogs in headless mode") })
        assertEquals(1, errors.size)
    }

    @Test fun dialogFailureDoesNotHideTheOriginalFailure() {
        val errors = mutableListOf<String>()
        reportSecondLaunchFailure(net.bible.sharedui.assembly.hostStringTable("en"), headless = false,
            stderr = errors::add, present = { _, _ -> error("Display unavailable") })
        assertEquals(1, errors.size)
    }

    @Test fun missingListenerReportsFailureWithinAFewSeconds() {
        val dir = Files.createTempDirectory("second-launch-missing")
        var reports = 0
        try {
            val started = System.nanoTime()
            assertEquals(1, handOffSecondLaunch(dir, emptyList()) { reports++ })
            assertEquals(1, reports)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5000)
        } finally { dir.toFile().deleteRecursively() }
    }

    @Test fun unresponsiveInstanceReportsFailureWithinAFewSeconds() {
        val dir = Files.createTempDirectory("second-launch-stuck")
        var reports = 0
        try {
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { server ->
                server.bind(UnixDomainSocketAddress.of(dir.resolve("instance.sock")))
                val started = System.nanoTime()
                assertEquals(1, handOffSecondLaunch(dir, emptyList()) { reports++ })
                assertEquals(1, reports, "A timeout must not silently exit")
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5000)
            }
        } finally { dir.toFile().deleteRecursively() }
    }
}
