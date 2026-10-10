package net.bible.service.cloudsync.documents

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
