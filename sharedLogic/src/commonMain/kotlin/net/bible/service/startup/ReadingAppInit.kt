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

package net.bible.service.startup

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.page.defaultBible
import net.bible.android.control.page.defaultVerse
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.argb
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkSortOrder
import net.bible.android.database.bookmarks.BookmarkType
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.LabelType
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.common.GlobalTextDisplaySettingsStore
import net.bible.service.common.ReadingProgressSettingsStore
import net.bible.service.db.DatabaseContainer
import net.bible.service.format.SaxTextPolicy
import net.bible.service.llm.AiSettings
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.csvprompt.addManuallyInstalledCsvPromptBooks
import net.bible.service.sword.epub.addManuallyInstalledEpubBooks
import net.bible.service.sword.esword.addManuallyInstalledESwordBooks
import net.bible.service.sword.mybible.addManuallyInstalledMyBibleBooks
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mysword.addManuallyInstalledMySwordBooks
import net.bible.service.sword.ttf.addManuallyInstalledTtfBooks
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedlogic.platform.FirstLoadEdge
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.koin.mp.KoinPlatform
import kotlin.math.roundToInt

/** Names of the first-run example labels (Android: `label_red`, ..., `label_salvation`, `migrated_my_notes`). */
data class ExampleLabelNames(
    val red: String,
    val green: String,
    val blue: String,
    val underline: String,
    val salvation: String,
    val migratedMyNotes: String,
)

/** What [ReadingAppInit] needs from the platform. */
interface ReadingAppInitPlatform {
    /** Android: logcat pipe, TTS notification manager, speak widget manager. iOS: nothing. */
    suspend fun platformStart()

    /** Dispatcher for blocking file work (Android: `Dispatchers.IO`; not common, so the platform supplies it). */
    val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher

    /** Label names for the first-run example data. */
    val exampleLabelNames: ExampleLabelNames

    /** Name of the first-run example workspace number [n] (1-based). */
    fun workspaceName(n: Int): String

    /** Called once every book is registered: sweeps the search indexes of uninstalled books. Must not block. */
    fun sweepOrphanSearchIndexes()
}

/**
 * The platform-neutral part of app initialisation (data, settings, installed books, first-run example data and the
 * window repository), shared by Android's `CommonUtils` and the iOS host. The platform part runs through
 * [ReadingAppInitPlatform].
 */
object ReadingAppInit {
    private const val TAG = "ReadingAppInit"

    const val LOREM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."

    /** True once [initialise] (or Android's synchronous `initializeApp`) has run. */
    @kotlin.concurrent.Volatile
    var initialized: Boolean = false
        internal set

    @kotlin.concurrent.Volatile private var booksInitialized = false
    @kotlin.concurrent.Volatile private var snapshotLoadJob: Job? = null

    private var lastRepositoryScope: CoroutineScope? = null
    private var lastRepository: WindowRepository? = null

