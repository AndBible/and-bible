package net.bible.sharedui.host.ports

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedui.app.ports.CompletingDocumentInstallStarter
import net.bible.sharedui.app.ports.DocumentRef
import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionInstallStarterTest {
    @Test fun pendingAndFailingPermissionNeverBlocksSubmissions() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var submitted = 0
        val delegate = object : CompletingDocumentInstallStarter {
            override fun start(uris: List<DocumentRef>, action: String?) { submitted++ }
            override suspend fun startAndAwait(uris: List<DocumentRef>, action: String?) { submitted++ }
            override fun hostBound() = Unit
            override fun hostUnbound() = Unit
        }
        val starter = PermissionInstallStarter(delegate, backgroundScope) {
            calls++
            if (calls == 1) gate.await() else error("permission failure")
        }
        val files = listOf(DocumentRef("file:///test.zip"))
        starter.start(emptyList(), null)
        runCurrent()
        assertEquals(0, calls)
        starter.start(files, null)
        runCurrent()
        assertEquals(2, submitted)
        assertEquals(1, calls)
        starter.startAndAwait(files, null)
        runCurrent()
        assertEquals(3, submitted)
        assertEquals(2, calls)
        gate.complete(Unit)
    }
}
