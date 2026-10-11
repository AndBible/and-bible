package net.bible.service.cloudsync.documents

import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.DocumentSyncPreferences
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSyncSettingsTest {
    private val scope = AppCoroutineScope()
    private val launcher = OrderedLauncher(scope)
    private val db get() = DatabaseContainer.instance.documentSyncDb
    private fun fresh(debug: Boolean = true): DocumentSyncSettings {
        val real = get<AppEnvironment>(AppEnvironment::class.java)
        val env = object : AppEnvironment by real { override val isDebugMode = debug }
        return DocumentSyncSettings({ db.documentSyncPreferencesDao() }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher, env)
    }

    @Before fun reset() = runBlocking {
        db.documentSyncPreferencesDao().clear()
        db.cloudListingStateDao().clear()
        db.cloudDocumentSyncTimestampDao().clear()
    }

    @After fun cleanup() = runBlocking {
        launcher.join("document-sync-settings")
        scope.coroutineContext[Job]!!.cancel()
        net.bible.test.DatabaseResetter.resetDatabase()
    }

    @Test fun databaseFixtureResetInvalidatesProcessSnapshot() = runBlocking {
        val processSettings = get<DocumentSyncSettings>(DocumentSyncSettings::class.java)
        processSettings.ensureLoaded()
        processSettings.enabled = true
        get<OrderedLauncher>(OrderedLauncher::class.java).join("document-sync-settings")
        net.bible.test.DatabaseResetter.resetDatabase()
        db.documentSyncPreferencesDao().set(DocumentSyncPreferences(enabled = false, wifiOnly = false))
        processSettings.ensureLoaded()
        assertFalse(processSettings.enabled)
        assertFalse(processSettings.wifiOnly)
    }

    @Test fun fixtureResetWaitsForInflightLoadAndProducerShutdown() = runBlocking {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val dao = db.documentSyncPreferencesDao()
        dao.set(DocumentSyncPreferences(enabled = true))
        val delayedDao = object : net.bible.android.database.DocumentSyncPreferencesDao by dao {
            override suspend fun get(): DocumentSyncPreferences? {
                val old = dao.get()
                started.complete(Unit)
                release.await()
                return old
            }
        }
        val settings = DocumentSyncSettings({ delayedDao }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher, get(AppEnvironment::class.java))
        val load = async(kotlinx.coroutines.Dispatchers.Default) { settings.ensureLoaded() }
        started.await()
        val resetStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val reset = async(kotlinx.coroutines.Dispatchers.Default) {
            resetStarted.complete(Unit)
            net.bible.test.DatabaseResetter.resetDocumentSyncFixture(settings, launcher) {
                org.junit.Assert.assertTrue("reset must exclude a suspended old-row load", release.isCompleted)
                dao.set(DocumentSyncPreferences(enabled = false, wifiOnly = false))
            }
        }
        resetStarted.await()
        kotlinx.coroutines.delay(100)
        release.complete(Unit)
        load.await()
        reset.await()
        settings.ensureLoaded()
        assertFalse(settings.enabled)
        assertFalse(settings.wifiOnly)
    }

    @Test fun fixtureResetWaitsForCancelledProducerFinallyWrite() = runBlocking {
        val processSettings = get<DocumentSyncSettings>(DocumentSyncSettings::class.java)
        processSettings.ensureLoaded()
        val producerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
        producerScope.launch {
            try {
                started.complete(Unit)
                kotlinx.coroutines.awaitCancellation()
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    kotlinx.coroutines.delay(100)
                    processSettings.enabled = true
                    finished.complete(Unit)
                }
            }
        }
        started.await()
        net.bible.test.DatabaseResetter.resetDatabase(producerScope)
        assertTrue("reset must await producer shutdown", finished.isCompleted)
        db.documentSyncPreferencesDao().set(DocumentSyncPreferences(enabled = false))
        processSettings.ensureLoaded()
        assertFalse(processSettings.enabled)
        get<OrderedLauncher>(OrderedLauncher::class.java).join("document-sync-settings")
        assertFalse(db.documentSyncPreferencesDao().get()!!.enabled)
    }

    @Test fun loadsExistingEnabledRowOnce() = runBlocking {
        db.documentSyncPreferencesDao().set(DocumentSyncPreferences(enabled = true))
        val settings = fresh()
        settings.ensureLoaded()
        assertTrue(settings.enabled)
        db.documentSyncPreferencesDao().set(DocumentSyncPreferences(enabled = false))
        settings.ensureLoaded()
        assertTrue(settings.enabled)
    }

    @Test fun setterIsImmediatelyVisibleAndWritesThrough() = runBlocking {
        val settings = fresh()
        settings.ensureLoaded()
        settings.enabled = true
        assertTrue(settings.enabled)
        launcher.join("document-sync-settings")
        assertTrue(db.documentSyncPreferencesDao().get()!!.enabled)
    }

    @Test fun consecutiveSettersKeepBothValuesOnDisk() = runBlocking {
        db.documentSyncPreferencesDao().set(DocumentSyncPreferences(wifiOnly = false))
        val settings = fresh()
        settings.ensureLoaded()
        settings.enabled = true
        settings.wifiOnly = true
        launcher.join("document-sync-settings")
        val row = db.documentSyncPreferencesDao().get()!!
        assertTrue(row.enabled)
        assertTrue(row.wifiOnly)
    }

    @Test(expected = IllegalStateException::class)
    fun readBeforeLoadFailsInDebug() { fresh().enabled }

    @Test fun releaseReadBeforeLoadReturnsDefaults() { assertFalse(fresh(debug = false).enabled) }

    @Test fun inFlightLoadDoesNotOverwriteImmediateReleaseSetter() = runBlocking {
        val readStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseRead = kotlinx.coroutines.CompletableDeferred<Unit>()
        val dao = db.documentSyncPreferencesDao()
        dao.set(DocumentSyncPreferences(enabled = false))
        val slowDao = object : net.bible.android.database.DocumentSyncPreferencesDao by dao {
            override suspend fun get(): DocumentSyncPreferences? {
                val old = dao.get()
                readStarted.complete(Unit)
                releaseRead.await()
                return old
            }
        }
        val real = get<AppEnvironment>(AppEnvironment::class.java)
        val settings = DocumentSyncSettings({ slowDao }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher,
            object : AppEnvironment by real { override val isDebugMode = false })
        val load = async(kotlinx.coroutines.Dispatchers.Default) { settings.ensureLoaded() }
        readStarted.await()
        settings.enabled = true
        assertTrue(settings.enabled)
        releaseRead.complete(Unit)
        load.await()
        launcher.join("document-sync-settings")
        assertTrue(settings.enabled)
        assertTrue(dao.get()!!.enabled)
    }

    @Test fun clearQueuesBehindWritesAndKeepsLaterSetter() = runBlocking {
        val settings = fresh()
        settings.ensureLoaded()
        settings.enabled = true
        settings.clearPreferences()
        assertFalse(settings.enabled)
        assertNull(db.documentSyncPreferencesDao().get())
        settings.autoUpload = false
        launcher.join("document-sync-settings")
        val row = db.documentSyncPreferencesDao().get()!!
        assertFalse(row.enabled)
        assertFalse(row.autoUpload)
    }

    @Test fun watermarkTimestampsAndBlockListRoundTrip() = runBlocking {
        val settings = fresh()
        settings.ensureLoaded()
        assertEquals(0L, settings.watermark())
        assertNull(settings.syncTimestamp("KJV"))
        settings.setWatermark(12345L)
        assertEquals(12345L, settings.watermark())
        settings.setSyncTimestamp("KJV", 99L)
        assertEquals(99L, settings.syncTimestamp("KJV"))
        settings.blockList.block("ESV")
        launcher.join("document-sync-settings")
        assertEquals(setOf("ESV"), db.documentSyncPreferencesDao().get()!!.blockList)
    }
}
