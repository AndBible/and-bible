package net.bible.sharedui.desktop.files

import java.nio.file.Paths
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import net.bible.sharedui.app.ports.DocumentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopIncomingFilesTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun productionFacadeQueuesBeforeSubscriptionAndFocusesOnMain() {
        val main = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "incoming-test-main") }
            .asCoroutineDispatcher()
        kotlinx.coroutines.Dispatchers.setMain(main)
        val dir = java.nio.file.Files.createTempDirectory("incoming-facade")
        val focus = java.util.concurrent.LinkedBlockingQueue<String>()
        val window = object : net.bible.sharedui.desktop.assembly.DesktopWindowHandle {
            override val sizeDp = androidx.compose.ui.unit.IntSize(1, 1)
            override val density = 1f
            override fun toFront() { focus.add(Thread.currentThread().name) }
        }
        val incoming = DesktopIncomingFilesImpl(dir, emptyList(), window, net.bible.sharedcore.platform.desktop.DesktopExitTasks())
        try {
            val file = dir.resolve("book.zip")
            assertTrue(net.bible.sharedcore.platform.desktop.files.InstanceHandOff.send(dir, listOf(file)))
            assertTrue(requireNotNull(focus.poll(3, java.util.concurrent.TimeUnit.SECONDS)).startsWith("incoming-test-main"))
            val deliveries = java.util.concurrent.LinkedBlockingQueue<Pair<String, () -> Unit>>()
            kotlinx.coroutines.runBlocking {
                val first = launch(main) {
                    incoming.receive({ refs, done -> deliveries.add(refs.single().uri to done) }, {})
                }
                val old = requireNotNull(deliveries.poll(3, java.util.concurrent.TimeUnit.SECONDS))
                first.cancel(); first.join()
                val second = launch(main) {
                    incoming.receive({ refs, done -> deliveries.add(refs.single().uri to done) }, {})
                }
                val current = requireNotNull(deliveries.poll(3, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals(file.toUri().toString(), current.first)
                kotlinx.coroutines.withContext(main) { old.second(); current.second() }
                second.cancel(); second.join()
            }
            assertTrue(net.bible.sharedcore.platform.desktop.files.InstanceHandOff.send(dir, emptyList()))
            assertTrue(requireNotNull(focus.poll(3, java.util.concurrent.TimeUnit.SECONDS)).startsWith("incoming-test-main"))
        } finally {
            incoming.close()
            kotlinx.coroutines.Dispatchers.resetMain()
            main.close()
        }
    }

    /**
     * A hand-off socket that cannot be bound (a path too long for AF_UNIX, a stale socket Windows keeps locked) must not
     * abort the app start: the app runs without a second-launch hand-off, and its own launch arguments still arrive.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun handOffListenFailureDegradesInsteadOfAbortingStartup() {
        val main = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "incoming-test-main") }
            .asCoroutineDispatcher()
        kotlinx.coroutines.Dispatchers.setMain(main)
        val dir = java.nio.file.Files.createTempDirectory("incoming-listen-failure")
        val window = object : net.bible.sharedui.desktop.assembly.DesktopWindowHandle {
            override val sizeDp = androidx.compose.ui.unit.IntSize(1, 1)
            override val density = 1f
            override fun toFront() {}
        }
        val file = dir.resolve("book.zip")
        val incoming = DesktopIncomingFilesImpl(
            dir, listOf(file.toString()), window, net.bible.sharedcore.platform.desktop.DesktopExitTasks(),
            listen = { _, _ -> throw java.io.IOException("Cannot remove the stale socket (still in use?)") },
        )
        try {
            val delivered = java.util.concurrent.LinkedBlockingQueue<String>()
            kotlinx.coroutines.runBlocking {
                val job = launch(main) {
                    incoming.receive({ refs, done -> delivered.add(refs.single().uri); done() }, {})
                }
                assertEquals(file.toUri().toString(), delivered.poll(3, java.util.concurrent.TimeUnit.SECONDS))
                job.cancel(); job.join()
            }
        } finally {
            incoming.close()
            kotlinx.coroutines.Dispatchers.resetMain()
            main.close()
        }
    }

    @Test fun relativeArgsResolvedAgainstCallerCwd() {
        val cwd = Paths.get("/tmp/caller")
        assertEquals(listOf(cwd.resolve("book.epub")), resolveIncomingArgs(listOf("book.epub"), cwd))
    }

    @Test fun unsupportedFileTypeIgnoredWithLog() {
        val messages = mutableListOf<String>()
        val queue = DesktopIncomingFileQueue { messages.add(it) }
        val delivered = mutableListOf<DocumentRef>()
        queue.bind({ refs, _ -> delivered.addAll(refs) }, {})
        queue.offer(listOf(Paths.get("/tmp/ignored.txt"), Paths.get("/tmp/book.epub")))
        assertEquals(listOf(DocumentRef(Paths.get("/tmp/book.epub").toUri().toString())), delivered)
        assertTrue(messages.any { it.contains("ignored.txt") })
    }

    @Test fun allInstallerFileFormatsAreDelivered() {
        val queue = DesktopIncomingFileQueue {}
        val seen = mutableListOf<String>()
        queue.bind({ refs, done -> seen.add(Paths.get(java.net.URI(refs.single().uri)).fileName.toString()); done() }, {})
        val names = listOf("a.zip", "a.epub", "a.ttf", "a.csv", "a.jpg", "a.jpeg", "a.png", "a.webp", "a.sqlite3", "a.mybible", "a.bblx", "a.bbli")
        queue.offer(names.map { Paths.get("/tmp", it) })
        assertEquals(names, seen)
    }

    @Test fun unacknowledgedFileSurvivesRebindingAndOldAck() {
        val queue = DesktopIncomingFileQueue {}
        queue.offer(listOf(Paths.get("/tmp/a.zip"), Paths.get("/tmp/b.zip")))
        val oldAcks = mutableListOf<() -> Unit>()
        val old = queue.bind({ _, done -> oldAcks.add(done) }, {})
        val seen = mutableListOf<String>()
        val newAcks = mutableListOf<() -> Unit>()
        queue.bind({ refs, done -> seen.add(refs.single().uri); newAcks.add(done) }, {})
        queue.detach(old)
        oldAcks.single()()
        assertEquals(listOf(Paths.get("/tmp/a.zip").toUri().toString()), seen)
        newAcks.single()()
        assertEquals(listOf("a.zip", "b.zip"), seen.map { Paths.get(java.net.URI(it)).fileName.toString() })
    }

    /** A second launch while the crash gate is up (no app graph yet) hands its file over; it is delivered once the graph attaches. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun filesHandedOverDuringCrashGateAreQueued() {
        val main = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "incoming-test-main") }
            .asCoroutineDispatcher()
        kotlinx.coroutines.Dispatchers.setMain(main)
        val dir = java.nio.file.Files.createTempDirectory("incoming-gate")
        val window = object : net.bible.sharedui.desktop.assembly.DesktopWindowHandle {
            override val sizeDp = androidx.compose.ui.unit.IntSize(1, 1)
            override val density = 1f
            override fun toFront() {}
        }
        val early = requireNotNull(DesktopEarlyIncomingFiles.install(dir))
        var incoming: DesktopIncomingFilesImpl? = null
        try {
            val file = dir.resolve("book.zip")
            // The gate is up: only the early listener exists.
            assertTrue(net.bible.sharedcore.platform.desktop.files.InstanceHandOff.send(dir, listOf(file)))
            // The app graph comes up and builds its facade over the same listener.
            incoming = DesktopIncomingFilesImpl(dir, emptyList(), window, net.bible.sharedcore.platform.desktop.DesktopExitTasks())
            val delivered = java.util.concurrent.LinkedBlockingQueue<String>()
            kotlinx.coroutines.runBlocking {
                val job = launch(main) { incoming.receive({ refs, done -> delivered.add(refs.single().uri); done() }, {}) }
                assertEquals(file.toUri().toString(), delivered.poll(3, java.util.concurrent.TimeUnit.SECONDS))
                job.cancel(); job.join()
            }
        } finally {
            incoming?.close()
            DesktopEarlyIncomingFiles.closeInstalled()
            kotlinx.coroutines.Dispatchers.resetMain()
            main.close()
        }
    }
}
