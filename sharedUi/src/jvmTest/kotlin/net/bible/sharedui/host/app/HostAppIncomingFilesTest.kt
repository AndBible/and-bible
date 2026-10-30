package net.bible.sharedui.host.app

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.bible.sharedui.app.ports.DocumentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HostAppIncomingFilesTest {
    private val events = mutableListOf<String>()
    private var completions = 0

    private fun TestScope.router(failClassify: Set<String> = emptySet(), failRestore: Set<String> = emptySet()) = IncomingFileRouter(
        scope = this,
        isBackup = { if (it.uri in failClassify) error("boom"); it.uri.endsWith(".abdb.zip") },
        restore = { events += "restore ${it.uri}"; if (it.uri in failRestore) error("restore failed") },
        install = { refs, complete -> events += "install ${refs.map { it.uri }}"; complete() },
    )

    @Test fun incomingBackupRoutesToRestore() = runTest(StandardTestDispatcher()) {
        router().route(listOf(DocumentRef("file:///a/b.abdb.zip"))) { completions++ }
        advanceUntilIdle()
        assertEquals(listOf("restore file:///a/b.abdb.zip"), events)
        assertEquals(1, completions)
    }

    @Test fun incomingModuleZipRoutesToInstall() = runTest(StandardTestDispatcher()) {
        router().route(listOf(DocumentRef("file:///a/kjv.zip"))) { completions++ }
        advanceUntilIdle()
        assertEquals(listOf("install [file:///a/kjv.zip]"), events)
        assertEquals(1, completions)
    }

    @Test fun mixedIncomingFilesEachRoutedOnce() = runTest(StandardTestDispatcher()) {
        router().route(listOf("file:///1.zip", "file:///2.abdb.zip", "file:///3.epub", "file:///4.abdb.zip").map(::DocumentRef)) { completions++ }
        advanceUntilIdle()
        assertEquals(listOf("restore file:///2.abdb.zip", "restore file:///4.abdb.zip", "install [file:///1.zip, file:///3.epub]"), events)
        assertEquals(1, completions)
    }

    @Test fun completionWaitsForRestoreAndSurvivesFailures() = runTest(StandardTestDispatcher()) {
        router(failRestore = setOf("file:///x.abdb.zip"), failClassify = setOf("file:///odd.zip"))
            .route(listOf("file:///x.abdb.zip", "file:///odd.zip").map(::DocumentRef)) { events += "complete" }
        assertEquals(emptyList(), events) // nothing happens before the coroutine runs
        advanceUntilIdle()
        // A failed restore does not stop the batch; an unclassifiable file still reaches install.
        assertEquals(listOf("restore file:///x.abdb.zip", "install [file:///odd.zip]", "complete"), events)
    }

    @Test fun filePathIsDecodedFromFileUri() {
        assertEquals("/home/u/My Files/ä.abdb.zip", incomingFilePath(DocumentRef("file:///home/u/My%20Files/%C3%A4.abdb.zip")))
        assertEquals("/tmp/x.zip", incomingFilePath(DocumentRef("/tmp/x.zip")))
        assertEquals("/tmp/x.zip", incomingFilePath(DocumentRef("file:/tmp/x.zip")))
        assertNull(incomingFilePath(DocumentRef("content://x/y")))
        assertNull(incomingFilePath(DocumentRef("file://host/x")))
    }
}
