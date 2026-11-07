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
package net.bible.sharedui.desktop.assembly

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.async
import okio.Path.Companion.toPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.sword.epub.epubBackend
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedlogic.fixtures.StartupFixture
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.assembly.StartupPhase
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.PropertyMap
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** How long [DesktopAppStartupTest] waits for an apply notification another thread may deliver; never reached when green. */
private const val APPLY_WAIT_MS = 10_000L

/**
 * The real desktop startup (Koin, databases, Sword) against temporary XDG directories. Every test resets the startup in
 * [after]; the database host is global, so the one installed before the class is put back.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DesktopAppStartupTest {
    private class FakeWindow : DesktopWindowHandle {
        override val sizeDp: IntSize = IntSize(1200, 800)
        override val density: Float = 1f
        override fun toFront() = Unit
    }

    private val mainThread = Executors.newSingleThreadExecutor { Thread(it, "test-main").apply { isDaemon = true } }.asCoroutineDispatcher()
    private lateinit var root: Path
    private var previousHost: DatabaseHost? = null
    private var restoreCwProject: () -> Unit = {}

    private fun paths(): DesktopPaths = DesktopPaths(
        { name ->
            when (name) {
                "XDG_DATA_HOME" -> root.resolve("data").toString()
                "XDG_CONFIG_HOME" -> root.resolve("config").toString()
                "XDG_CACHE_HOME" -> root.resolve("cache").toString()
                else -> null
            }
        },
        "Linux", root.resolve("home"),
    )

    private fun start(paths: DesktopPaths = paths()): StartupPhase =
        DesktopAppStartup.ensureStarted(paths, FakeWindow(), "en", repositories = { PropertyMap() }, notifierSource = net.bible.sharedui.desktop.assembly.DesktopNotifierSource(net.bible.sharedcore.platform.desktop.dbus.DesktopDbus().supportedOs) { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier })

    @BeforeTest fun before() {
        assertNull(KoinPlatform.getKoinOrNull(), "a Koin context leaked into this test")
        // The shared startup reaches Dispatchers.Main (MyDocumentBookManager's scope); the app has the AWT thread
        // (kotlinx-coroutines-swing in :desktopApp). Not on this classpath: it would move every Compose test's
        // navigation onto the AWT thread. A real thread stands in, for this class only.
        Dispatchers.setMain(mainThread)
        previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        restoreCwProject = snapshotCwProject()
        root = Files.createTempDirectory("desktop-startup")
    }

    @AfterTest fun after() {
        try {
            DesktopAppStartup.resetForTest()
        } finally {
            previousHost?.let { DatabaseContainer.installHost(it) }
            // A start here may have established JSword's project home inside [root]; later test classes in this JVM
            // must not keep a project home in a deleted directory.
            restoreCwProject()
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
            mainThread.close()
        }
    }

    @Test fun startupUsesInjectedNotifierSource() {
        val notifier = object : net.bible.sharedcore.platform.NativeNotifier {
            override val supportsProgress = false
            override fun show(notification: net.bible.sharedcore.platform.NativeNotification) = Unit
            override fun remove(id: String) = Unit
            override fun setOnActivated(handler: (String) -> Unit) = Unit
        }
        val source = DesktopNotifierSource(true) { notifier }
        assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths(), FakeWindow(), "en", repositories = { PropertyMap() }, notifierSource = source))
        assertSame(notifier, KoinPlatform.getKoin().get<net.bible.sharedcore.platform.NativeNotifier>())
    }

    @Test fun swordEpubIsDiscoveredBeforeKoinAndReadableAfterStartup() {
        val paths = paths()
        val epub = paths.sword.resolve("modules/epub/startup")
        Files.createDirectories(epub.resolve("META-INF"))
        Files.createDirectories(paths.sword.resolve("mods.d"))
        Files.writeString(epub.resolve("META-INF/container.xml"), """
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>
              <rootfile full-path="content.opf"/></rootfiles></container>
        """.trimIndent())
        Files.writeString(epub.resolve("content.opf"), """
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Startup EPUB</dc:title><dc:language>en</dc:language></metadata>
              <manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="chapter"/></spine>
            </package>
        """.trimIndent())
        Files.writeString(epub.resolve("chapter.xhtml"), """
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>Startup</title></head>
              <body><p>EPUB cold startup works.</p></body></html>
        """.trimIndent())
        Files.writeString(paths.sword.resolve("mods.d/startup-epub.conf"), """
            [StartupEpub]
            Description=Startup EPUB
            ModDrv=EpubBook
            DataPath=./modules/epub/startup/
            Encoding=UTF-8
            Lang=en
        """.trimIndent())
        assertNull(KoinPlatform.getKoinOrNull())
        assertEquals(StartupPhase.Ready, start(paths))
        val book = assertNotNull(Books.installed().getBook("StartupEpub"), "the scan must not skip the EPUB")
        val backend = assertNotNull(book.epubBackend)
        try {
            val key = backend.state.keys.first()
            assertTrue(backend.state.read(key).contains("EPUB cold startup works."))
        } finally {
            backend.state.close()
        }
    }

    @Test fun startsWithEmptyDataDir() {
        val paths = paths()
        assertEquals(StartupPhase.Ready, start(paths))
        val koin = assertNotNull(KoinPlatform.getKoinOrNull())
        assertTrue(HostStartup.isStarted)
        assertTrue(HostStartup.databasesOpen)
        assertTrue(DatabaseContainer.ready)
        assertEquals("en", HostStartup.stringTable.tag)
        assertTrue(HostStartup.stringTable.text("okay").isNotBlank())
        assertEquals(koin.get<HostStringTableHolder>().current.tag, HostStartup.stringTable.tag)
        // Databases and Sword folders live below the data directory.
        assertTrue(Files.isDirectory(paths.data.resolve("databases")))
        assertTrue(Files.list(paths.data.resolve("databases")).use { s -> s.count() } > 0, "no database file was created")
        assertTrue(Files.isDirectory(paths.sword.resolve("mods.d")))
        // The log file is configured into the data directory.
        assertTrue(Files.isDirectory(paths.data.resolve("logs")))
        // A second call joins the finished start: same graph, still ready.
        assertEquals(StartupPhase.Ready, start(paths))
        assertSame(koin, KoinPlatform.getKoin())
    }

    @Test fun installedEpubRoutesServeImagesAndStyles() = kotlinx.coroutines.runBlocking {
        val paths = paths()
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        val epub = paths.sword.resolve("epub/DesktopAssets")
        val content = epub.resolve("OEBPS")
        Files.createDirectories(epub.resolve("META-INF"))
        Files.createDirectories(content.resolve("images"))
        Files.createDirectories(content.resolve("css"))
        Files.writeString(epub.resolve("META-INF/container.xml"), """
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
            </container>
        """.trimIndent())
        Files.writeString(content.resolve("content.opf"), """
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Desktop Assets</dc:title><dc:language>en</dc:language></metadata>
              <manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
              <spine><itemref idref="chapter"/></spine>
            </package>
        """.trimIndent())
        Files.writeString(content.resolve("chapter.xhtml"), """
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>Assets</title>
              <link rel="stylesheet" type="text/css" href="css/book.css"/></head>
              <body><p>Illustrated chapter.</p><img src="images/pic.png"/></body>
            </html>
        """.trimIndent())
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0, 0xff.toByte())
        Files.write(content.resolve("images/pic.png"), png)
        Files.writeString(content.resolve("css/book.css"), "body {color: red;} p {color: red; line-height: 2; font-weight: bold;}")
        assertEquals(StartupPhase.Ready, start(paths))
        net.bible.service.sword.epub.addEpubBook(epub.toString().toPath(), KoinPlatform.getKoin().get(), KoinPlatform.getKoin().get())
        val book = assertNotNull(Books.installed().getBook("Epub-DesktopAssets"))
        val assets = KoinPlatform.getKoin().get<net.bible.sharedui.desktop.webengine.DesktopPageAssets>()
        val image = assertNotNull(assets.resolve("/module-style/epub/Epub-DesktopAssets/images/pic.png"))
        kotlin.test.assertContentEquals(png, Files.readAllBytes(image.file))
        assertEquals("image/png", image.contentType)
        var currentBook: org.crosswire.jsword.book.Book? = book
        val paneAssets = net.bible.sharedui.desktop.webview.DesktopPageAssetRoutes(
            backgrounds = { emptyMap() }, bookLocation = { null }, bookStyleFile = { null }, verify = { _, _ -> false },
            currentBook = { id -> if (id == "epub-pane") currentBook else null },
        )
        var paneOwner: String? = "epub-pane"
        val pane = paneAssets.forPane { paneOwner }
        val paneImage = assertNotNull(pane.resolve("/epub/images/pic.png"))
        kotlin.test.assertContentEquals(png, Files.readAllBytes(paneImage.file))
        assertEquals("image/png", paneImage.contentType)
        assertEquals(paneImage.file, assertNotNull(pane.resolve("/epub/images/pic.png")).file)
        assertEquals(content.resolve("images/pic.png").toRealPath(), paneImage.file.toRealPath(), "served in place, not copied")
        val cacheFiles = { Files.list(net.bible.sharedui.desktop.webview.DesktopAssetCache.directory()).use { it.count() } }
        val before = cacheFiles()
        repeat(3) {
            assertNotNull(paneAssets.forPane { "epub-pane" }.resolve("/epub/images/pic.png"))
            assertNotNull(assets.resolve("/module-style/epub/Epub-DesktopAssets/images/pic.png"))
        }
        assertEquals(before, cacheFiles(), "EPUB images must not create temp files")
        assertNull(paneAssets.forPane("other-pane").resolve("/epub/images/pic.png"))
        paneOwner = "other-pane"
        assertNull(pane.resolve("/epub/images/pic.png"), "an adopted transport uses its new owner")
        paneOwner = "epub-pane"
        assertNotNull(pane.resolve("/epub/images/pic.png"))
        currentBook = null
        assertNull(pane.resolve("/epub/images/pic.png"), "a document switch must not reuse the previous image")
        currentBook = book
        assertNull(pane.resolve("/epub/images/missing.png"))
        val backend = assertNotNull(book.epubBackend)
        val pageKey = backend.state.keys.first()
        val key = pageKey.getOsisRef()
        kotlinx.coroutines.withContext(mainThread) {
            val repository = KoinPlatform.getKoin().get<net.bible.android.control.page.window.WindowControl>().windowRepository
            repository.initialize()
            val window = repository.activeWindow
            val document = net.bible.android.control.page.OsisDocument(
                net.bible.android.misc.OsisFragment(fi.sykero.kdom.Element("div"), pageKey, book), book, pageKey,
            )
            val previousView = window.bibleView
            try {
                window.bibleView = java.lang.reflect.Proxy.newProxyInstance(
                    net.bible.android.control.page.window.WindowView::class.java.classLoader,
                    arrayOf(net.bible.android.control.page.window.WindowView::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "getFirstDocument" -> document
                        else -> error("Unexpected view call: ${method.name}")
                    }
                } as net.bible.android.control.page.window.WindowView
                val bound = net.bible.sharedui.desktop.webview.DesktopPageAssetRoutes.koinBound().forPane(window.id.toString())
                val boundImage = assertNotNull(bound.resolve("/epub/images/pic.png"))
                kotlin.test.assertContentEquals(png, Files.readAllBytes(boundImage.file))
            } finally {
                window.bibleView = previousView
            }
        }
        val css = assertNotNull(assets.resolve("/module-style/epub/Epub-DesktopAssets/$key/style.css"))
        assertEquals("text/css", css.contentType)
        assertEquals(" p {  font-weight: bold;}", Files.readString(css.file))
        val rawCss = assertNotNull(assets.resolve("/module-style/epub/Epub-DesktopAssets/css/book.css"))
        assertTrue(Files.readString(rawCss.file).contains("color: red"))
        assertNull(assets.resolve("/module-style/epub/Unknown/images/pic.png"))
        assertNull(assets.resolve("/module-style/epub/Epub-DesktopAssets/images/missing.png"))
        assertNull(assets.resolve("/module-style/epub/Epub-DesktopAssets/missing/style.css"))
        assertNull(assets.resolve("/epub/images/pic.png"), "an unbound pane has no current EPUB")
        for (path in listOf("../secret.txt", "images/../../secret.txt", "images\\..\\secret.txt")) {
            assertNull(assets.resolve("/module-style/epub/Epub-DesktopAssets/$path"))
            assertNull(pane.resolve("/epub/$path"))
        }
        val bundle = root.resolve("bundle").also { Files.createDirectories(it) }
        val handler = net.bible.sharedui.desktop.webengine.kcef.BundleRequestHandler(bundle, pageAssets = pane)
        val origin = net.bible.sharedui.desktop.webengine.kcef.BUNDLE_ORIGIN
        assertNotNull(handler.resolve("$origin/epub/images/pic.png"))
        assertNull(handler.resolve("$origin/epub/%2e%2e/secret.png"))
        val secret = root.resolve("secret.png").also { Files.writeString(it, "secret") }
        Files.createSymbolicLink(content.resolve("images/escape.png"), secret)
        assertNull(assets.resolve("/module-style/epub/Epub-DesktopAssets/images/escape.png"))
        assertNull(pane.resolve("/epub/images/escape.png"))
        val secretCss = root.resolve("secret.css").also { Files.writeString(it, "secret {font-weight: bold;}") }
        Files.delete(content.resolve("css/book.css"))
        Files.createSymbolicLink(content.resolve("css/book.css"), secretCss)
        assertEquals("", Files.readString(assertNotNull(assets.resolve("/module-style/epub/Epub-DesktopAssets/$key/style.css")).file))
        // Retirement waits for resource leases: this also proves that the desktop copy closed every stream.
        backend.state.close()
        assertNull(pane.resolve("/epub/images/pic.png"))
        assertNull(assets.resolve("/module-style/epub/Epub-DesktopAssets/$key/style.css"))
    }

    @Test fun csvRoundTripMatchesAndroidFormat() = kotlinx.coroutines.runBlocking {
        val paths = paths()
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(StartupPhase.Ready, start(paths))
        val koin = KoinPlatform.getKoin()
        val control = koin.get<net.bible.android.control.bookmark.BookmarkControl>()
        val bookmark = control.addOrUpdateBibleBookmark(net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = org.crosswire.jsword.passage.VerseRangeFactory.fromString(net.bible.android.database.bookmarks.KJVA, "Gen.1.1"),
            textRange = null, wholeVerse = true, book = null,
        ).apply { notes = "first; line\nsecond"; customIcon = "star"; new = true })
        val path = root.resolve("bookmark CSV # test.csv")
        val ref = net.bible.sharedui.app.ports.DocumentRef(path.toUri().toString())
        val dialogs = net.bible.sharedcore.ui.dialog.AppDialogController()
        val errors = mutableListOf<Throwable?>()
        val picker = object : net.bible.sharedui.app.ports.DocumentPicker {
            override suspend fun createDocument(suggestedName: String, mimeType: String) = ref
            override suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean) = listOf(ref)
            override suspend fun openDocumentTree(): net.bible.sharedui.app.ports.DocumentRef? = null
        }
        val notifier = object : net.bible.sharedcore.platform.UserNotifier {
            override fun showInfo(message: String, long: Boolean) = Unit
            override fun showError(message: String, cause: Throwable?) { errors += cause }
        }
        val csvFiles = net.bible.sharedui.desktop.misc.DesktopBookmarkCsvFiles()
        val flow = net.bible.sharedui.app.bookmark.BookmarkCsvHostFlow(
            { control }, picker, koin.get(), dialogs, { koin.get<HostStringTableHolder>().current }, notifier,
            { okio.FileSystem.SYSTEM.source(path.toString().toPath()) }, csvFiles::openSink, csvFiles::timestamp,
        )
        kotlinx.coroutines.coroutineScope {
            val job = async { flow.exportBookmarksCsv(listOf(bookmark)) }
            kotlinx.coroutines.withTimeout(10_000) { while (dialogs.pending.value == null) kotlinx.coroutines.delay(1) }
            val shown = dialogs.pending.value!!
            val request = (shown.request as net.bible.sharedcore.ui.dialog.AppDialogRequest.Localized).resolve() as net.bible.sharedcore.ui.dialog.AppDialogRequest.MultiChoice
            dialogs.respond(shown.id, net.bible.sharedcore.ui.dialog.AppDialogResult.SelectedMany(request.options.map { it.value }))
            job.await()
        }
        val text = Files.readString(path)
        assertTrue(text.startsWith("osisRef;bibleRef;document;book;"))
        assertTrue(text.contains("\"first; line\nsecond\""))
        assertTrue(errors.isEmpty(), "$errors")
        control.deleteBookmark(bookmark)
        flow.importBookmarksCsv()
        val restored = assertNotNull(control.bibleBookmarkById(bookmark.id))
        assertEquals("first; line\nsecond", restored.notes)
        assertEquals("star", restored.customIcon)
        assertTrue(errors.isEmpty(), "$errors")
    }

    @Test fun readingConfigUsesStockThemeForUnsetWorkspace() = kotlinx.coroutines.runBlocking {
        val paths = paths()
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(StartupPhase.Ready, start(paths))
        val koin = KoinPlatform.getKoin()
        val control = koin.get<net.bible.android.control.page.window.WindowControl>()
        kotlinx.coroutines.withContext(mainThread) { control.windowRepository.initialize() }
        val settings = koin.get<net.bible.sharedcore.platform.AppSettings>()
        for (seed in listOf(null, net.bible.sharedcore.reading.DEFAULT_WORKSPACE_COLOR_ARGB, 0xFFFF8000.toInt())) {
            control.windowRepository.workspaceSettings.workspaceColor = seed
            for (dark in listOf(false, true)) for (mode in net.bible.service.common.DisplayColorMode.entries) {
                settings.setString("display_color_mode", mode.value)
                val platform = net.bible.sharedui.host.reading.HostDocumentLoaderPlatform(
                    window = { control.activeWindow }, windowControl = { control }, settings = { settings },
                    nightModeState = { object : net.bible.sharedlogic.platform.NightModeState { override val nightMode = dark } },
                    isPortrait = { true }, isBeta = { false },
                )
                val json = kotlinx.coroutines.withContext(mainThread) { platform.configInputs(emptyList()).themeColors }
                val chosen = seed == 0xFFFF8000.toInt()
                if (mode == net.bible.service.common.DisplayColorMode.NORMAL && !chosen) {
                    assertTrue(json.contains(if (dark) "\"primary\":\"#D0BCFF\"" else "\"primary\":\"#6750A4\""), json)
                } else if (mode == net.bible.service.common.DisplayColorMode.NORMAL) {
                    // A chosen workspace colour must reach BibleView as Android sends it (it used to be "null").
                    assertTrue(json.startsWith("{\"primary\":\"#"), json)
                    assertTrue(!json.contains("#D0BCFF") && !json.contains("#6750A4"), "palette follows the chosen colour: $json")
                } else if (mode == net.bible.service.common.DisplayColorMode.COLOR_EINK && chosen) {
                    assertTrue(json.startsWith("{\"primary\":\"#"), json)
                } else {
                    assertEquals("null", json, "grey-base modes and unset-colour e-ink keep the host CSS palette")
                }
            }
        }
    }

    @Test fun sideBySidePaneReleasesFullWidthRailButKeepsTheSystemInset() = kotlinx.coroutines.runBlocking {
        val paths = paths()
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(StartupPhase.Ready, start(paths))
        val koin = KoinPlatform.getKoin()
        val control = koin.get<net.bible.android.control.page.window.WindowControl>()
        kotlinx.coroutines.withContext(mainThread) { control.windowRepository.initialize() }
        val repo = control.windowRepository
        val left = repo.activeWindow
        kotlinx.coroutines.withContext(mainThread) { repo.addNewWindow(left).apply { isPinMode = true } }
        repo.workspaceSettings.restoreButtonsVisible = true
        val offsets = net.bible.sharedui.reading.session.ReadingOffsets(restoreButtonsVisible = { true })
        offsets.setSystemBottomInset(34f)
        offsets.setRailCoveredWindows(setOf(left.id.toString(), repo.lastVisibleWindow!!.id.toString()))
        val platform = net.bible.sharedui.host.reading.HostDocumentLoaderPlatform(
            window = { left }, windowControl = { control }, settings = { koin.get() },
            nightModeState = { object : net.bible.sharedlogic.platform.NightModeState { override val nightMode = false } },
            offsets = { offsets }, isPortrait = { false }, isBeta = { false },
        )
        assertEquals(74f, platform.bottomOffset, "the full-width rail covers every side-by-side pane")
        assertTrue(platform.configInputs(emptyList()).isBottomWindow, "every side-by-side pane paints its system-inset band")
        offsets.setRailCoveredWindows(emptySet())
        assertEquals(34f, platform.bottomOffset, "hiding the rail must preserve the safe area")
        var catalogReads = 0
        val withFonts = net.bible.sharedui.host.reading.HostDocumentLoaderPlatform(
            window = { left }, windowControl = { control }, settings = { koin.get() },
            nightModeState = { object : net.bible.sharedlogic.platform.NightModeState { override val nightMode = false } },
            offsets = { offsets }, isPortrait = { false }, isBeta = { false },
            fontModuleNames = { catalogReads++; listOf("StudyFonts") },
        )
        assertEquals(listOf("StudyFonts"), withFonts.configInputs(emptyList()).fontModuleNames)
        assertEquals(1, catalogReads, "the config reads the injected catalog once per build")
    }

    /**
     * The state objects applied to the global snapshot after [action], collected until every one of [readSets] has a
     * member among them (or [APPLY_WAIT_MS] passes, which only a real regression reaches).
     *
     * Issue #4002: our own `sendApplyNotifications()` does not guarantee that the notification reached our observer
     * when it returns. Any earlier Compose test in the JVM starts the never-stopped `GlobalSnapshotManager`, which
     * applies the global snapshot on the AWT thread too. Compose takes the change set under its lock but calls the apply
     * observers after releasing it, so when the AWT thread wins, our call finds nothing to apply and returns while the
     * AWT thread is still calling the observers registered before ours. Asserting (and disposing the observer) at that
     * point missed the write. The observer may run on any thread, so it hands each change set over a channel.
     */
    private suspend fun appliedWritesAfter(vararg readSets: Set<Any>, action: suspend () -> Unit): Set<Any> {
        val applied = kotlinx.coroutines.channels.Channel<Set<Any>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val observer = androidx.compose.runtime.snapshots.Snapshot.registerApplyObserver { changed, _ -> applied.trySend(changed.toSet()) }
        val written = mutableSetOf<Any>()
        try {
            action()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            kotlinx.coroutines.withTimeoutOrNull(APPLY_WAIT_MS) {
                while (!readSets.all { reads -> reads.any { it in written } }) written.addAll(applied.receive())
            }
        } finally {
            observer.dispose()
        }
        return written
    }

    @Test fun railLabelsFollowARealDocumentChange() = kotlinx.coroutines.runBlocking {
        val paths = paths()
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        val originalConf = StartupFixture.files.getValue("mods.d/K4RawText.conf").decodeToString()
        Files.writeString(paths.sword.resolve("mods.d/SecondBible.conf"), originalConf.replace("[K4RawText]", "[SecondBible]"))
        assertEquals(StartupPhase.Ready, start(paths))
        kotlinx.coroutines.withContext(mainThread) {
            val koin = KoinPlatform.getKoin()
            val repo = koin.get<net.bible.android.control.page.window.WindowControl>().windowRepository
            repo.initialize()
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + mainThread)
            val navigator = object : net.bible.sharedui.host.app.HostNavigator {
                override fun navigate(route: String) = Unit
                override fun popBackStack() = false
                override fun popBackStackTo(route: String, inclusive: Boolean) = false
            }
            val previousRestoreAllowed = net.bible.sharedui.components.OverlayPresence.restoreAllowed
            var host: net.bible.sharedui.host.reading.HostReadingHost? = null
            try {
                host = net.bible.sharedui.host.reading.HostReadingHost(
                    scope, net.bible.sharedui.host.app.HostNavShell(scope, navigator), koin, DesktopReadingHostPlatform(FakeWindow()),
                )
                val painter = androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.Black)
                val icons = net.bible.sharedui.reading.ReadingToolbarIcons(painter, painter, painter, painter, painter, painter, painter, painter, painter)
                val deps = host.deps(icons)
                val window = repo.activeWindow
                val snapshot = deps.controller.layout.value.windows.first { it.id == window.id.toString() }
                val first = assertNotNull(Books.installed().getBook("K4RawText"))
                val second = assertNotNull(Books.installed().getBook("SecondBible"))
                window.pageManager.setCurrentDocument(first)
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                val reads = mutableSetOf<Any>()
                val before = androidx.compose.runtime.snapshots.Snapshot.observe({ reads += it }, null) { deps.windowLabel(snapshot) }
                val topReads = mutableSetOf<Any>()
                androidx.compose.runtime.snapshots.Snapshot.observe({ topReads += it }, null) { deps.windowTopLabel(snapshot) }
                val written = appliedWritesAfter(reads, topReads) {
                    window.pageManager.setCurrentDocument(second)
                    kotlinx.coroutines.yield() // Main-dispatched passage observers run on the test main thread.
                }
                assertEquals(first.abbreviation, before)
                assertEquals(second.abbreviation, deps.windowLabel(snapshot))
                assertTrue(reads.any { it in written }, "document change must invalidate the rail label")
                assertTrue(topReads.any { it in written }, "document change must invalidate the rail top label")

                val verseWritten = appliedWritesAfter(reads, topReads) {
                    net.bible.android.control.PassageChangeMediator.onBibleVerseSelected()
                    kotlinx.coroutines.yield()
                }
                assertTrue(reads.any { it in verseWritten }, "BibleVerseChanged must invalidate the rail label")
                assertTrue(topReads.any { it in verseWritten }, "BibleVerseChanged must invalidate the rail top label")
            } finally {
                try {
                    host?.dispose()
                } finally {
                    net.bible.sharedui.components.OverlayPresence.restoreAllowed = previousRestoreAllowed
                    scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
                }
                assertSame(previousRestoreAllowed, net.bible.sharedui.components.OverlayPresence.restoreAllowed)
            }
        }
    }

    @Test fun consecutiveLinkClicksFromOneWindowReuseTheLinksWindow() = kotlinx.coroutines.runBlocking {
        // Host regression 2026-11-03: every link click opened a NEW links window. The host web views swallow the touch
        // that makes a window active on Android, so the links window (active after the first link) was the source of the
        // next link and created its own target. Clicks must be routed from the window that carries the link.
        val paths = paths()
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(StartupPhase.Ready, start(paths))
        val koin = KoinPlatform.getKoin()
        val control = koin.get<net.bible.android.control.page.window.WindowControl>()
        val commands = koin.get<net.bible.sharedcore.window.WindowCommands>()
        val book = Books.installed().getBook("K4RawText")!!
        val repo = control.windowRepository
        kotlinx.coroutines.withContext(mainThread) {
            repo.initialize()
            val source = repo.activeWindow
            val key = book.getKey("Gen 1:1")
            // What the link handler does for a verse link: LinkControl -> WindowControl.showLink.
            val handler = { _: String -> control.showLink(book, key); true }
            fun linksWindows() = repo.windowList.filter { it.isLinksWindow }

            net.bible.sharedui.host.reading.openLinkInWindow(commands, source.id.toString(), handler, "osis://?osis=Gen.1.1")
            net.bible.sharedui.host.reading.openLinkInWindow(commands, source.id.toString(), handler, "osis://?osis=Gen.1.2")
            assertEquals(1, linksWindows().size, "two links from the same window share one links window")
            val links = linksWindows().single()

            // A link inside the links window follows Android: that window is the source (its own target, created once).
            net.bible.sharedui.host.reading.openLinkInWindow(commands, links.id.toString(), handler, "osis://?osis=Gen.1.3")
            net.bible.sharedui.host.reading.openLinkInWindow(commands, links.id.toString(), handler, "osis://?osis=Gen.1.4")
            assertEquals(2, linksWindows().size, "the links window's own links go to one target, reused")

            // And the first window still targets the first links window.
            net.bible.sharedui.host.reading.openLinkInWindow(commands, source.id.toString(), handler, "osis://?osis=Gen.1.5")
            assertEquals(2, linksWindows().size)
        }
    }

    @Test fun resetForTestCancelsAndJoinsTheWindowLoadsBeforeKoinStops() = kotlinx.coroutines.runBlocking {
        // Leak 2026-11-04: Window.loadText runs fetchDocument on Dispatchers.IO in the repository's scope. The test's
        // repository is WindowControl's lazy fallback, whose scope nothing cancelled: a load still running when Koin
        // stopped threw "KoinApplication has not been started" (CurrentPageBase.strings) into whichever test ran next.
        val paths = paths()
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(StartupPhase.Ready, start(paths))
        val control = KoinPlatform.getKoin().get<net.bible.android.control.page.window.WindowControl>()
        val repo = control.windowRepository
        kotlinx.coroutines.withContext(mainThread) {
            repo.initialize()
            repeat(3) { repo.activeWindow.loadText() }
        }
        val scopeJob = assertNotNull(repo.scope.coroutineContext[kotlinx.coroutines.Job])
        DesktopAppStartup.resetForTest()
        assertTrue(scopeJob.isCompleted, "a window load job outlived resetForTest (and with it Koin)")
    }

    @Test fun startsWithStartupFixtureModule() {
        val paths = paths()
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(StartupPhase.Ready, start(paths))
        val bibles = Books.installed().getBooks().filter { it.bookCategory == BookCategory.BIBLE }.map { it.initials }
        assertTrue("K4RawText" in bibles, "installed Bibles: $bibles")
    }

    @Test fun modulesOfOtherSwordAppsInHomeAreNotRead() {
        // A module in ~/.sword (another SWORD application's) must not become an installed book.
        val home = root.resolve("home")
        StartupFixture.files.forEach { (name, bytes) ->
            val target = home.resolve(".sword").resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        val previousHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", home.toString())
            assertEquals(StartupPhase.Ready, start())
            val books = Books.installed().getBooks().map { it.initials }
            assertFalse("K4RawText" in books, "a ~/.sword module was installed: $books")
            assertTrue(Files.isRegularFile(home.resolve(".sword/mods.d/K4RawText.conf")), "~/.sword must be left alone")
        } finally {
            System.setProperty("user.home", previousHome)
        }
    }

    @Test fun jswordProjectHomeIsTheModuleFolderAndTheHomeStaysEmpty() {
        val paths = paths()
        val home = Files.createDirectories(root.resolve("home"))
        val previousHome = System.getProperty("user.home")
        // JNA unpacks native libraries below user.home/.cache/JNA when a library is first loaded in the JVM (a library
        // cache, not app data). In a filtered run this test can be JNA's first user; in the full suite an earlier class
        // already loaded them. Point JNA's temp dir outside the checked home.
        val previousJnaTmp = System.getProperty("jna.tmpdir")
        System.setProperty("jna.tmpdir", Files.createDirectories(root.resolve("jna")).toString())
        try {
            System.setProperty("user.home", home.toString())
            // CWProject keeps the first project home of the process: start from a fresh one, as the app's first start does.
            resetCwProject()
            assertEquals(StartupPhase.Ready, start(paths))
            val projectDir = assertNotNull(CWProject.instance().writableProjectDirPath).toNioPath()
            assertTrue(projectDir.startsWith(paths.sword), "JSword project dir $projectDir is not inside ${paths.sword}")
            val written = Files.walk(home).use { s -> s.filter { it != home }.map { home.relativize(it).toString() }.toList() }
            assertEquals(emptyList(), written, "files were written under user.home")
        } finally {
            System.setProperty("user.home", previousHome)
            if (previousJnaTmp == null) System.clearProperty("jna.tmpdir") else System.setProperty("jna.tmpdir", previousJnaTmp)
        }
    }

    /**
     * CWProject's process-wide state: the returned function puts the home settings that `setHome` stores back and
     * restores the singleton. A singleton that had established its home before is put back as it was; one that had not
     * (it may have established it inside this test's temp dir since) is replaced by a fresh instance, which establishes
     * its home again on first use.
     */
    private fun snapshotCwProject(): () -> Unit {
        fun field(name: String) = (runCatching { CWProject::class.java.getDeclaredField(name) }.getOrNull()
            ?: CWProject.Companion::class.java.getDeclaredField(name)).apply { isAccessible = true }
        fun owner(f: java.lang.reflect.Field): Any? = if (java.lang.reflect.Modifier.isStatic(f.modifiers)) null else CWProject.Companion
        val statics = listOf("homeProperty", "homeDirectory", "homeAltDirectory").map(::field)
        val staticValues = statics.map { it.get(owner(it)) }
        val instanceField = field("instance")
        val instance = instanceField.get(owner(instanceField))
        val established = CWProject::class.java.getDeclaredField("writeHome").apply { isAccessible = true }.get(instance) != null
        return {
            statics.zip(staticValues).forEach { (f, v) -> f.set(owner(f), v) }
            if (established) instanceField.set(owner(instanceField), instance) else resetCwProject()
        }
    }

    /** CWProject's own test reset (a private static): a new instance that establishes its home again on first use. */
    private fun resetCwProject() {
        val method = runCatching { CWProject::class.java.getDeclaredMethod("reset") }
            .getOrElse { CWProject.Companion::class.java.getDeclaredMethod("reset") }
        method.isAccessible = true
        method.invoke(if (java.lang.reflect.Modifier.isStatic(method.modifiers)) null else CWProject.Companion)
    }

    @Test fun startupFailureMessageIncludesTheRootCause() {
        val failure = DesktopStartupException(paths().data,
            IllegalStateException("JSword setup failed", IllegalArgumentException("Unreadable module folder")))
        assertTrue(failure.message!!.contains("Unreadable module folder"), failure.message)
    }

    @Test fun unwritableDataDirFails() {
        val paths = paths()
        // The data directory's place is taken by a plain file, so it can be neither created nor written.
        Files.createDirectories(paths.data.parent)
        Files.write(paths.data, byteArrayOf(1))
        val phase = assertIs<StartupPhase.Failed>(start(paths))
        val message = assertNotNull(phase.error.message)
        assertTrue(paths.data.toString() in message, message)
        assertFalse(HostStartup.isStarted)
        assertFalse(HostStartup.databasesOpen)
        assertNull(KoinPlatform.getKoinOrNull())
    }

    @Test fun koinOnlyStartLeavesTheDatabasesClosedUntilAskedToOpen() {
        val paths = paths()
        assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, FakeWindow(), "en", openDatabases = false, repositories = { PropertyMap() }, notifierSource = net.bible.sharedui.desktop.assembly.DesktopNotifierSource(net.bible.sharedcore.platform.desktop.dbus.DesktopDbus().supportedOs) { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier }))
        assertNotNull(KoinPlatform.getKoinOrNull())
        assertTrue(HostStartup.isStarted)
        assertFalse(HostStartup.databasesOpen)
        assertFalse(DatabaseContainer.ready)
        assertEquals(StartupPhase.Ready, start(paths))
        assertTrue(HostStartup.databasesOpen)
    }

    @Test fun resetForTestForgetsEveryGlobal() {
        assertEquals(StartupPhase.Ready, start())
        DesktopAppStartup.resetForTest()
        assertNull(KoinPlatform.getKoinOrNull())
        assertFalse(HostStartup.isStarted)
        assertFalse(HostStartup.databasesOpen)
        assertFalse(DatabaseContainer.ready)
        // And a new start in a fresh directory works again.
        root.toFile().deleteRecursively()
        root = Files.createTempDirectory("desktop-startup")
        assertEquals(StartupPhase.Ready, start())
    }

    @Test fun shutdownHookRestoresTheWorkspaceAndReadingPositionAfterRestart() = kotlinx.coroutines.runBlocking {
        val paths = paths()
        StartupFixture.files.forEach { (name, bytes) ->
            val target = paths.sword.resolve(name)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        assertEquals(StartupPhase.Ready, start(paths))
        val reading = net.bible.sharedui.host.app.HostReadingReady
        try {
            reading.ensure(HostStartup.stringTable)
            val control = KoinPlatform.getKoin().get<net.bible.android.control.page.window.WindowControl>()
            val id = control.windowRepository.id
            kotlinx.coroutines.withContext(mainThread) {
                control.windowRepository.name = "Terminated workspace"
                val book = assertNotNull(Books.installed().getBook("K4RawText"))
                control.activeWindow.pageManager.setCurrentDocumentAndKey(book, book.getKey("John 3:16"))
            }
            var hook: Thread? = null
            DesktopExitPersistence(registerHook = { hook = it })
            assertNotNull(hook).run() // The exact body registered with Runtime, not the normal window-close path.
            reading.resetForTest()
            net.bible.service.startup.ReadingAppInit.resetForTest()
            DesktopAppStartup.resetForTest()
            assertEquals(StartupPhase.Ready, start(paths))
            reading.ensure(HostStartup.stringTable)
            val restored = KoinPlatform.getKoin().get<net.bible.android.control.page.window.WindowControl>().windowRepository
            assertEquals(id, restored.id)
            assertEquals("John.3.16", restored.activeWindow.pageManager.currentBible.singleKey.getOsisRef())
            assertEquals("Terminated workspace", restored.name)
        } finally {
            reading.resetForTest()
            net.bible.service.startup.ReadingAppInit.resetForTest()
        }
    }

    @Test fun exitWithOnlyKoinStartedDoesNotOpenOrSaveDatabases() {
        assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(
            paths(), FakeWindow(), "en", openDatabases = false, repositories = { PropertyMap() }, notifierSource = net.bible.sharedui.desktop.assembly.DesktopNotifierSource(net.bible.sharedcore.platform.desktop.dbus.DesktopDbus().supportedOs) { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier },
        ))
        var saved = false
        DesktopAppStartup.persistBeforeExit(saveWorkspace = { saved = true })
        assertFalse(saved)
        assertFalse(DesktopAppStartup.databasesOpen)
        assertFalse(DatabaseContainer.ready)
    }

    @Test fun exitRunsTheRegisteredTasksAfterTheWorkspaceSaveAndBounded() {
        assertEquals(StartupPhase.Ready, start())
        val koin = KoinPlatform.getKoin()
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        val tasks = koin.get<net.bible.sharedcore.platform.desktop.DesktopExitTasks>()
        tasks.register("first") { order += "task" }
        tasks.register("hang") { kotlinx.coroutines.awaitCancellation() }
        val started = System.nanoTime()
        DesktopAppStartup.persistBeforeExit(exitTasksTimeoutMs = 300, saveWorkspace = { order += "save" })
        val tookMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(listOf("save", "task"), order.toList())
        assertTrue(tookMs < 10_000, "persistBeforeExit took $tookMs ms")
    }

    @Test fun notifierCloseThatBlocksForeverDoesNotDelayExitTasks() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(1)
        val notifier = object : net.bible.sharedcore.platform.NativeNotifier, AutoCloseable {
            override val supportsProgress = true
            override fun show(n: net.bible.sharedcore.platform.NativeNotification) = Unit
            override fun remove(id: String) = Unit
            override fun setOnActivated(handler: (String) -> Unit) = Unit
            override fun close() {
                entered.countDown()
                try { release.await() } finally { finished.countDown() }
            }
        }
        assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(
            paths(), FakeWindow(), "en", repositories = { PropertyMap() }, notifierFactory = { notifier },
        ))
        val koin = KoinPlatform.getKoin()
        koin.get<net.bible.sharedcore.platform.NativeNotifier>()
        val tasksRan = java.util.concurrent.CountDownLatch(1)
        koin.get<net.bible.sharedcore.platform.desktop.DesktopExitTasks>().register("test") { tasksRan.countDown() }
        val host = DatabaseContainer.host
        val flushed = java.util.concurrent.CountDownLatch(1)
        var flushTimeout: Long? = null
        DatabaseContainer.installHost(object : DatabaseHost by host {
            override fun flushSettingsBeforeExit(store: net.bible.sharedcore.settings.store.SettingsStore?, timeoutMs: Long?) {
                flushTimeout = timeoutMs
                host.flushSettingsBeforeExit(store, timeoutMs)
                flushed.countDown()
            }
        })
        val worker = Thread { DesktopAppStartup.persistBeforeExit(exitTasksTimeoutMs = 300, saveWorkspace = {}) }
        try {
            worker.start()
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(tasksRan.await(1_500, java.util.concurrent.TimeUnit.MILLISECONDS))
            assertTrue(flushed.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(2_000L, flushTimeout)
            worker.join(2_000)
            assertFalse(worker.isAlive)
        } finally {
            release.countDown()
            worker.join(5_000)
            assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS))
            DatabaseContainer.installHost(host)
        }
    }

    @Test fun launchArgumentsAndLifecycleEventsReachTheGraph() {
        val events = net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents()
        assertEquals(
            StartupPhase.Ready,
            DesktopAppStartup.ensureStarted(paths(), FakeWindow(), "en", repositories = { PropertyMap() }, notifierSource = net.bible.sharedui.desktop.assembly.DesktopNotifierSource(net.bible.sharedcore.platform.desktop.dbus.DesktopDbus().supportedOs) { net.bible.sharedcore.platform.desktop.dbus.NoOpNativeNotifier }, launchArgs = listOf("a.zip"), lifecycleEvents = events),
        )
        val koin = KoinPlatform.getKoin()
        assertEquals(listOf("a.zip"), koin.get<DesktopLaunchArgs>().args)
        assertSame(events, koin.get<net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents>())
    }
}
