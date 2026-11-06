package net.bible.android.control.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedlogic.addons.fakeAddonBook
import org.crosswire.jsword.book.install.DownloadCancelledException
import org.crosswire.jsword.book.install.InstallException
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #3973: a repository download requested during onboarding was lost without a trace when the app was
 * backgrounded or terminated mid-transfer. The queue must remember a requested install until it reaches an outcome
 * the user has seen (installed, error shown, user cancel), so the next launch can report it as not installed.
 */
class DownloadQueueInterruptionTest {
    private class MapPrefs : DeviceLocalPrefs {
        val map = mutableMapOf<String, Any>()
        override fun getString(key: String, default: String?) = map[key] as? String ?: default
        override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
        val durableKeys = mutableListOf<String>()
        override fun setString(key: String, value: String) { map[key] = value }
        override fun setStringDurably(key: String, value: String) { durableKeys.add(key); setString(key, value) }
        override fun setBoolean(key: String, value: Boolean) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private val errors = mutableListOf<String>()
    private val notifier = object : UserNotifier {
        override fun showInfo(message: String, long: Boolean) = Unit
        override fun showError(message: String, cause: Throwable?) { errors.add(message) }
    }
    private val strings = Proxy.newProxyInstance(CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java)) { _, method, _ ->
        if (method.returnType == String::class.java) "text:${method.name}" else null
    } as CoreStrings

    private val prefs = MapPrefs()
    private val kjv = fakeAddonBook("KJV", null)
    private val kjvRequest = PendingRepoInstall("CrossWire", "KJV")

    private fun queue(statuses: MutableList<DocumentStatus> = mutableListOf()) =
        DownloadQueue(statuses::add, notifier, strings, PendingRepoInstalls(prefs))

    /** A fresh store over the same device prefs: what the next process sees. */
    private fun nextLaunch() = PendingRepoInstalls(prefs)

    @Test
    fun transferCancelledWithItsScopeIsStillPendingAtTheNextLaunch() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            queue().addDocumentToDownloadQueue(kjv, "CrossWire") {
                started.complete(Unit)
                CompletableDeferred<Unit>().await() // the transfer never finishes before the app goes away
            }
        }
        withTimeout(10_000) { started.await() }
        job.cancelAndJoin()

        assertEquals(listOf(kjvRequest), nextLaunch().interrupted(isInstalled = { false }))
    }

    @Test
    fun transferInFlightWhenTheProcessDiesIsPendingAtTheNextLaunch() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            queue().addDocumentToDownloadQueue(kjv, "CrossWire") {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
        }
        withTimeout(10_000) { started.await() }
        // The process is killed here: nothing after this point runs in the old process.
        assertEquals(listOf(kjvRequest), nextLaunch().interrupted(isInstalled = { false }))
        job.cancelAndJoin()
    }

    @Test
    fun completedInstallIsNotPending() = runBlocking {
        val statuses = mutableListOf<DocumentStatus>()
        queue(statuses).addDocumentToDownloadQueue(kjv, "CrossWire") {}
        assertEquals(DocumentStatus.DocumentInstallStatus.INSTALLED, statuses.single().documentInstallStatus)
        assertEquals(emptyList(), nextLaunch().interrupted(isInstalled = { false }))
    }

    @Test
    fun failedInstallIsReportedNowAndNotAgainAtTheNextLaunch() = runBlocking {
        val statuses = mutableListOf<DocumentStatus>()
        queue(statuses).addDocumentToDownloadQueue(kjv, "CrossWire") { throw InstallException("broken zip") }
        assertEquals(DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, statuses.single().documentInstallStatus)
        assertTrue(errors.isNotEmpty(), "the failure is reported when it happens")
        assertEquals(emptyList(), nextLaunch().interrupted(isInstalled = { false }))
    }

    @Test
    fun userCancelledInstallIsNotPending() = runBlocking {
        queue().addDocumentToDownloadQueue(kjv, "CrossWire") { throw DownloadCancelledException(java.net.URI("https://example.org/kjv.zip")) }
        assertEquals(emptyList(), nextLaunch().interrupted(isInstalled = { false }))
    }

    @Test
    fun pendingRequestWhoseModuleIsInstalledIsDropped() {
        PendingRepoInstalls(prefs).add(kjvRequest)
        PendingRepoInstalls(prefs).add(PendingRepoInstall("Lockman (CrossWire)", "NASB"))
        assertEquals(listOf(kjvRequest), nextLaunch().interrupted(isInstalled = { it == "NASB" }))
        // The installed one is forgotten for good, not only filtered from this answer.
        assertEquals(listOf(kjvRequest), nextLaunch().interrupted(isInstalled = { false }))
    }

    @Test
    fun forgetRemovesOnlyTheGivenRequests() {
        val store = PendingRepoInstalls(prefs)
        val nasb = PendingRepoInstall("Lockman (CrossWire)", "NASB")
        store.add(kjvRequest)
        store.add(nasb)
        store.forget(listOf(kjvRequest))
        assertEquals(listOf(nasb), nextLaunch().interrupted(isInstalled = { false }))
    }

    @Test
    fun unreadableStoredValueIsTreatedAsEmpty() {
        prefs.setString(PendingRepoInstalls.KEY, "{not json")
        assertEquals(emptyList(), nextLaunch().interrupted(isInstalled = { false }))
    }

    @Test
    fun addIsWrittenDurablyBeforeTheTransferStarts() {
        PendingRepoInstalls(prefs).add(kjvRequest)
        assertEquals(listOf(PendingRepoInstalls.KEY), prefs.durableKeys)
    }

    @Test
    fun requestMissingFromALoadedCatalogueIsForgotten() {
        val store = PendingRepoInstalls(prefs)
        val nasb = PendingRepoInstall("Lockman (CrossWire)", "NASB")
        store.add(kjvRequest)
        store.add(nasb)
        // The catalogue has NASB but no longer KJV from CrossWire (module removed or repository renamed).
        store.forgetNotInCatalogue(listOf(kjvRequest, nasb), listOf(nasb, PendingRepoInstall("CrossWire", "ESV")))
        assertEquals(listOf(nasb), nextLaunch().interrupted(isInstalled = { false }))
    }

    @Test
    fun requestIsKeptWhenTheCatalogueDidNotLoad() {
        val store = PendingRepoInstalls(prefs)
        store.add(kjvRequest)
        store.forgetNotInCatalogue(listOf(kjvRequest), emptyList())
        assertEquals(listOf(kjvRequest), nextLaunch().interrupted(isInstalled = { false }))
    }

    @Test
    fun racingDuplicateRequestsRunOneTransferAndKeepTheRecordUntilItIsSettled() = runBlocking {
        val transfers = java.util.concurrent.atomic.AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val q = queue()
        val jobs = List(32) {
            launch(Dispatchers.Default) {
                q.addDocumentToDownloadQueue(kjv, "CrossWire") { transfers.incrementAndGet(); gate.await() }
            }
        }
        withTimeout(10_000) { while (transfers.get() == 0) kotlinx.coroutines.yield() }
        kotlinx.coroutines.delay(300) // let every duplicate reach the queue (and, if it is not atomic, start)
        assertEquals(1, transfers.get(), "duplicates must not start a second transfer")
        assertEquals(listOf(kjvRequest), nextLaunch().interrupted(isInstalled = { false }))
        gate.complete(Unit)
        jobs.forEach { it.join() }
        assertEquals(emptyList(), nextLaunch().interrupted(isInstalled = { false }))
    }
}
