package net.bible.sharedui.desktop.files

import java.nio.file.Paths
import net.bible.sharedui.app.ports.DocumentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopIncomingFilesTest {
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
