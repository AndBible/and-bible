package net.bible.sharedui.app.bookmark

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.DbDate
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.view.activity.bookmark.BookmarksServiceImpl
import net.bible.service.db.DatabaseContainer
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.bookmark.BookmarkSortMode
import net.bible.sharedcore.bookmark.BookmarksController
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedlogic.fixtures.StartupFixture
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.bookmark.BookmarksScreen
import net.bible.sharedui.desktop.assembly.DesktopAppStartup
import net.bible.sharedui.desktop.assembly.DesktopWindowHandle
import net.bible.sharedui.host.app.HostReadingReady
import net.bible.sharedui.host.reading.HostReadingWindows
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.PropertyMap
import org.crosswire.jsword.passage.VerseRangeFactory
import org.koin.mp.KoinPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

/** Exercises the real Room-backed service and controller through the shared bookmark screen. */
@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BookmarkListMigrationTest {
    @Test
    fun sortSearchAndNotesFlow() {
        assertNull(KoinPlatform.getKoinOrNull())
        val main = Executors.newSingleThreadExecutor { Thread(it, "bookmark-test-main").apply { isDaemon = true } }.asCoroutineDispatcher()
        val root = Files.createTempDirectory("bookmark-list")
        val previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        fun field(name: String) = (runCatching { CWProject::class.java.getDeclaredField(name) }.getOrNull()
            ?: CWProject.Companion::class.java.getDeclaredField(name)).apply { isAccessible = true }
        fun owner(f: java.lang.reflect.Field): Any? = if (java.lang.reflect.Modifier.isStatic(f.modifiers)) null else CWProject.Companion
        val fields = listOf("homeProperty", "homeDirectory", "homeAltDirectory").map(::field)
        val values = fields.map { it.get(owner(it)) }
        val instanceField = field("instance")
        val instance = instanceField.get(owner(instanceField))
        val established = CWProject::class.java.getDeclaredField("writeHome").apply { isAccessible = true }.get(instance) != null
        val scope = CoroutineScope(SupervisorJob() + main)
        Dispatchers.setMain(main)
        try {
            val paths = DesktopPaths({ key -> when (key) {
                "XDG_DATA_HOME" -> root.resolve("data").toString()
                "XDG_CONFIG_HOME" -> root.resolve("config").toString()
                "XDG_CACHE_HOME" -> root.resolve("cache").toString()
                else -> null
            } }, "Linux", root.resolve("home"))
            StartupFixture.files.forEach { (name, bytes) ->
                val target = paths.sword.resolve(name)
                Files.createDirectories(target.parent)
                Files.write(target, bytes)
            }
            val window = object : DesktopWindowHandle {
                override val sizeDp = IntSize(1200, 800)
                override val density = 1f
                override fun toFront() = Unit
            }
            assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, window, "en", repositories = { PropertyMap() }, notifierFactory = { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier }))
            val koin = KoinPlatform.getKoin()
            // Keep first-run example bookmarks out of this independently seeded ordering fixture.
            koin.get<net.bible.sharedcore.platform.AppSettings>().setLong("data-version", 1L)
            runBlocking { HostReadingReady.ensure(HostStartup.stringTable) }
            val control = koin.get<BookmarkControl>()
            runBlocking {
                withContext(main) {
                    koin.get<WindowControl>().windowRepository.initializeAtEdge()
                    listOf(Triple("Rev.1.1", "seed alpha", 1000L), Triple("Gen.1.1", "seed beta", 2000L), Triple("John.3.16", "seed gamma", 3000L)).forEach { (ref, note, timestamp) ->
                        control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(
                            verseRange = VerseRangeFactory.fromString(KJVA, ref), textRange = null, wholeVerse = true, book = null,
                        ).apply { notes = note; createdAt = DbDate(timestamp); new = true })
                    }
                }
            }
            val controller = BookmarksController(koin.get<BookmarksServiceImpl>(), scope, 0, { _, _ -> }, {}, {}, {}, {}, {})
            val strings = iosStrings("en")
            val language = mutableStateOf("en")
            runComposeUiTest {
                setContent {
                    val screenStrings = iosStrings(language.value)
                    val rows by controller.rows.collectAsState()
                    val labels by controller.filterLabels.collectAsState()
                    val filter by controller.selectedFilterIndex.collectAsState()
                    val sort by controller.sortMode.collectAsState()
                    val query by controller.searchText.collectAsState()
                    val search by controller.searchModeActive.collectAsState()
                    val notes by controller.showNotes.collectAsState()
                    val selection by controller.selection.collectAsState()
                    val expanded by controller.expandedIds.collectAsState()
                    val loading by controller.loading.collectAsState()
                    AbTheme(darkTheme = false) {
                        CompositionLocalProvider(LocalStrings provides screenStrings) {
                            BookmarksScreen(screenStrings.bookmarksAndMynotesTitle, rows, labels, filter, sort, query, notes, selection, expanded, loading,
                                controller::setFilter, controller::cycleSort, controller::setSearch, search,
                                controller::openSearch, controller::closeSearch, controller::toggleShowNotes,
                                controller::selectRow, controller::enterSelection, controller::toggleSelection, controller::toggleExpanded,
                                controller::assignSelected, controller::requestDelete, controller::clearSelection,
                                controller::manageLabels, controller::exportCsv, controller::importCsv, {})
                        }
                    }
                }
                waitUntil(timeoutMillis = 20_000) { !controller.loading.value && controller.rows.value.size == 3 }
                fun checkOrder(expected: List<String>) {
                    waitUntil(timeoutMillis = 20_000) { !controller.loading.value && controller.rows.value.mapNotNull { it.notes?.runs?.joinToString("") { run -> run.text } } == expected }
                    waitForIdle()
                    val actual = expected.sortedBy { onNodeWithText(it).getUnclippedBoundsInRoot().top }
                    assertEquals(expected, actual)
                }
                checkOrder(listOf("seed beta", "seed gamma", "seed alpha"))
                val expectedReferences = mapOf(
                    "seed beta" to setOf("Genesis 1:1", "Gen 1:1"),
                    "seed gamma" to setOf("John 3:16", "Joh 3:16"),
                    "seed alpha" to setOf("Revelation of John 1:1", "Revelation 1:1", "Rev 1:1"),
                )
                controller.rows.value.forEach { row ->
                    val note = row.notes!!.runs.joinToString("") { it.text }
                    assertTrue(row.title in expectedReferences.getValue(note), "Wrong reference for $note: ${row.title}")
                    onNodeWithText(row.title).assertExists()
                }
                onNodeWithContentDescription(strings.sortByBibleBookLabel).performClick()
                checkOrder(listOf("seed alpha", "seed gamma", "seed beta"))
                onNodeWithContentDescription(strings.sortByBibleBookLabel).performClick()
                checkOrder(listOf("seed gamma", "seed beta", "seed alpha"))
                onNodeWithContentDescription(strings.sortByDateLabel).performClick()
                checkOrder(listOf("seed alpha", "seed beta", "seed gamma"))
                onNodeWithContentDescription(strings.sortByDateLabel).performClick()
                assertEquals(BookmarkSortMode.BIBLE_ORDER, controller.sortMode.value)
                checkOrder(listOf("seed beta", "seed gamma", "seed alpha"))
                onNodeWithContentDescription(strings.search).performClick()
                onNode(hasSetTextAction()).performTextInput("beta")
                waitUntil(timeoutMillis = 20_000) { !controller.loading.value && controller.rows.value.size == 1 }
                onNodeWithText("seed beta").assertExists()
                onNodeWithText("seed alpha").assertDoesNotExist()
                runOnIdle { controller.closeSearch() }
                waitUntil(timeoutMillis = 20_000) { !controller.loading.value && controller.rows.value.size == 3 }
                onNodeWithContentDescription(strings.menu).performClick()
                onNodeWithText(strings.showNotesLabel).performClick()
                waitUntil(timeoutMillis = 20_000) { !controller.loading.value && !controller.showNotes.value }
                onNodeWithContentDescription(strings.search).assertDoesNotExist()
                onNodeWithText("seed beta").assertDoesNotExist()
                onNodeWithContentDescription(strings.menu).performClick()
                onNodeWithText(strings.showNotesLabel).performClick()
                waitUntil(timeoutMillis = 20_000) { !controller.loading.value && controller.showNotes.value }
                onNodeWithContentDescription(strings.search).assertExists()
                onNodeWithText("seed beta").assertExists()

                runOnIdle { language.value = "fi" }
                waitForIdle()
                onNodeWithText("Kirjanmerkit & Omat Merkinnät").assertExists()
                onNodeWithText("Bookmarks & My Notes").assertDoesNotExist()
                onNodeWithContentDescription("Hallitse kirjanmerkkiryhmiä").assertExists()
                onNodeWithContentDescription("Manage Labels").assertDoesNotExist()
                onNodeWithContentDescription("Kirjajärjestys").assertExists()
                onNodeWithContentDescription("Biblical order").assertDoesNotExist()
                onNodeWithContentDescription("Date order").assertDoesNotExist()
                onNodeWithContentDescription("Valikko").performClick()
                onNodeWithText("Näytä merkinnät").assertExists()
                onNodeWithText("Show notes").assertDoesNotExist()
            }
        } finally {
            scope.cancel()
            runBlocking { withContext(main) { HostReadingWindows.resetForTest() } }
            HostReadingReady.resetForTest()
            ReadingAppInit.resetForTest()
            DesktopAppStartup.resetForTest()
            previousHost?.let { DatabaseContainer.installHost(it) }
            fields.zip(values).forEach { (f, value) -> f.set(owner(f), value) }
            if (established) instanceField.set(owner(instanceField), instance) else {
                val reset = runCatching { CWProject::class.java.getDeclaredMethod("reset") }.getOrElse { CWProject.Companion::class.java.getDeclaredMethod("reset") }
                reset.isAccessible = true
                reset.invoke(if (java.lang.reflect.Modifier.isStatic(reset.modifiers)) null else CWProject.Companion)
            }
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
            main.close()
        }
    }
}
