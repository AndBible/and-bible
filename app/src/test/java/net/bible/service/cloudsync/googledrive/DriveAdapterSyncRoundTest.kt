package net.bible.service.cloudsync.googledrive

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.createTriggers
import net.bible.service.cloudsync.drive.fake.FakeDriveServer
import net.bible.service.cloudsync.dropTriggers
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.test.DatabaseResetter
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * A device on the old Android Drive adapter and a device on the shared Ktor adapter sync bookmarks through one
 * [FakeDriveServer]: the old device creates the sync folder and uploads a patch, the new one joins from the
 * initial file and the patch, uploads its own patch, and the old one receives it. The full sync steps run
 * (`initializeSync`, then `createAndUploadNewPatch` and `downloadAndApplyNewPatches` as `synchronize` runs them
 * for one category), with per-device databases as in `CloudSyncNamespaceTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DriveAdapterSyncRoundTest {
    private val files = mutableListOf<File>()
    private val dbs = mutableListOf<BookmarkDatabase>()

    @After
    fun tearDown() {
        dbs.forEach { runCatching { it.close() } }
        files.forEach { File(it.path).delete(); File(it.path + "-journal").delete() }
        testAppSettings().setBoolean("sync_enable_bookmarks", false)
        DatabaseResetter.resetDatabase()
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(120_000) { block() } }

    /** A device: its own bookmarks database file, device id and device name, and a [CloudSync] over [adapter]. */
    private inner class Device(name: String, val adapter: CloudAdapter) {
        val db: SyncableDatabaseAccessor<BookmarkDatabase>
        val dialogs = AppDialogController()
        val cloud: CloudSync

        init {
            val file = File.createTempFile("bookmarks-$name-", ".sqlite3", CommonUtils.tmpDir).also { files += it }
            fun open(path: String) = DatabaseContainer.instance.getBookmarkDb(path).also { dbs += it }
            db = SyncableDatabaseAccessor(open(file.absolutePath), ::open, { open(file.absolutePath) }, file.path,
                SyncableDatabaseDefinition.BOOKMARKS, deviceId = "device-$name")
            val realEnv = get<AppEnvironment>(AppEnvironment::class.java)
            val env = object : AppEnvironment by realEnv {
                override val packageName = "net.bible.android.activity"
                override val deviceIdentifier = "device-$name"
            }
            cloud = CloudSync(
                dialogs, testCoreStrings(), env, testAppSettings(),
                object : CloudAdapterFactory {
                    override val available = listOf("GOOGLE_DRIVE")
                    override fun displayName(id: String) = id
                    override fun create(id: String): CloudAdapter = adapter
                }, SyncScheduler { error("not scheduled") }, get(DocumentSyncStarter::class.java),
                DocumentSyncProvider { error("No document operations in this test") },
                get(net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java), null,
            )
        }

        suspend fun start() {
            assertEquals(true, cloud.signIn())
            dropTriggers(db)
            createTriggers(db)
        }

        private suspend fun step(name: String) {
            CloudSync::class.declaredFunctions.single { it.name == name }.apply { isAccessible = true }.callSuspend(cloud, db)
        }

        /** One category's part of `synchronize`: join (answering "fetch" if asked), upload own changes, apply the others'. */
        suspend fun sync() {
            answeringDialogs { cloud.initializeSync(db) }
            step("createAndUploadNewPatch")
            step("downloadAndApplyNewPatches")
        }

        private suspend fun answeringDialogs(block: suspend () -> Unit) {
            val job = CoroutineScope(Dispatchers.Default).async { block() }
            try {
                withTimeout(30_000) {
                    while (!job.isCompleted) {
                        shadowOf(Looper.getMainLooper()).idle()
                        dialogs.pending.value?.let { p ->
                            val r = p.request
                            dialogs.respond(p.id, when (r) {
                                is AppDialogRequest.Options -> AppDialogResult.Selected(r.options.single { it.value == "fetch" }.value)
                                else -> AppDialogResult.Ok
                            })
                        }
                        Thread.sleep(20)
                    }
                    job.await()
                }
            } finally {
                job.cancel()
            }
        }

        suspend fun addBookmark(): IdType {
            val bookmark = BookmarkEntities.BibleBookmarkWithNotes().bookmarkEntity
            db.localDb.bookmarkDao().insert(bookmark)
            return bookmark.id
        }

        suspend fun bookmarkIds(): Set<IdType> = db.localDb.bookmarkDao().allBookmarks().map { it.id }.toSet()
    }

    /** The old adapter's own signIn() needs an activity (One Tap); its account is already set, so it is skipped. */
    private fun oldSignedIn(server: FakeDriveServer): CloudAdapter {
        val old = oldDriveAdapter(server)
        return object : CloudAdapter by old { override suspend fun signIn() = true }
    }

    /**
     * Device A creates the sync folder and uploads a patch; B (empty database) joins from A's initial file and patch,
     * then adds its own bookmark; A receives it.
     */
    private suspend fun exchange(server: FakeDriveServer, a: Device, b: Device) {
        a.start(); b.start()
        a.sync() // creates the sync folder and uploads the initial file
        Thread.sleep(5)
        val first = a.addBookmark()
        Thread.sleep(5)
        a.sync() // uploads the first patch
        assertTrue("A uploaded a patch", server.requests.any { it.url.contains("uploadType=resumable") && it.body.decodeToString().contains("\"name\":\"1.") })

        Thread.sleep(5)
        b.sync() // joins from A's folder (fetch), then applies A's patch
        assertEquals(setOf(first), b.bookmarkIds())

        Thread.sleep(5)
        val second = b.addBookmark()
        Thread.sleep(5)
        b.sync()
        Thread.sleep(5)
        a.sync()
        assertEquals(setOf(first, second), a.bookmarkIds())
        assertEquals(setOf(first, second), b.bookmarkIds())
    }

    @Test fun oldDeviceCreatesNewDeviceJoins() = run {
        val server = FakeDriveServer()
        exchange(server, Device("a", oldSignedIn(server)), Device("b", newDriveAdapter(server)))
    }

    @Test fun newDeviceCreatesOldDeviceJoins() = run {
        val server = FakeDriveServer()
        exchange(server, Device("a", newDriveAdapter(server)), Device("b", oldSignedIn(server)))
    }
}
