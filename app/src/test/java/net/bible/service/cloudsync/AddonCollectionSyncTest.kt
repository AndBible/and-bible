/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.cloudsync

import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.DurableTestBibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.isAddonIdSql
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.service.addons.collections.AddonCollectionImporter
import net.bible.service.addons.collections.addonId
import net.bible.service.cloudsync.merge.prepareMergeSnapshot
import net.bible.service.cloudsync.webdav.FakeDavServer
import net.bible.service.cloudsync.webdav.*
import net.bible.service.common.ArchiveUtils
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.exec
import net.bible.service.db.importDatabaseFile
import net.bible.service.db.openSqlite
import net.bible.service.db.queryLong
import net.bible.service.db.sqliteDriverFactory
import net.bible.service.db.useWriterConnectionMarked
import net.bible.android.control.backup.BackupControl
import net.bible.service.installzip.DefaultInstallCommitter
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.webdav.*
import net.bible.sharedlogic.platform.CollectionPackRef
import net.bible.test.DatabaseResetter
import okio.Path
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Add-on collection rows (#4162) never leave a device through sync: not in `LogEntry`, not in patches, not in the
 * initial upload snapshot, not in merge snapshots, and an incoming patch cannot change them. No user row references
 * one: the app refuses to make such a reference (AddonCollectionBookmarkControlTest), and one that an older build or
 * another device made ([addRogueRefs]) is stripped from every patch, snapshot and import, in both directions, so it
 * cannot create the association anywhere. Devices B (the app's own container, real CloudSync flows against the
 * in-process WebDAV fake) and standalone devices for the patch exchange.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = DurableTestBibleApplication::class, sdk = [TEST_SDK])
class AddonCollectionSyncTest {
    private val dialogs: AppDialogController get() = get(AppDialogController::class.java)
    @get:Rule val dialogReset = AppDialogControllerResetRule()
    private val testDispatcher = StandardTestDispatcher()

    private val env get() = get<AppEnvironment>(AppEnvironment::class.java)
    private val settings get() = get<AppSettings>(AppSettings::class.java)
    private val cloud get() = get<CloudSync>(CloudSync::class.java)
    private val category = SyncableDatabaseDefinition.BOOKMARKS

    private val server = FakeDavServer(nowServer = System.currentTimeMillis())
    private val davState = object : WebDavStateStore { override var propagation = Propagation.UNKNOWN; override var certPin: CertPin? = null }
    private val davUi = object : WebDavSignInUi {
        override suspend fun confirmCertificate(info: DavCertificateInfo) = true
        override fun message(kind: WebDavMessage) = kind.name
    }
    private fun adapter(deviceId: String) = WebDavCloudAdapter(
        WebDavConfig("https://h/dav/", "me", "pw", "AndBible"), davState, davUi, deviceId = deviceId,
        clientFactory = { c, _ -> WebDavClient(createWebDavHttpClient(server.engine, c.username, c.password), c.serverUrl, c.username, c.password, { server.nowServer }) },
        nowMs = { server.nowServer },
    )
    private val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
    private var originalAdapter: Any? = null
    private val files = mutableListOf<File>()
    private val opened = mutableListOf<SyncableDatabaseAccessor<*>>()