    // Test seams: the parts of startup that need real documents or the whole Koin graph.
    @VisibleForTesting internal var loadStores: suspend () -> Unit = ::loadStoresFromKoin
    @VisibleForTesting internal var loadDocumentSyncSettings: suspend () -> Unit = {
        KoinPlatform.getKoin().get<DocumentSyncSettings>().ensureLoaded()
    }
    @VisibleForTesting internal var installManualBooks: suspend () -> Unit = ::installManualBooksFromKoin
    @VisibleForTesting internal var hasInstalledBible: () -> Boolean = {
        Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }.isNotEmpty()
    }
    @VisibleForTesting internal var unlockBook: (initials: String, cipherKey: String?) -> Unit = { initials, key ->
        Books.installed().getBook(initials)?.unlock(key)
    }

    /** Loads a freshly taken repository; tests skip the real load, which needs the whole window graph. */
    @VisibleForTesting internal var loadRepository: (WindowRepository) -> Unit = { it.initializeAtEdge() }

    fun markInitialized() { initialized = true }
    fun reset() { initialized = false }

    /**
     * Platform-neutral body of the cold-start initialisation. Runs once per process: later calls only load the
     * stores and join the document queue again, and unlock books if that did not happen yet.
     */
    suspend fun initialise(platform: ReadingAppInitPlatform) {
        DatabaseContainer.ready = true
        loadStores()
        MyDocumentBookManager.launcher.join("mydocument-sync")
        if (!initialized) {
            DatabaseContainer.ready = true
            DatabaseContainer.instance
            loadDocumentSyncSettings()
            platform.platformStart()
            withContext(platform.ioDispatcher) { installManualBooks() }
            MyDocumentBookManager.registerAllDocuments()
            platform.sweepOrphanSearchIndexes()
            initialized = true
        }
        unlockStoredCipherKeys()
    }

    /** Unlocks installed books that have a stored cipher key; once per process, and only when a Bible is installed. */
    suspend fun unlockStoredCipherKeys() {
        if (booksInitialized || !hasInstalledBible()) return
        if (!DatabaseContainer.host.isRunningTests) {
            for (info in DatabaseContainer.instance.repoDb.swordDocumentInfoDao().getUnlocked()) {
                unlockBook(info.initials, info.cipherKey)
            }
        }
        booksInitialized = true
    }

    private suspend fun loadStoresFromKoin() {
        val koin = KoinPlatform.getKoin()
        koin.get<AiSettings>().ensureLoaded()
        // Per-step isolation: an unreadable AI/settings row must not abort startup.
        runEachCatching(
            { koin.get<GlobalTextDisplaySettingsStore>().load() },
            { koin.get<ReadingProgressSettingsStore>().load() },
            { koin.get<PromptService>().refresh() },
        )
    }

    private fun installManualBooksFromKoin() {
        val koin = KoinPlatform.getKoin()
        addManuallyInstalledMyBibleBooks()
        addManuallyInstalledMySwordBooks()
        addManuallyInstalledESwordBooks()
        addManuallyInstalledEpubBooks(koin.get<CoreStrings>(), koin.get<AppEnvironment>())
        addManuallyInstalledTtfBooks()
        addManuallyInstalledCsvPromptBooks()
    }

    /** Runs every step even when one fails; cancellation is rethrown. */
    suspend fun runEachCatching(vararg steps: suspend () -> Unit) {
        for (step in steps) {
            try { step() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "Snapshot store step failed", e) }
        }
    }

    /**
     * Starts the snapshot-store loads without blocking. The job is owned by [DatabaseContainer.aiLifetime]: every
     * database reset, close or restore cancels and joins it before closing. A cancelled load leaves the store
     * unloaded (factory defaults) until a later [initialise] loads it; [awaitSnapshotLoadsAtEdge] joins this job.
     */
    fun loadSnapshotStoresInBackground(): Job {
        val koin = KoinPlatform.getKoin()
        val scope = koin.get<AppCoroutineScope>()
        return scope.launch(start = CoroutineStart.LAZY) {
            runEachCatching(
                { koin.get<GlobalTextDisplaySettingsStore>().load() },
                { koin.get<ReadingProgressSettingsStore>().load() },
                { koin.get<PromptService>().refresh() },
            )
        }.also {
            snapshotLoadJob = it
            DatabaseContainer.aiLifetime.ownUntilReplace(it)
            it.start()
        }
    }

    /**
     * The cold-start barrier for the snapshot stores (the process-death path never runs [initialise]). A cancelled
     * load completes the join at once instead of hanging.
     */
    fun awaitSnapshotLoadsAtEdge() {
        val job = snapshotLoadJob ?: return
        if (job.isCompleted) return
        KoinPlatform.getKoin().get<FirstLoadEdge>().awaitFirstLoad { job.join() }
    }

    /** Data-version bookkeeping and the first-run example bookmarks and workspaces. */
    suspend fun prepareData(platform: ReadingAppInitPlatform) {
        val settings = KoinPlatform.getKoin().get<AppSettings>()
        val dataVersionNow = 1L
        val dataVersion = settings.getLong("data-version", 0)
        if (dataVersion < 1) {
            prepareExampleBookmarks(platform.exampleLabelNames)
            prepareExampleWorkspaces(platform, settings)
        }
        if (dataVersion != dataVersionNow) {
            settings.setLong("data-version", dataVersionNow)
        }
    }

    /** First-run example bookmarks and highlight labels. */
    private suspend fun prepareExampleBookmarks(names: ExampleLabelNames) {
        val bookmarkDao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val hasExistingBookmarks = bookmarkDao.allBookmarks(BookmarkSortOrder.ORDER_NUMBER).isNotEmpty()

        if (bookmarkDao.allLabelsSortedByName().none { !it.name.startsWith("__") && it.name != names.migratedMyNotes }) {
            val redLabel = BookmarkEntities.Label(name = names.red, type = LabelType.HIGHLIGHT, color = argb(255, 255, 0, 0), displayStyleWholeVerse = null, favourite = true)
            val greenLabel = BookmarkEntities.Label(name = names.green, type = LabelType.HIGHLIGHT, color = argb(255, 0, 255, 0), displayStyleWholeVerse = null, favourite = true)
            val blueLabel = BookmarkEntities.Label(name = names.blue, type = LabelType.HIGHLIGHT, color = argb(255, 0, 0, 255), displayStyleWholeVerse = null, favourite = true)
            val underlineLabel = BookmarkEntities.Label(name = names.underline, type = LabelType.HIGHLIGHT, color = argb(255, 255, 0, 255), displayStyle = BookmarkDisplayStyle.UNDERLINE, displayStyleWholeVerse = null, favourite = true)

            val highlightLabels = listOf(redLabel, greenLabel, underlineLabel, blueLabel)

            val salvationLabel = BookmarkEntities.Label(name = names.salvation, type = LabelType.EXAMPLE, color = argb(255, 100, 0, 150))
            bookmarkDao.insertLabels(highlightLabels)

            if (!hasExistingBookmarks) {
                val koin = KoinPlatform.getKoin()
                val strings = koin.get<CoreStrings>()
                val saxTextPolicy = koin.get<SaxTextPolicy>()
                val defaultBible = defaultBible()
                val defaultVerse = defaultVerse()

                fun getBookmark(verseRange: VerseRange, start: Double, end: Double): BookmarkEntities.BibleBookmarkWithNotes {
                    val v1 = verseRange.toVerseArray()[start.toInt()]
                    val v2 = verseRange.toVerseArray()[end.toInt()]
                    val l1 = SwordContentFacade.getCanonicalText(defaultBible, v1, true, strings = strings, saxTextPolicy = saxTextPolicy).length
                    val l2 = SwordContentFacade.getCanonicalText(defaultBible, v2, true, strings = strings, saxTextPolicy = saxTextPolicy).length
                    val tr = BookmarkEntities.TextRange(((start - start.toInt()) * l1).roundToInt(), ((end - end.toInt()) * l2).roundToInt())
                    val v = VerseRange(v1.versification, v1, v2)

                    return BookmarkEntities.BibleBookmarkWithNotes(v, textRange = tr, wholeVerse = false, book = defaultBible)
                }

                bookmarkDao.insert(salvationLabel)

                // first bookmark, full verses, with underline
                var b = BookmarkEntities.BibleBookmarkWithNotes(defaultVerse, textRange = null, wholeVerse = true, book = defaultBible).apply { primaryLabelId = underlineLabel.id }
                bookmarkDao.insert(b.bookmarkEntity)
                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, underlineLabel.id))
                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, salvationLabel.id))

                // second bookmark, red
                b = getBookmark(defaultVerse, 1.0, 1.5).apply { primaryLabelId = redLabel.id }
                bookmarkDao.insert(b.bookmarkEntity)
                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, redLabel.id))

                // third bookmark, green
                b = getBookmark(defaultVerse, 1.2, 1.4).apply {
                    primaryLabelId = greenLabel.id
                    notes = LOREM
                }
                bookmarkDao.insert(b.bookmarkEntity)
                bookmarkDao.insert(b.noteEntity!!)

                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, greenLabel.id))

                val salvationVerses = listOf("Joh.3.3", "Tit.3.3-Tit.3.7", "Rom.3.23-Rom.3.24", "Rom.4.3", "1Tim.1.15", "Eph.2.8-Eph.2.9", "Isa.6.3", "Rev.4.8", "Exo.20.2-Exo.20.17")
                    .mapNotNull {
                        try { VerseRangeFactory.fromString(KJVA, it) } catch (e: NoSuchVerseException) {
                            Log.e(TAG, "NoSuchVerseException for $it??!", e)
                            null
                        }
                    }

                salvationVerses
                    .map {
                        BookmarkEntities.BibleBookmarkWithNotes(it, textRange = null, wholeVerse = true, book = null).apply { type = BookmarkType.EXAMPLE }
                    }.forEach {
                        bookmarkDao.insert(it.bookmarkEntity)
                        bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(it.id, salvationLabel.id))
                    }
            }
        }
    }

    private suspend fun prepareExampleWorkspaces(platform: ReadingAppInitPlatform, settings: AppSettings) {
        val workspaceDao = DatabaseContainer.instance.workspaceDb.workspaceDao()
        val ws = workspaceDao.allWorkspaces()
        if (ws.isNotEmpty()) {
            workspaceDao.updateWorkspaces(ws)
            settings.setBoolean("first-time", false)
        } else {
            val workspaceSettings = WorkspaceEntities.WorkspaceSettings()
            val newWorkspaces = listOf(
                WorkspaceEntities.Workspace(name = platform.workspaceName(1), workspaceSettings = workspaceSettings),
                WorkspaceEntities.Workspace(name = platform.workspaceName(2), workspaceSettings = workspaceSettings),
            )
            newWorkspaces.forEach { workspaceDao.insertWorkspace(it) }
            settings.setString("current_workspace_id", newWorkspaces[0].id.toString())
        }
    }

    /**
     * Builds the [WindowRepository] on [scope], makes it the [WindowControl]'s repository and loads it. Asking again
     * with the same scope returns the repository already built for it.
     */
    fun createWindowRepository(scope: CoroutineScope): WindowRepository {
        lastRepository?.let { if (lastRepositoryScope === scope) return it }
        val repository = WindowRepository(scope)
        lastRepositoryScope = scope
        lastRepository = repository
        // Flushes the previous owner first, so initializeAtEdge() reads a DB that has its last edit (CR-1 I1).
        KoinPlatform.getKoin().get<WindowControl>().takeWindowRepository(repository)
        loadRepository(repository)
        // Process-death recreation never runs initialise: the snapshot stores must be loaded before the first render reads them.
        awaitSnapshotLoadsAtEdge()
        return repository
    }

    @VisibleForTesting
    fun resetForTest() {
        initialized = false
        booksInitialized = false
        snapshotLoadJob = null
        lastRepositoryScope = null
        lastRepository = null
        loadStores = ::loadStoresFromKoin
        loadDocumentSyncSettings = { KoinPlatform.getKoin().get<DocumentSyncSettings>().ensureLoaded() }
        installManualBooks = ::installManualBooksFromKoin
        hasInstalledBible = { Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }.isNotEmpty() }
        unlockBook = { initials, key -> Books.installed().getBook(initials)?.unlock(key) }
        loadRepository = { it.initializeAtEdge() }
    }
}
