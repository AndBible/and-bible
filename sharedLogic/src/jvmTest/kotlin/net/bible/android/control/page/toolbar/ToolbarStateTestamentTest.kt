/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * Licensed under the GNU General Public License, version 3 or later.
 */
package net.bible.android.control.page.toolbar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.GlobalTextDisplaySettingsSource
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakPlatform
import net.bible.service.device.speak.TextToSpeechServiceManager
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.download.FakeBookFactory
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.UserNotifier
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Issue #4041: the reading toolbar's Strong's button must show the Greek glyph in the New Testament
 * and the Hebrew glyph in the Old. [ToolbarStateServiceImpl] publishes which testament the active
 * window is in, and the published value follows navigation (a verse change re-runs the snapshot
 * through `PassageChangeMediator`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolbarStateTestamentTest {
    private val windowJob = SupervisorJob()
    private val values = mutableMapOf<String, Any?>()
    private lateinit var pageManager: CurrentPageManager
    private lateinit var window: Window
    private lateinit var service: ToolbarStateServiceImpl
    private var speak: SpeakControl? = null
    private val kjv get() = Versifications.instance().getVersification("KJV")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val settings = proxy<AppSettings> { method, args ->
            when (method) {
                "getString" -> values[args[0] as String] as String? ?: args.getOrNull(1)
                "getStringSet" -> args.getOrNull(1)
                "getBoolean" -> args[1]
                "getInt" -> args[1]
                else -> error("Unexpected settings: $method")
            }
        }
        // Pseudo-document descriptions (My Notes, Journal, ...) are read while listing commentaries.
        val strings = proxy<CoreStrings> { method, _ -> method }
        val fakeBooks = FakeBookFactory(strings)
        startKoin { modules(module { single { settings }; single { fakeBooks }; single { WindowStateServiceImpl() }
            single<GlobalTextDisplaySettingsSource> { object : GlobalTextDisplaySettingsSource {
                override val current = WorkspaceEntities.TextDisplaySettings.default
            } }
        }) }
        val windowControl = WindowControl()
        val windowRepository = WindowRepository(kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined + windowJob))
        pageManager = CurrentPageManager(blank<BibleTraverser>(), blank<BookmarkControl>(), windowControl)
        pageManager.currentBible.onlySetCurrentDocument(fakeBooks.createFakeRepoBook(
            "TestBible", "[TestBible]\nDescription=TestBible\nCategory=Biblical Texts\nVersification=KJV\nAndBibleSpecial=1", "",
        ))
        window = Window(WorkspaceEntities.Window(IdType(), false, false, windowLayout = WorkspaceEntities.WindowLayout("VISIBLE")), pageManager, windowRepository)
        windowControl.windowRepository = windowRepository
        windowRepository.activeWindow = window
        val documentControl = DocumentControl(fakeBooks, windowControl, settings, proxy<UserNotifier> { method, _ -> error(method) }, strings,
            blank<AppCoroutineScope>(), { error("unused") }, { error("unused") }, {})
        val scope = AppCoroutineScope(Dispatchers.Unconfined + windowJob)
        val speakControl = SpeakControl(lazy { blank<TextToSpeechServiceManager>() }, windowControl,
            proxy<SpeakPlatform> { method, _ -> when (method) {
                "isSupported" -> false
                "attachMediaControls" -> Unit
                else -> error(method)
            } },
            proxy<UserNotifier> { method, _ -> error(method) }, strings, settings, scope,
            AdvancedSpeakSettings(settings), OrderedLauncher(scope), fakeBooks).also { speak = it }
        service = ToolbarStateServiceImpl(windowControl, documentControl, PageControl(windowControl), speakControl,
            WindowStateServiceImpl(), EventSource<Boolean>())
    }

    @AfterTest
    fun tearDown() {
        if (::service.isInitialized) service.close()
        if (::window.isInitialized) window.destroy()
        speak?.release()
        runBlocking { windowJob.cancelAndJoin() }
        stopKoin()
        Dispatchers.resetMain()
    }

    /**
     * Moves the active window's Bible to [book] 1:1 and announces it the way navigation does
     * (`PageChange.VerseChanged`). `setKey` would also start a document load in the window, which
     * needs the whole rendering graph; the toolbar only listens to the verse-change event.
     */
    private fun navigateTo(book: BibleBook) {
        pageManager.currentBible.doSetKey(Verse(kjv, book, 1, 1))
        PassageChangeMediator.onCurrentVerseChanged(window)
    }

    @Test
    fun reportsOldTestamentForAnOldTestamentVerse() {
        navigateTo(BibleBook.GEN)
        // A real snapshot, not the EMPTY placeholder whose isNewTestament is also false.
        assertTrue(service.toolbar.value.pageTitle.startsWith("Gen"), service.toolbar.value.pageTitle)
        assertFalse(service.toolbar.value.isNewTestament)
    }

    @Test
    fun reportsNewTestamentForANewTestamentVerseAndFollowsNavigation() {
        navigateTo(BibleBook.MATT)
        assertTrue(service.toolbar.value.isNewTestament, "Matthew is New Testament")
        navigateTo(BibleBook.MAL)
        assertFalse(service.toolbar.value.isNewTestament, "Malachi is Old Testament")
        navigateTo(BibleBook.REV)
        assertTrue(service.toolbar.value.isNewTestament, "Revelation is New Testament")
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args ?: emptyArray()) } as T

    private inline fun <reified T> blank(): T {
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return type.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }
}