    private val module = "Manna"
    private val srcLabel = IdType("11111111-0000-4000-8000-000000000001")
    private val srcB1 = IdType("22222222-0000-4000-8000-000000000001")
    private val srcB2 = IdType("22222222-0000-4000-8000-000000000002")
    private val srcEntry = IdType("33333333-0000-4000-8000-000000000001")
    private val addonLabel = addonId(module, srcLabel)
    private val addonB1 = addonId(module, srcB1)

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        Dispatchers.setMain(testDispatcher)
        originalAdapter = adapterField.get(cloud)
        category.setSyncEnabled(settings, true)
    }

    @After fun tearDown() {
        adapterField.set(cloud, originalAdapter)
        category.setSyncEnabled(settings, false)
        dialogs.cancelAll()
        Dispatchers.resetMain()
        opened.forEach { runCatching { it.localDb.close() } }
        files.forEach { File(it.path).delete(); File(it.path + "-journal").delete() }
        DatabaseResetter.resetDatabase()
    }

    private fun hex(id: IdType) = "X'${id.toHex()}'"
    private fun tmp(prefix: String, suffix: String = ".sqlite3") = File.createTempFile(prefix, suffix, CommonUtils.tmpDir).also { files += it }

    private fun open(path: String): BookmarkDatabase = Room.databaseBuilder(application, BookmarkDatabase::class.java, path)
        .addMigrations(*bookmarkMigrations)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .setDriver(sqliteDriverFactory())
        .build()

    /** A standalone device with sync triggers. */
    private suspend fun device(name: String = UUID.randomUUID().toString()): SyncableDatabaseAccessor<BookmarkDatabase> {
        val file = tmp("addon-sync-$name-")
        var db = open(file.absolutePath)
        val dbDef = SyncableDatabaseAccessor(db, { open(it) }, { db.close(); db = open(file.absolutePath); db }, file.path, category, deviceId = name)
        dropTriggers(dbDef)
        createTriggers(dbDef)
        return dbDef.also { opened += it }
    }

    /** The collection module's file: one label with two verses (one with a note) and a StudyPad text entry. */
    private val collectionFile: Path by lazy {
        val file = tmp("collection-")
        file.delete()
        val db = open(file.absolutePath)
        kotlinx.coroutines.runBlocking {
            db.useWriterConnectionMarked { c ->
                c.exec("INSERT INTO Label (id, name, color) VALUES (${hex(srcLabel)}, 'Comfort', 5)")
                for ((id, ord) in listOf(srcB1 to 100, srcB2 to 200)) {
                    c.exec("INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt) VALUES (${hex(id)}, $ord, $ord, $ord, $ord, 'KJV', 1)")
                    c.exec("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (${hex(id)}, ${hex(srcLabel)})")
                }
                c.exec("INSERT INTO BibleBookmarkNotes (bookmarkId, notes) VALUES (${hex(srcB1)}, 'Fear not')")
                c.exec("INSERT INTO StudyPadTextEntry (id, labelId, orderNumber, indentLevel) VALUES (${hex(srcEntry)}, ${hex(srcLabel)}, 2, 0)")
                c.exec("INSERT INTO StudyPadTextEntryText (studyPadTextEntryId, text) VALUES (${hex(srcEntry)}, 'Intro')")
            }
        }
        db.close()
        file.toOkioPath()
    }

    private suspend fun install(db: BookmarkDatabase, version: String = "1.0") =
        AddonCollectionImporter({ db }, env).replace(CollectionPackRef(module, "Manna verses", version, collectionFile), "hash-$version")

    private suspend fun uninstall(db: BookmarkDatabase) = AddonCollectionImporter({ db }, env).remove(module)

    private val mineLabel = IdType("01020304-0506-4708-890a-0b0c0d0e0f10")

    /** The user's own bookmark on b1's range (a collection verse), in the own label "Mine": what "Bookmark this verse" makes. */
    private suspend fun addOwnBookmark(db: BookmarkDatabase): IdType {
        val own = IdType()
        db.useWriterConnectionMarked { c ->
            c.exec("INSERT INTO Label (id, name) VALUES (${hex(mineLabel)}, 'Mine')")
            c.exec("INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt, primaryLabelId) VALUES (${hex(own)}, 100, 100, 100, 100, 'KJV', 5, ${hex(mineLabel)})")
            c.exec("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (${hex(own)}, ${hex(mineLabel)})")
        }
        return own
    }

    /**
     * User rows that reference add-on rows, written past BookmarkControl (as an older build, with per-bookmark copies,
     * or another device's database could hold them): a user bookmark with the add-on label as primary label and a link
     * to it (a Bible and a generic one), and a user StudyPad text entry in the add-on StudyPad. All logged as the
     * user's own changes. Returns the Bible bookmark's, the entry's and the generic bookmark's ids.
     */
    private data class Rogue(val bookmark: IdType, val entry: IdType, val generic: IdType)

    private suspend fun addRogueRefs(db: BookmarkDatabase): Rogue {
        val rogue = IdType()
        val entry = IdType()
        val generic = IdType()
        db.useWriterConnectionMarked { c ->
            c.exec("PRAGMA foreign_keys=OFF")
            c.exec("INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt, primaryLabelId) VALUES (${hex(rogue)}, 200, 200, 200, 200, 'KJV', 6, ${hex(addonLabel)})")
            c.exec("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (${hex(rogue)}, ${hex(addonLabel)})")
            c.exec("INSERT INTO StudyPadTextEntry (id, labelId, orderNumber, indentLevel) VALUES (${hex(entry)}, ${hex(addonLabel)}, 9, 0)")
            c.exec("INSERT INTO StudyPadTextEntryText (studyPadTextEntryId, text) VALUES (${hex(entry)}, 'My own thoughts')")
            c.exec("INSERT INTO GenericBookmark (id, key, createdAt, bookInitials, primaryLabelId) VALUES (${hex(generic)}, 'gen', 6, 'MannaSample', ${hex(addonLabel)})")
            c.exec("INSERT INTO GenericBookmarkToLabel (bookmarkId, labelId) VALUES (${hex(generic)}, ${hex(addonLabel)})")
            c.exec("PRAGMA foreign_keys=ON")
        }
        return Rogue(rogue, entry, generic)
    }

    /** User rows referencing an add-on id, and log entries naming one, in [count]'s database. */
    private suspend fun userRefsToAddonIds(count: suspend (String) -> Long): Long {
        fun addon(column: String) = isAddonIdSql(column)
        fun own(column: String) = "NOT (${addon(column)})"
        return listOf(
            "SELECT COUNT(*) FROM BibleBookmarkToLabel WHERE ${own("bookmarkId")} AND ${addon("labelId")}",
            "SELECT COUNT(*) FROM BibleBookmark WHERE ${own("id")} AND primaryLabelId IS NOT NULL AND ${addon("primaryLabelId")}",
            "SELECT COUNT(*) FROM StudyPadTextEntry WHERE ${own("id")} AND ${addon("labelId")}",
            "SELECT COUNT(*) FROM GenericBookmarkToLabel WHERE ${own("bookmarkId")} AND ${addon("labelId")}",
            "SELECT COUNT(*) FROM GenericBookmark WHERE ${own("id")} AND primaryLabelId IS NOT NULL AND ${addon("primaryLabelId")}",
            "SELECT COUNT(*) FROM LogEntry WHERE tableName IN ('StudyPadTextEntry','StudyPadTextEntryText') AND entityId1 IN " +
                "(SELECT id FROM StudyPadTextEntry WHERE ${own("id")} AND ${addon("labelId")})",
            "SELECT COUNT(*) FROM LogEntry WHERE ${addon("entityId2")}",
        ).sumOf { count(it) }
    }

    private val addonTag = "X'ADD08C01A011'"

    /** Add-on rows (first sync key tagged) and their log entries in a database file or connection. */
    private fun addonRowsIn(file: String): Long = openSqlite(file).let { c ->
        try {
            category.tables.sumOf { t -> c.queryLong("SELECT COUNT(*) FROM ${t.tableName} WHERE substr(${t.idField1}, 5, 6) = $addonTag")!! } +
                c.queryLong("SELECT COUNT(*) FROM LogEntry WHERE substr(entityId1, 5, 6) = $addonTag")!!
        } finally { c.close() }
    }

    /** [addonRowsIn] through the database's own (Room) connection, for the container's live database. */
    private suspend fun addonRowsIn(db: SyncableDatabaseAccessor<*>): Long = db.localDb.useWriterConnectionMarked { c ->
        category.tables.sumOf { t -> c.queryLong("SELECT COUNT(*) FROM ${t.tableName} WHERE substr(${t.idField1}, 5, 6) = $addonTag")!! } +
            c.queryLong("SELECT COUNT(*) FROM LogEntry WHERE substr(entityId1, 5, 6) = $addonTag")!!
    }

    /** A count on a live device database, through its own connection (a raw second connection may meet its lock). */
    private suspend fun countIn(db: SyncableDatabaseAccessor<*>, sql: String): Long = db.localDb.useWriterConnectionMarked { it.queryLong(sql)!! }

    private fun countIn(file: String, sql: String): Long = openSqlite(file).let { c -> try { c.queryLong(sql)!! } finally { c.close() } }

    private suspend fun addonLogEntries(dbDef: SyncableDatabaseAccessor<*>) =
        dbDef.dao.allLogEntries().count { it.entityId1.toHex().substring(8, 20).uppercase() == "ADD08C01A011" }

    private fun gunzip(path: Path): String = tmp("gunzip-").also { ArchiveUtils.gunzipFile(path, it.toOkioPath()) }.path

    private fun <T> run(block: suspend () -> T) = kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(120_000) { block() } }

    @Test
    fun logAndPatchesNeverHoldAddonRowsAndBothDevicesEndWithoutDuplicates() = run {
        val d1 = device("d1")
        val d2 = device("d2")
        install(d1.localDb); install(d2.localDb)
        val own = addOwnBookmark(d1.localDb)
        install(d1.localDb, "2.0")
        assertEquals(0, addonLogEntries(d1))

        val patch1 = createPatchForDatabase(d1, env)!!
        val patchFile = gunzip(patch1)
        assertEquals("no add-on row or log entry in the patch", 0, addonRowsIn(patchFile))
        assertEquals(1, countIn(patchFile, "SELECT COUNT(*) FROM BibleBookmark WHERE id = ${hex(own)}"))

        applyPatchesForDatabase(d2, env, patch1)
        val d2db = d2.localDb
        assertEquals(listOf("Comfort", "Mine"), d2db.bookmarkDao().allLabelsSortedByName().map { it.name }.sorted())
        assertEquals("the collection's two verses and the user's bookmark beside one of them", 3, countIn(d2, "SELECT COUNT(*) FROM BibleBookmark"))
        assertEquals(0, addonLogEntries(d2))
        assertEquals(0, userRefsToAddonIds { countIn(d2, it) })

        uninstall(d1.localDb)
        assertEquals(0, addonLogEntries(d1))
        assertEquals("module removal is not sent", null, createPatchForDatabase(d1, env))
    }

    @Test
    fun deviceWithoutTheModuleGetsNothingButTheUsersOwnRows() = run {
        val d1 = device("d1")
        val d2 = device("d2")
        install(d1.localDb)
        val own = addOwnBookmark(d1.localDb)
        applyPatchesForDatabase(d2, env, createPatchForDatabase(d1, env))
        assertEquals(0, addonRowsIn(d2))
        assertEquals(listOf("Mine"), d2.localDb.bookmarkDao().allLabelsSortedByName().map { it.name })
        assertEquals(1, countIn(d2, "SELECT COUNT(*) FROM BibleBookmark WHERE id = ${hex(own)} AND primaryLabelId = ${hex(mineLabel)}"))
        assertEquals(0, userRefsToAddonIds { countIn(d2, it) })
        // Installing the module there later adds the collection beside the user's bookmark, which it does not touch.
        install(d2.localDb)
        assertEquals(3, countIn(d2, "SELECT COUNT(*) FROM BibleBookmark"))
        assertEquals(0, userRefsToAddonIds { countIn(d2, it) })
    }

    /**
     * Receiving-side robustness: user rows that reference add-on rows (an older build's per-bookmark copy, another
     * device) never make the association. This device does not send them, and a patch that still carries them (forged
     * here) is applied without them: where the module is installed they would pass the foreign-key check, and where it
     * is not the plain clean-up would delete the whole user bookmark instead of only its add-on primary label.
     */
    @Test
    fun noPatchCanMakeAUserRowReferenceAnAddonRow() = run {
        val d1 = device("d1")
        install(d1.localDb)
        val (rogue, entry, generic) = addRogueRefs(d1.localDb)
        val sent = gunzip(createPatchForDatabase(d1, env)!!)
        assertEquals("this device sends no reference to an add-on row", 0, userRefsToAddonIds { countIn(sent, it) })
        assertEquals("the user's bookmark itself is sent", 1, countIn(sent, "SELECT COUNT(*) FROM BibleBookmark WHERE id = ${hex(rogue)} AND primaryLabelId IS NULL"))

        // A patch that carries them anyway: the references and their log entries, as a device without the strip sends.
        val later = System.currentTimeMillis() + 100_000
        openSqlite(sent).also { c ->
            try {
                c.exec("PRAGMA foreign_keys=OFF")
                c.exec("UPDATE BibleBookmark SET primaryLabelId = ${hex(addonLabel)} WHERE id = ${hex(rogue)}")
                c.exec("INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (${hex(rogue)}, ${hex(addonLabel)})")
                c.exec("INSERT INTO LogEntry VALUES ('BibleBookmarkToLabel', ${hex(rogue)}, ${hex(addonLabel)}, 'UPSERT', $later, 'd1')")
                c.exec("INSERT INTO StudyPadTextEntry (id, labelId, orderNumber, indentLevel) VALUES (${hex(entry)}, ${hex(addonLabel)}, 9, 0)")
                c.exec("INSERT INTO StudyPadTextEntryText (studyPadTextEntryId, text) VALUES (${hex(entry)}, 'My own thoughts')")
                c.exec("INSERT INTO LogEntry VALUES ('StudyPadTextEntry', ${hex(entry)}, '', 'UPSERT', $later, 'd1')")
                c.exec("INSERT INTO LogEntry VALUES ('StudyPadTextEntryText', ${hex(entry)}, '', 'UPSERT', $later, 'd1')")
                c.exec("UPDATE GenericBookmark SET primaryLabelId = ${hex(addonLabel)} WHERE id = ${hex(generic)}")
                c.exec("INSERT INTO GenericBookmarkToLabel (bookmarkId, labelId) VALUES (${hex(generic)}, ${hex(addonLabel)})")
                c.exec("INSERT INTO LogEntry VALUES ('GenericBookmarkToLabel', ${hex(generic)}, ${hex(addonLabel)}, 'UPSERT', $later, 'd1')")
            } finally { c.close() }
        }
        assertEquals("bible link + primary label, generic link + primary label, entry, its 2 log entries, 2 link log entries", 9, userRefsToAddonIds { countIn(sent, it) })
        val forged = tmp("forged-", ".gz").toOkioPath()
        ArchiveUtils.gzipFile(File(sent).toOkioPath(), forged)

        val withModule = device("with-module")
        install(withModule.localDb)
        val withoutModule = device("without-module")
        for (d in listOf(withModule, withoutModule)) {
            applyPatchesForDatabase(d, env, forged)
            assertEquals(d.deviceId, 0, userRefsToAddonIds { countIn(d, it) })
            assertEquals("${d.deviceId}: the user's bookmark arrives, without the add-on primary label", 1,
                countIn(d, "SELECT COUNT(*) FROM BibleBookmark WHERE id = ${hex(rogue)} AND primaryLabelId IS NULL"))
            assertEquals("${d.deviceId}: the generic bookmark arrives without the add-on primary label", 1,
                countIn(d, "SELECT COUNT(*) FROM GenericBookmark WHERE id = ${hex(generic)} AND primaryLabelId IS NULL"))
            assertEquals(d.deviceId, 0, countIn(d, "SELECT COUNT(*) FROM StudyPadTextEntryText WHERE studyPadTextEntryId = ${hex(entry)}"))
            assertEquals(d.deviceId, 0, addonLogEntries(d))
        }
        assertEquals("the collection itself is untouched", 2, countIn(withModule, "SELECT COUNT(*) FROM BibleBookmarkToLabel WHERE labelId = ${hex(addonLabel)}"))
    }

    @Test
    fun incomingPatchNamingAnAddonRowLeavesTheLocalAddonRow() = run {
        val d1 = device("d1")
        val d2 = device("d2")
        install(d2.localDb)
        d1.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "Mine"))
        val patch = gunzip(createPatchForDatabase(d1, env)!!)
        // A forged patch: the add-on label renamed, a new add-on bookmark, both with newer log entries.
        openSqlite(patch).also { c ->
            try {
                val later = System.currentTimeMillis() + 100_000
                c.exec("INSERT INTO Label (id, name) VALUES (${hex(addonLabel)}, 'Hijacked')")
                c.exec("INSERT INTO LogEntry VALUES ('Label', ${hex(addonLabel)}, '', 'UPSERT', $later, 'd1')")
                c.exec("INSERT INTO LogEntry VALUES ('BibleBookmark', ${hex(addonB1)}, '', 'DELETE', $later, 'd1')")
            } finally { c.close() }
        }
        val gz = tmp("forged-", ".gz").toOkioPath()
        ArchiveUtils.gzipFile(File(patch).toOkioPath(), gz)
        applyPatchesForDatabase(d2, env, gz)
        assertEquals("Comfort", d2.localDb.bookmarkDao().labelById(addonLabel)!!.name)
        assertEquals(1, countIn(d2, "SELECT COUNT(*) FROM BibleBookmark WHERE id = ${hex(addonB1)}"))
        assertEquals("Mine", d2.localDb.bookmarkDao().allLabelsSortedByName().single { !it.id.toHex().contains("add08c01a011") }.name)
        assertEquals(0, addonLogEntries(d2))
    }

    @Test
    fun importingAnExportThatContainsAddonRowsSkipsThem() = run {
        val b = DatabaseContainer.databaseAccessorsByCategory[category]!!
        val export = tmp("export-")
        export.delete()
        open(export.absolutePath).also { db ->
            install(db)
            db.bookmarkDao().insert(BookmarkEntities.Label(name = "Mine"))
            addRogueRefs(db)
            db.close()
        }
        // The count of skipped items (1 label, 2 bookmarks, 1 StudyPad entry) is returned, for the UI to explain an
        // import that brought nothing.
        assertEquals(4L, importDatabaseFile(category, export.toOkioPath()))
        assertEquals(0, addonRowsIn(b))
        // The user rows of the file come in without their references to the add-on rows.
        assertEquals(0, userRefsToAddonIds { countIn(b, it) })
        assertEquals(1, countIn(b, "SELECT COUNT(*) FROM BibleBookmark WHERE kjvOrdinalStart = 200 AND primaryLabelId IS NULL"))
        assertTrue((b.localDb as BookmarkDatabase).bookmarkDao().allLabelsSortedByName().any { it.name == "Mine" })
    }

    @Test
    fun aStudyPadImportThatSkippedAddonRowsToastsTheCount() = run {
        val unzip = File(CommonUtils.tmpDir, "studypad-import-${UUID.randomUUID()}").also { it.mkdirs() }
        val dbDir = File(unzip, "db").also { it.mkdirs() }
        val export = File(dbDir, BookmarkDatabase.dbFileName).also { files += it }
        open(export.absolutePath).also { db ->
            install(db)
            db.bookmarkDao().insert(BookmarkEntities.Label(name = "Mine"))
            db.close()
        }
        val toasts = mutableListOf<String>()
        val sub = net.bible.android.control.event.UserMessages.messages.subscribe {
            (it as? net.bible.android.control.event.UserMessage.Toast)?.let { t -> t.text?.let(toasts::add) }
        }
        try {
            DefaultInstallCommitter(
                get(BackupControl::class.java), get(CoreStrings::class.java), env,
                clearAddonCaches = {},
            ).commitStudyPad(unzip.toOkioPath())
            assertEquals(1, toasts.size)
            assertTrue(toasts.single(), toasts.single().contains("4 add-on collection items were skipped"))
        } finally {
            sub.cancel()
            unzip.deleteRecursively()
        }
    }

    /** Sync review Minor 3: favourites and expand state of add-on rows are this device's own and survive an initial download. */
    @Test
    fun initialDownloadSwapKeepsThisDevicesAddonState() = run {
        val d1 = device("d1")
        install(d1.localDb)
        d1.localDb.useWriterConnectionMarked { c ->
            c.exec("INSERT INTO AddonLabelLocalState (labelId, favourite) VALUES (${hex(addonLabel)}, 1)")
            c.exec("INSERT INTO AddonLinkLocalState (bookmarkId, labelId, expandContent) VALUES (${hex(addonB1)}, ${hex(addonLabel)}, 0)")
        }
        // The cloud's initial file: another device's database.
        val cloudDevice = device("d2")
        cloudDevice.localDb.bookmarkDao().insert(BookmarkEntities.Label(name = "Cloud"))
        val downloaded = tmp("initial-").also { it.delete() }
        cloudDevice.localDb.useWriterConnectionMarked { it.exec("VACUUM INTO '${downloaded.absolutePath}'") }
        cloud.swapInInitialDb(d1, downloaded.toOkioPath()) {}
        assertEquals(1, countIn(d1, "SELECT COUNT(*) FROM Label WHERE name = 'Cloud'"))
        assertEquals(1, countIn(d1, "SELECT COUNT(*) FROM AddonLabelLocalState WHERE labelId = ${hex(addonLabel)} AND favourite = 1"))
        assertEquals(1, countIn(d1, "SELECT COUNT(*) FROM AddonLinkLocalState WHERE bookmarkId = ${hex(addonB1)} AND expandContent = 0"))
    }

    @Test
    fun mergeSnapshotHoldsNoAddonRows() = run {
        val d1 = device("d1")
        install(d1.localDb)
        addOwnBookmark(d1.localDb)
        addRogueRefs(d1.localDb)
        val snapshot = tmp("snapshot-").toOkioPath()
        prepareMergeSnapshot(d1, snapshot)
        assertEquals(0, addonRowsIn(snapshot.toString()))
        assertEquals(0, userRefsToAddonIds { countIn(snapshot.toString(), it) })
        assertEquals(0, countIn(snapshot.toString(), "SELECT COUNT(*) FROM AddonCollectionRow"))
        assertEquals(0, countIn(snapshot.toString(), "SELECT COUNT(*) FROM AddonCollectionModule"))
        // The user's own rows are in it, logged for the merge.
        assertEquals(1, countIn(snapshot.toString(), "SELECT COUNT(*) FROM Label WHERE name = 'Mine'"))
        assertTrue(countIn(snapshot.toString(), "SELECT COUNT(*) FROM LogEntry") > 0)
        // The live database still has its collection.
        assertTrue(addonRowsIn(d1) > 0)
    }

    private suspend fun signedInAdapters(): Pair<WebDavCloudAdapter, WebDavCloudAdapter> {
        val mine = adapter(env.deviceIdentifier)
        check(mine.signIn())
        adapterField.set(cloud, mine)
        val other = adapter("device-A")
        check(other.signIn())
        return mine to other
    }

    private suspend fun kotlinx.coroutines.test.TestScope.syncAnswering(choiceIndex: Int) {
        val run = async(Dispatchers.IO) { cloud.synchronize() }
        val deadline = System.currentTimeMillis() + 60_000
        while (!run.isCompleted) {
            advanceUntilIdle()
            dialogs.pending.value?.let { shown ->
                dialogs.respond(shown.id, when (val r = shown.request) {
                    is AppDialogRequest.Options -> AppDialogResult.Selected(r.options[choiceIndex].value)
                    else -> AppDialogResult.Ok
                })
            }
            check(System.currentTimeMillis() < deadline) { "sync did not finish" }
            withContext(Dispatchers.Default) { delay(10) }
        }
        run.await()
    }

    /** Review Focus 3: the first sync after install uploads an initial file; a fresh device must get no add-on rows. */
    @Test
    fun initialUploadHoldsNoAddonRows() = runTest(testDispatcher, timeout = 120.seconds) {
        val b = DatabaseContainer.databaseAccessorsByCategory[category]!!
        val bDb = b.localDb as BookmarkDatabase
        install(bDb)
        addOwnBookmark(bDb)
        addRogueRefs(bDb)
        val (_, other) = signedInAdapters()

        syncAnswering(choiceIndex = 0)

        val folder = other.listFiles(name = "${env.packageName}-sync-${category.name.lowercase()}").single().id
        val initial = other.listFiles(parentsIds = listOf(folder), name = INITIAL_BACKUP_FILENAME).single()
        val gz = tmp("initial-", ".gz").toOkioPath()
        other.download(initial.id, okio.FileSystem.SYSTEM.sink(gz))
        val file = gunzip(gz)
        assertEquals(0, addonRowsIn(file))
        assertEquals(0, userRefsToAddonIds { countIn(file, it) })
        for (table in ADDON_LOCAL_TABLES) assertEquals(table, 0, countIn(file, "SELECT COUNT(*) FROM $table"))
        assertEquals(1, countIn(file, "SELECT COUNT(*) FROM Label WHERE name = 'Mine'"))
        // The device itself keeps its collection.
        assertTrue(addonRowsIn(b) > 0)
        assertEquals(0, addonLogEntries(b))
    }

    /**
     * The initial download (a first-join FETCH) strips add-on rows and user rows' references to them from the cloud's
     * file before it replaces the local database (CloudSync.fetchAndRestoreInitial). The cloud's initial file is
     * replaced here by one with the references, as another device or an older build could have uploaded.
     */
    @Test
    fun initialDownloadStripsUserRefsToAddonIds() = runTest(testDispatcher, timeout = 120.seconds) {
        val before = DatabaseContainer.databaseAccessorsByCategory[category]!!
        install(before.localDb as BookmarkDatabase)
        val (_, other) = signedInAdapters()
        syncAnswering(choiceIndex = 0) // creates the cloud's initial file
        val folder = other.listFiles(name = "${env.packageName}-sync-${category.name.lowercase()}").single().id

        val forgery = device("forger")
        install(forgery.localDb)
        val rogue = addRogueRefs(forgery.localDb)
        val raw = tmp("forged-initial-").also { it.delete() }
        forgery.localDb.useWriterConnectionMarked { it.exec("VACUUM INTO '${raw.absolutePath}'") }
        assertTrue("the forged file holds references", userRefsToAddonIds { countIn(raw.absolutePath, it) } > 0)
        val gz = tmp("forged-initial-", ".gz").toOkioPath()
        ArchiveUtils.gzipFile(raw.toOkioPath(), gz)
        other.upload(INITIAL_BACKUP_FILENAME, gz, folder)

        // Forget the join, so that the next sync fetches the initial file: choice 1 is "fetch and restore initial".
        before.dao.removeConfig(SYNC_FOLDER_FILE_ID_KEY)
        syncAnswering(choiceIndex = 1)

        val b = DatabaseContainer.databaseAccessorsByCategory[category]!! // the swap reopened the database
        assertEquals(0, userRefsToAddonIds { countIn(b, it) })
        assertEquals("the user's bookmark arrives without its add-on primary label", 1,
            countIn(b, "SELECT COUNT(*) FROM BibleBookmark WHERE id = ${hex(rogue.bookmark)} AND primaryLabelId IS NULL"))
        assertEquals(1, countIn(b, "SELECT COUNT(*) FROM GenericBookmark WHERE id = ${hex(rogue.generic)} AND primaryLabelId IS NULL"))
        assertEquals(0, countIn(b, "SELECT COUNT(*) FROM StudyPadTextEntry WHERE id = ${hex(rogue.entry)}"))
    }
}
