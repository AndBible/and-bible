package net.bible.sharedui.host.app

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.ports.DocumentRef
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test fun successfulRestoreReleasesWelcomeOnlyOnWelcome() {
        var released = 0
        afterIncomingRestore(NavRoutes.WELCOME) { released++ }
        afterIncomingRestore(NavRoutes.WELCOME + "?x=1") { released++ }
        afterIncomingRestore("reading") { released++ }
        afterIncomingRestore(null) { released++ }
        assertEquals(2, released)
    }
}
