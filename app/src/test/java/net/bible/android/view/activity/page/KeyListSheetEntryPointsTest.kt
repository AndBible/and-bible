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
package net.bible.android.view.activity.page

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.platform.AndroidKeyChooserLauncher
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.test.DatabaseResetter
import org.crosswire.common.activate.Activator
import org.crosswire.common.util.NetUtil
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * #4172: every general-book and map key-chooser entry point opens the reading view's key-list quick sheet (the one a
 * title tap opens), never a full-screen chooser: the `KeyChooserLauncher` port, `CurrentPageManager`'s auto-open after
 * switching to a general book or map without a key, the reading navigator (the page's Ctrl+B), `ReadingCommands`'
 * title-tap fallback, and a chooser asked for by an inactive window, which is activated first because the sheet answers
 * the active window.
 *
 * The host is built as [KeyChooserSheetEntryPointsTest] builds it. The general book is the K4 corpus's `K4GenBook`, the
 * map the same module read as `Category=Maps`; both are registered for the test and removed afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class KeyListSheetEntryPointsTest {
    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: NavHostComposeActivity
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<NavHostComposeActivity>>()
    private lateinit var host: ComposeReadingViewHost
    private lateinit var genBook: Book
    private lateinit var map: Book

    @Before
    fun setUp() {
        genBook = register(k4Book("K4GenBook", "K4GenBookFixture"))
        map = register(k4Book("K4GenBook", "K4MapFixture", category = "Maps"))
        windowControl = CommonUtils.windowControl
        windowRepository = net.bible.test.testWindowRepository()
        windowControl.windowRepository = windowRepository
        windowRepository.initializeAtEdge()

        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        )
        controllers.add(controller)
        activity = controller.get()
        activity.readingAppBootstrap.windowRepository = windowRepository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
        host = net.bible.test.fixtureReadingHost(activity)
        activity.composeReadingViewHost = host
    }

    @After
    fun tearDown() {
        try {
            activity.composeReadingViewHost = null
            CurrentActivityHolder.forgetActivitiesForTest()
            net.bible.test.closeReadingHostsBeforeReset(controllers, reset = { DatabaseResetter.resetDatabase(windowRepository.scope) })
        } finally {
            registered.forEach { Activator.deactivate(it); Books.installed().removeBook(it) }
        }
    }

    private val registered = mutableListOf<Book>()

    private fun register(book: Book): Book = book.also {
        Books.installed().addBook(it)
        registered += it
    }

    /** A K4 corpus module under its own [initials], read as [category] when given. */
    private fun k4Book(module: String, initials: String, category: String? = null): Book {
        val root = File("../jsword/src/commonTest/resources/k4-modules/space dir/ä")
        val conf = root.resolve("mods.d/$module.conf").readText().replace("[$module]", "[$initials]")
        val md = SwordBookMetaData(conf.toByteArray(), "$initials.conf").apply {
            library = root.toURI()
            location = NetUtil.lengthenURI(library!!, getProperty(SwordBookMetaData.KEY_DATA_PATH)!!)
            category?.let { setProperty("Category", it) }
        }
        return md.bookType!!.createBook(md)
    }

    private val manager: CurrentPageManager get() = windowRepository.activeWindow.pageManager

    /** Opens [book] in [m]'s window at its first key, as the page a chooser is then asked for. */
    private fun opened(book: Book, m: CurrentPageManager = manager): CurrentPage {
        m.setCurrentDocumentAndKey(book, book.getKey("Part 1"))
        return m.currentPage
    }

    private fun generalBookPage(m: CurrentPageManager = manager) = opened(genBook, m)
    private fun mapPage(m: CurrentPageManager = manager) = opened(map, m)

    /** Drains Main until a sheet shows: the keys are resolved on a background dispatcher first. */
    private fun idleUntilASheet() {
        val deadline = System.currentTimeMillis() + 10_000
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (host.quickSheet.value != null) return
            Thread.sleep(10)
        } while (System.currentTimeMillis() < deadline)
    }

    private fun assertKeyListSheet(kind: KeyChooserKind) {
        idleUntilASheet()
        assertEquals(kind, (host.quickSheet.value as? ReadingQuickSheet.KeyChooser)?.kind,
            "the key-list quick sheet: ${host.quickSheet.value}")
        assertEquals(listOf("Part 1", "Chapter 1", "Chapter 2", "Part 2"), activityHostKeys().map { it.getName() })
        assertNull(shadowOf(activity).nextStartedActivityForResult, "no full-screen chooser is started")
    }

    private fun activityHostKeys() = host.hostState.keyChooserKeys

    private fun launcher(page: CurrentPage) = AndroidKeyChooserLauncher { activity }.startKeyChooser(page)

    @Test fun launcherOpensTheSheetForAGeneralBook() {
        assertEquals(true, launcher(generalBookPage()))
        assertKeyListSheet(KeyChooserKind.GeneralBook)
    }

    @Test fun launcherOpensTheSheetForAMap() {
        assertEquals(true, launcher(mapPage()))
        assertKeyListSheet(KeyChooserKind.Map)
    }

    @Test fun readingNavigatorCtrlKeyBOpensTheSheetForAGeneralBook() {
        AndroidReadingNavigator({ activity }).startKeyChooser(generalBookPage())
        assertKeyListSheet(KeyChooserKind.GeneralBook)
    }

    @Test fun readingNavigatorCtrlKeyBOpensTheSheetForAMap() {
        AndroidReadingNavigator({ activity }).startKeyChooser(mapPage())
        assertKeyListSheet(KeyChooserKind.Map)
    }

    /** `CurrentPageManager.setCurrentDocument`'s auto-open, through the app's own `KeyChooserLauncher` binding. */
    @Test fun switchingToAGeneralBookOpensTheSheet() {
        CurrentActivityHolder.activate(activity)
        manager.setCurrentDocument(genBook)
        assertKeyListSheet(KeyChooserKind.GeneralBook)
    }

    @Test fun switchingToAMapOpensTheSheet() {
        CurrentActivityHolder.activate(activity)
        manager.setCurrentDocument(map)
        assertKeyListSheet(KeyChooserKind.Map)
    }

    /** `ReadingCommands`' title tap on a map still opens the key-list sheet (its own direct opening, kept). */
    @Test fun readingCommandsTitleTapOpensTheSheetForAMap() {
        mapPage()
        activity.readingCommands.composeStartKeyChooser()
        assertKeyListSheet(KeyChooserKind.Map)
    }

    /** `ReadingCommands`' fallback with no reading view mounted: no full-screen chooser any more, nothing is started. */
    @Test fun readingCommandsFallbackWithoutAReadingViewStartsNoFullScreenChooser() {
        mapPage()
        activity.composeReadingViewHost = null
        activity.readingCommands.composeStartKeyChooser()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(host.quickSheet.value)
        assertNull(shadowOf(activity).nextStartedActivityForResult, "no full-screen chooser is started")
    }

    /** A reading view whose workspace does not own the asking page (another live host's) shows nothing. */
    @Test fun aPageOfAnotherHostsWorkspaceIsNotShownHere() {
        val foreign = net.bible.test.testWindowRepository()
        try {
            foreign.initializeAtEdge()
            val page = opened(genBook, foreign.activeWindow.pageManager)
            assertEquals(false, launcher(page))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(host.quickSheet.value)
        } finally {
            windowControl.windowRepository = windowRepository
        }
    }

    @Test fun anInactiveWindowsChooserActivatesItAndOpensTheSheet() {
        val asking = windowRepository.activeWindow
        val page = generalBookPage(asking.pageManager)
        val other = windowControl.addNewWindow(asking)
        windowRepository.activeWindow = other
        assertSame(other, windowRepository.activeWindow)
        launcher(page)
        assertKeyListSheet(KeyChooserKind.GeneralBook)
        assertSame(asking, windowRepository.activeWindow, "the sheet answers the active window, so the asker is activated")
    }

    /** With no reading view in front there is nothing to show the sheet on: nothing is started, and the caller is told. */
    @Test fun withoutAReadingHostNothingIsShownAndFalseIsReturned() {
        activity.composeReadingViewHost = null
        assertEquals(false, launcher(mapPage()))
        assertEquals(false, launcher(generalBookPage()))
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(host.quickSheet.value)
        assertNull(shadowOf(activity).nextStartedActivityForResult, "no full-screen chooser is started")
    }
}
