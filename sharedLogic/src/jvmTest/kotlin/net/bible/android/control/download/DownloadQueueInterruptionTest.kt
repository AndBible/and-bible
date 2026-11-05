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
        override fun setString(key: String, value: String) { map[key] = value }
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
        queue().addDocumentToDownloadQueue(kjv, "CrossWire") { throw InstallException("broken zip") }
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
}
