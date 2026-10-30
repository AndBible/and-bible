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
}
