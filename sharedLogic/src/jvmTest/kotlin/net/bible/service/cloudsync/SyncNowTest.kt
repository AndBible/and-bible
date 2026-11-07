package net.bible.service.cloudsync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.event.EventSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncNowTest {
    private class Cloud : CloudSyncPort {
        override var signedIn = false
        var signInResult: Boolean? = true
        val calls = mutableListOf<String>()
        val source = EventSource<Boolean>()
        override val runningChanged get() = source
        val finish = CompletableDeferred<Unit>()
        var failure: Exception? = null
        override suspend fun signIn(): Boolean? { calls += "signIn"; return signInResult }
        override fun start(): Deferred<Boolean> { calls += "start"; return CompletableDeferred(true) }
        override suspend fun waitUntilFinished() { calls += "wait"; failure?.let { throw it }; finish.await() }
    }

    @Test fun startRunsSaveThenSignInThenStartThenWait() = runTest {
        val cloud = Cloud()
        val sync = SyncNow(cloud, EventSource(), { true }, { cloud.calls += "save" }, { true }, { false }, {}, this)
        sync.start()
        assertTrue(sync.running.value)
        runCurrent()
        assertEquals(listOf("save", "signIn", "start", "wait"), cloud.calls)
        cloud.finish.complete(Unit)
        runCurrent()
        assertFalse(sync.running.value)
        sync.close()
    }

    @Test fun signedInSkipsSignIn() = runTest {
        val cloud = Cloud().apply { signedIn = true; finish.complete(Unit) }
        val sync = SyncNow(cloud, EventSource(), { true }, { cloud.calls += "save" }, { true }, { false }, {}, this)
        sync.start(); runCurrent()
        assertEquals(listOf("save", "start", "wait"), cloud.calls)
        sync.close()
    }

    @Test fun signInRefusedStartsNothing() = runTest {
        for (result in listOf(false, null)) {
            val cloud = Cloud().apply { signInResult = result }
            val sync = SyncNow(cloud, EventSource(), { true }, { cloud.calls += "save" }, { true }, { false }, {}, this)
            sync.start(); runCurrent()
            assertEquals(listOf("save", "signIn"), cloud.calls)
            assertFalse(sync.running.value)
            sync.close()
        }
    }

    @Test fun secondStartWhileRunningIsNoOp() = runTest {
        val cloud = Cloud().apply { signedIn = true }
        val sync = SyncNow(cloud, EventSource(), { true }, { cloud.calls += "save" }, { true }, { false }, {}, this)
        sync.start(); sync.start(); runCurrent()
        assertEquals(listOf("save", "start", "wait"), cloud.calls)
        cloud.finish.complete(Unit); runCurrent()
        cloud.calls.clear(); cloud.source.emit(true); sync.start(); runCurrent()
        assertTrue(sync.running.value)
        assertEquals(emptyList(), cloud.calls)
        cloud.source.emit(false); sync.close()
    }

    @Test fun documentSyncRunningCountsAsRunning() = runTest {
        val cloud = Cloud()
        val documents = EventSource<Boolean>()
        val sync = SyncNow(cloud, documents, { true }, { cloud.calls += "save" }, { true }, { false }, {}, this)
        documents.emit(true)
        assertTrue(sync.running.value)
        sync.start(); runCurrent()
        assertEquals(emptyList(), cloud.calls)
        documents.emit(false); assertFalse(sync.running.value)
        sync.close()
    }

    @Test fun overlappingSourcesRemainRunningUntilBothFinish() = runTest {
        val cloud = Cloud()
        val documents = EventSource<Boolean>()
        val sync = SyncNow(cloud, documents, { true }, {}, { true }, { false }, {}, this)
        cloud.source.emit(true); documents.emit(true); cloud.source.emit(false)
        assertTrue(sync.running.value)
        documents.emit(false); assertFalse(sync.running.value)
        sync.close()
    }

    @Test fun closeUnsubscribes() = runTest {
        val cloud = Cloud()
        val documents = EventSource<Boolean>()
        val sync = SyncNow(cloud, documents, { true }, {}, { true }, { false }, {}, this)
        sync.close(); cloud.source.emit(true); documents.emit(true)
        assertFalse(sync.running.value)
    }

    @Test fun alreadyRunningAtConstructionBlocksStart() = runTest {
        val cloud = Cloud()
        val cloudState = MutableStateFlow(true)
        val documentState = MutableStateFlow(false)
        val sync = SyncNow(cloud, EventSource(), { true }, { cloud.calls += "save" }, { true }, { false }, {}, this,
            cloudRunningState = cloudState, documentRunningState = documentState)
        assertTrue(sync.running.value)
        sync.start(); runCurrent()
        assertEquals(emptyList(), cloud.calls)
        cloudState.value = false; documentState.value = true
        sync.start()
        assertEquals(emptyList(), cloud.calls)
        runCurrent()
        assertTrue(sync.running.value)
        sync.start(); runCurrent()
        assertEquals(emptyList(), cloud.calls)
        documentState.value = false; runCurrent()
        assertFalse(sync.running.value)
        sync.close()
    }

    @Test fun replacingBlocks() = runTest {
        val cloud = Cloud()
        val sync = SyncNow(cloud, EventSource(), { true }, { cloud.calls += "save" }, { true }, { true }, { cloud.calls += "offline" }, this)
        sync.start(); runCurrent()
        assertEquals(emptyList(), cloud.calls)
        sync.close()
    }

    @Test fun noNetworkShowsToastAndStartsNothing() = runTest {
        val cloud = Cloud()
        val sync = SyncNow(cloud, EventSource(), { true }, { cloud.calls += "save" }, { false }, { false }, { cloud.calls += "offline" }, this)
        sync.start(); runCurrent()
        assertEquals(listOf("offline"), cloud.calls)
        sync.close()
    }

    @Test fun runningResetsAfterFailure() = runTest {
        val cloud = Cloud().apply { signedIn = true; failure = IllegalStateException("broken") }
        val sync = SyncNow(cloud, EventSource(), { true }, {}, { true }, { false }, {}, this)
        sync.start(); runCurrent()
        assertEquals(listOf("start", "wait"), cloud.calls)
        assertFalse(sync.running.value)
        sync.close()
    }

    @Test fun availableFollowsSettings() = runTest {
        var enabled = false
        val sync = SyncNow(Cloud(), EventSource(), { enabled }, {}, { true }, { false }, {}, this)
        assertFalse(sync.available()); enabled = true; assertTrue(sync.available())
        sync.close()
    }
}
