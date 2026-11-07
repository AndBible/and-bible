package net.bible.demo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.bible.android.common.toV11n
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.TextContentType
import net.bible.android.database.parseArgbHex
import net.bible.service.db.ALL_DB_FILENAMES
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.db.FakeDatabaseHost
import net.bible.service.db.newTestDir
import net.bible.service.db.useWriterConnectionMarked
import net.bible.sharedcore.platform.files.PickedSource
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Generates a demo backup from `resources/demo/fixture-content.yaml`, restores it through the real
 * `BackupControl` restore flow into a fresh database directory and compares the restored rows with the YAML.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DemoBackupGeneratorTest {
    private var previousHost: DatabaseHost? = null
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null }
        Dispatchers.setMain(mainDispatcher)
        startKoin { modules(module { single<net.bible.sharedcore.platform.DateTimeFormats> { FixedDateTimeFormats } }) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
        DatabaseContainer.ready = false
        DatabaseContainer.forgetReplacesForTest()
        DatabaseContainer.detachInstanceForTest()
        previousHost?.let { DatabaseContainer.installHost(it) }
    }

    private fun fixture(): DemoContent =
        DemoContent.parse(checkNotNull(javaClass.getResource("/demo/fixture-content.yaml")) { "fixture missing" }.readText())

    @Test
    fun roundTrip() = runTest(mainDispatcher, timeout = 10.minutes) {
        val zip = Files.createTempDirectory("demo-backup-").resolve("fixture.abdb.zip")
        DemoBackupGenerator.generate(fixture(), zip)

        ZipFile(zip.toFile()).use { z ->
            val names = z.entries().toList().map { it.name }
            assertTrue(names.any { it.startsWith("db/").not() }, "manifest entry expected: $names")
            for (db in ALL_DB_FILENAMES) assertTrue("db/$db" in names, "db/$db missing in $names")
        }

        val dir = newTestDir()
        DatabaseContainer.installHost(FakeDatabaseHost(dir))
        val rig = DemoBackupRig(dir)
        val restored = restoreFlow(rig, Files.readAllBytes(zip))
        assertTrue(restored, "restore failed: ${rig.notices.errors} / ${rig.host.errors}")

        val container = DatabaseContainer.create()
        try {
            val dao = container.bookmarkDb.bookmarkDao()
            val labels = dao.allLabelsSortedByName().associateBy { it.name }
            val god = assertNotNull(labels["Character of God"])
            assertEquals(parseArgbHex("#E57373"), god.color)
            assertTrue(god.favourite)
            val sower = assertNotNull(labels["Parable of the Sower"])
            assertEquals(parseArgbHex("#81C784"), sower.color)
            assertEquals(net.bible.sharedcore.bookmark.BookmarkDisplayStyle.UNDERLINE, sower.displayStyle)

            val kjv = Versifications.instance().getVersification("KJV")
            val range = VerseRangeFactory.fromString(kjv, "Jer.9.23-Jer.9.24")
            val bookmarks = dao.allBookmarks()
            assertEquals(3, bookmarks.size)
            val jer = bookmarks.single { it.ordinalStart == range.start.ordinal }
            assertEquals(range.end.ordinal, jer.ordinalEnd)
            assertEquals("KJV", jer.v11n.name)
            assertEquals(range.toV11n(KJVA).start.ordinal, jer.kjvOrdinalStart)
            assertEquals(range.toV11n(KJVA).end.ordinal, jer.kjvOrdinalEnd)
            assertEquals(
                "<p>This passage names <i>three</i> things:</p><ol><li>mercy</li><li>justice</li><li>righteousness</li></ol>",
                jer.notes,
            )
            assertEquals(TextContentType.HTML, jer.notesContentType)
            assertEquals(god.id, jer.primaryLabelId)
            assertEquals(listOf(god.id), dao.labelsForBookmark(jer).map { it.id })
            assertEquals("Plain note", bookmarks.single { it.notes == "Plain note" }.notes)

            val matthew = bookmarks.single { it.labelIdsOf(dao) == listOf(sower.id) }
            // StudyPad order: the text entry first, then the bookmark.
            val textEntries = dao.studyPadTextEntriesByLabelId(sower.id)
            assertEquals(listOf("<p>Jesus tells a parable.</p>"), textEntries.map { it.text })
            val bookmarkOrders = dao.bibleBookmarkToLabelStubs(sower.id)
            assertEquals(listOf(matthew.id), bookmarkOrders.map { it.bookmarkId })
            assertTrue(textEntries.single().orderNumber < bookmarkOrders.single().orderNumber, "text entry precedes the bookmark")

            val wdao = container.workspaceDb.workspaceDao()
            val workspaces = wdao.allWorkspaces()
            assertEquals(listOf("bookmarks-labels", "strongs-split"), workspaces.map { it.name })
            assertEquals(workspaces.first().id.toString(), container.settingsStore.getString("current_workspace_id", null))

            val w1 = wdao.windows(workspaces[0].id)
            assertEquals(2, w1.size)
            val pm1 = w1.map { assertNotNull(wdao.pageManager(it.id)) }
            assertEquals("KJV", pm1[0].biblePage.document)
            assertEquals("BIBLE", pm1[0].currentCategoryName)
            assertEquals(WorkspaceEntities.Verse("KJV", range.end.book.ordinal, 9, 24), pm1[0].biblePage.verse)
            assertEquals("GENERAL_BOOK", pm1[1].currentCategoryName)
            assertEquals("MyStudyPads", pm1[1].generalBookPage?.document)
            assertEquals("journal:${sower.id}", pm1[1].generalBookPage?.key)
            assertEquals(0.5f, w1[1].windowLayout.weight)
            assertEquals(listOf(true, true), w1.map { it.isSynchronized })
            assertNull(workspaces[0].textDisplaySettings?.strongsMode)

            val w2 = wdao.windows(workspaces[1].id)
            assertEquals(listOf("VISIBLE", "MINIMISED"), w2.map { it.windowLayout.state })
            assertEquals(listOf(true, false), w2.map { it.isPinMode })
            assertEquals(listOf(true, false), w2.map { it.isSynchronized })
            val pm2 = w2.map { assertNotNull(wdao.pageManager(it.id)) }
            assertEquals("TSK", pm2[1].commentaryPage?.document)
            assertEquals("COMMENTARY", pm2[1].currentCategoryName)
            assertEquals("KJV", pm2[1].biblePage.document)
            assertEquals(WorkspaceEntities.Verse("KJV", org.crosswire.jsword.versification.BibleBook.PS.ordinal, 33, 5), pm2[1].biblePage.verse)
            assertEquals(1, workspaces[1].textDisplaySettings?.strongsMode)
            assertEquals(true, workspaces[1].textDisplaySettings?.showXrefs)
        } finally {
            DatabaseContainer.aiLifetime.replace { container.closeAllOwned(it) }
            DatabaseContainer.ready = false
        }
    }

    @Test
    fun unknownLabelIdFailsNamingIt() = runTest(mainDispatcher, timeout = 10.minutes) {
        val broken = DemoContent.parse(
            """
            labels:
              - {id: a, name: A, color: "#112233"}
            bookmarks:
              - {id: b1, ref: "Gen.1.1", labels: [a, no-such-label]}
            """.trimIndent(),
        )
        val e = assertFailsWith<IllegalArgumentException> {
            DemoBackupGenerator.generate(broken, Files.createTempDirectory("demo-backup-").resolve("x.zip"))
        }
        assertTrue("no-such-label" in e.message!! && "b1" in e.message!!, e.message)
    }

    @Test
    fun unknownYamlFieldFailsParsing() {
        assertFailsWith<Exception> { DemoContent.parse("labels:\n  - {id: a, name: A, color: \"#112233\", colour: x}\n") }
    }

    private suspend fun BookmarkEntities.BibleBookmarkWithNotes.labelIdsOf(dao: net.bible.android.database.bookmarks.BookmarkDao): List<IdType> =
        dao.labelsForBookmark(this).map { it.id }

    /** Runs the restore flow over [zipBytes], answering its dialogs: every section, "restore" for each question. */
    private suspend fun TestScope.restoreFlow(rig: DemoBackupRig, zipBytes: ByteArray): Boolean {
        val job: Deferred<Boolean> = async {
            rig.backup.restoreAppDatabaseFromSourceWithUI(rig.host.over(zipBytes), PickedSource("test://backup.abdb.zip", "backup.abdb.zip", null))
        }
        while (!job.isCompleted) {
            advanceUntilIdle()
            rig.dialogs.pending.value?.let { shown ->
                val answer: AppDialogResult = when (val request = shown.request) {
                    is AppDialogRequest.MultiChoice -> AppDialogResult.SelectedMany(request.options.map { it.value })
                    is AppDialogRequest.Options -> AppDialogResult.Selected("restore")
                    else -> AppDialogResult.Ok
                }
                rig.dialogs.respond(shown.id, answer)
            }
            withContext(Dispatchers.Default) { delay(20) }
        }
        return job.await()
    }
}
