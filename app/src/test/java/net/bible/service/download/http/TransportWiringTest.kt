package net.bible.service.download.http

import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.control.buildCoreModule
import net.bible.android.control.coreModule
import net.bible.android.control.speak.SpeakSettingsStore
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.platform.AndroidSpeakSettingsStore
import net.bible.android.platform.SuspendHttpTransport
import net.bible.service.sword.SwordEnvironmentInitialisation
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.http.DownloadHttp
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BooksListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.instance.InstanceFactory
import org.koin.core.module.Module
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** No automatic startup: the test must enter the real onCreate with no global Koin context. */
class ColdTransportApplication : BibleApplication() {
    override val isRunningTests = true
    override fun onCreate() = Unit
    fun startCold() = super.onCreate()
}

@RunWith(RobolectricTestRunner::class)
@Config(application = ColdTransportApplication::class, sdk = [TEST_SDK])
@OptIn(KoinInternalApi::class)
class TransportWiringTest {
    /** A module's own mappings after those of every module it includes, recursively (later wins, as in Koin). */
    private fun flatMappings(module: Module): Map<String, InstanceFactory<*>> =
        LinkedHashMap<String, InstanceFactory<*>>().also { out ->
            module.includedModules.forEach { out.putAll(flatMappings(it)) }
            out.putAll(module.mappings)
        }

