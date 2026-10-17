package net.bible.service.llm

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class AiDatabaseLifetimeCommonTest {
    @Test
    fun replaceCancelsAnOwnedLazyJob() = runTest(timeout = 10.minutes) {
        val lifetime = AiDatabaseLifetime()
        val job = launch(start = CoroutineStart.LAZY) { awaitCancellation() }
        lifetime.ownUntilReplace(job)
        assertEquals(1, lifetime.ownedCountForTest())
        job.start()
        lifetime.replace { }
        assertTrue(job.isCancelled)
        assertEquals(0, lifetime.ownedCountForTest())
    }

    @Test
    fun aStaleTokenIsRejected() = runTest(timeout = 10.minutes) {
        val lifetime = AiDatabaseLifetime()
        var captured: AiDatabaseLifetime.Replacement? = null
        lifetime.replace { token -> captured = token; lifetime.requireOwner(token) }
        val stale = assertNotNull(captured)
        assertFailsWith<IllegalStateException> { lifetime.requireOwner(stale) }
        assertFailsWith<IllegalStateException> { AiDatabaseLifetime().requireOwner(stale) }
    }
}