    @Test
    fun coldStartupInstallsSharedTransportBeforePostKoinConsumers() {
        val app = BibleApplication.application as ColdTransportApplication
        val contextFields = listOf("_koin", "_koinApplication").map { name ->
            GlobalContext::class.java.getDeclaredField(name).apply { isAccessible = true }
        }
        val savedContext = contextFields.map { it.get(null) }
        val savedMappings = coreModule.mappings.toMap()
        val savedIncluded = coreModule.includedModules.toList()
        val swordLoaded = SwordEnvironmentInitialisation::class.java.getDeclaredField("isSwordLoaded").apply { isAccessible = true }
        val wasLoaded = swordLoaded.getBoolean(SwordEnvironmentInitialisation)
        val oldTransport = WebResource.transport
        val oldTimeout = WebResource.getTimeout()
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        val storeField = SpeakSettings.Companion::class.java.enclosingClass.getDeclaredField("store").apply { isAccessible = true }
        val oldStore = storeField.get(null)
        val launcherField = MyDocumentBookManager::class.java.getDeclaredField("launcher").apply { isAccessible = true }
        val oldLauncher = launcherField.get(MyDocumentBookManager)
        val listenersField = Class.forName("org.crosswire.jsword.book.AbstractBookList").getDeclaredField("listeners").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val listeners = listenersField.get(Books.installed()) as MutableList<BooksListener>
        val oldListeners = listeners.toList()
        val registeredField = net.bible.android.control.versification.BookInstallWatcher::class.java.getDeclaredField("registered").apply { isAccessible = true }
        val oldRegistered = registeredField.get(null)
        val reporterField = org.crosswire.common.util.Reporter::class.java.getDeclaredField("LISTENERS").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val reporters = reporterField.get(null) as MutableList<org.crosswire.common.util.ReporterListener>
        val oldReporters = reporters.toList()
        val progressManager = net.bible.service.device.ProgressNotificationManager.instance
        val progressFields = listOf("notificationManager", "workListener").map { name ->
            progressManager.javaClass.getDeclaredField(name).apply { isAccessible = true }
        }
        val oldProgressOwners = progressFields.map { it.get(progressManager) }
        val oldProgressSet = progressManager.progs
        val oldProgress = oldProgressSet.toSet()
        val workListenersField = org.crosswire.common.progress.JobManager::class.java
            .getDeclaredField("listeners").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val workListeners = workListenersField.get(null) as MutableList<org.crosswire.common.progress.WorkListener?>
        val oldWorkListeners = workListeners.toList()
        var detached = false
        var reachedPostKoinConsumer = false
        try {
            // Never stop the saved graph: coreModule factories cache services used by later tests.
            app.onTerminate()
            contextFields.forEach { it.set(null, null) }
            detached = true
            // buildCoreModule() only includes its platform and shared parts; flatten them into coreModule so the
            // boundary override below still wins.
            coreModule.mappings.clear()
            (coreModule.includedModules as MutableList<Module>).clear()
            coreModule.mappings.putAll(flatMappings(buildCoreModule()))
            val boundary = module {
                single<SpeakSettingsStore> {
                    reachedPostKoinConsumer = true
                    assertTrue("transport must be installed before the first post-Koin consumer", WebResource.transport is SuspendHttpTransport)
                    assertEquals(5000, WebResource.getTimeout())
                    AndroidSpeakSettingsStore()
                }
            }
            coreModule.mappings.putAll(boundary.mappings)
            swordLoaded.setBoolean(SwordEnvironmentInitialisation, false)
            WebResource.transport = org.crosswire.common.util.UrlConnectionHttpTransport()
            WebResource.setTimeout(1234)
            assertNull(GlobalContext.getOrNull())

            app.startCold()

            assertTrue("folder initialisation must succeed without Koin", swordLoaded.getBoolean(SwordEnvironmentInitialisation))
            assertTrue(reachedPostKoinConsumer)
            assertTrue(WebResource.transport is SuspendHttpTransport)
            val httpField = SuspendHttpTransport::class.java.getDeclaredField("http").apply { isAccessible = true }
            assertSame("JSword must use the host's shared DownloadHttp", GlobalContext.get().get<DownloadHttp>(), httpField.get(WebResource.transport))
        } finally {
            try {
                app.onTerminate()
                // Only this test's fresh factories/resources can be closed, never the saved graph.
                if (detached) {
                    try {
                        GlobalContext.getOrNull()?.let { koin ->
                            try {
                                koin.getOrNull<net.bible.android.platform.AndroidHttpClients>()?.downloadClient?.let { client ->
                                    client.close()
                                    kotlinx.coroutines.runBlocking {
                                        kotlinx.coroutines.withTimeout(5_000) {
                                            client.engine.coroutineContext[kotlinx.coroutines.Job]!!.join()
                                        }
                                    }
                                }
                            } finally {
                                // A failed engine shutdown must not leave the private scope running.
                                koin.getOrNull<net.bible.sharedcore.platform.AppCoroutineScope>()
                                    ?.coroutineContext?.get(kotlinx.coroutines.Job)?.let { job ->
                                        job.cancel()
                                        kotlinx.coroutines.runBlocking {
                                            kotlinx.coroutines.withTimeout(5_000) { job.join() }
                                        }
                                    }
                            }
                        }
                    } finally {
                        GlobalContext.stopKoin()
                    }
                }
            } finally {
                // Do not close the shared manager: close() cancels pre-existing progress jobs.
                workListeners.clear()
                workListeners.addAll(oldWorkListeners)
                progressFields.zip(oldProgressOwners).forEach { (field, owner) -> field.set(progressManager, owner) }
                oldProgressSet.clear()
                oldProgressSet.addAll(oldProgress)
                progressManager.progs = oldProgressSet
                coreModule.mappings.clear()
                coreModule.mappings.putAll(savedMappings)
                (coreModule.includedModules as MutableList<Module>).apply { clear(); addAll(savedIncluded) }
                contextFields.zip(savedContext).forEach { (field, value) -> field.set(null, value) }
                storeField.set(null, oldStore)
                launcherField.set(MyDocumentBookManager, oldLauncher)
                listeners.clear()
                listeners.addAll(oldListeners)
                registeredField.set(null, oldRegistered)
                reporters.clear()
                reporters.addAll(oldReporters)
                swordLoaded.setBoolean(SwordEnvironmentInitialisation, wasLoaded)
                WebResource.transport = oldTransport
                WebResource.setTimeout(oldTimeout)
                Thread.setDefaultUncaughtExceptionHandler(oldHandler)
            }
        }
        progressFields.zip(oldProgressOwners).forEach { (field, owner) ->
            assertSame("progress ${field.name} ownership must survive cold startup teardown", owner, field.get(progressManager))
        }
        assertEquals("startup must not leave extra progress callbacks", oldWorkListeners.size, workListeners.size)
        oldWorkListeners.zip(workListeners).forEach { (before, after) -> assertSame(before, after) }
        assertSame(oldProgressSet, progressManager.progs)
        assertEquals("pre-existing progress must not be cancelled or removed", oldProgress, progressManager.progs)
    }
}
