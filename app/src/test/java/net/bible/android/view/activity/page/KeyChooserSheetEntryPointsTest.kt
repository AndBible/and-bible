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
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.platform.AndroidKeyChooserLauncher
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.test.DatabaseResetter
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * #4169: every Bible/commentary key-chooser entry point opens the reading view's passage quick sheet (the one a title
 * tap opens), never a full-screen book grid: the `KeyChooserLauncher` port (`CurrentPageManager`'s auto-open after a
 * document switch), the reading navigator (the page's Ctrl+B) and a chooser asked for by an inactive window, which is
 * activated first because the sheet answers the active window. Dictionaries keep their full-screen chooser.
 *
 * The host is built as [RefChooserSheetTest] builds it: a reading-route [NavHostComposeActivity] that is never created,
 * with a [ComposeReadingViewHost] that is never installed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class KeyChooserSheetEntryPointsTest {
    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: NavHostComposeActivity
    private lateinit var host: ComposeReadingViewHost

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = net.bible.test.testWindowRepository()
        windowControl.windowRepository = windowRepository
        windowRepository.initializeAtEdge()

        activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        activity.readingAppBootstrap.windowRepository = windowRepository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
        host = net.bible.test.fixtureReadingHost(activity)
        activity.composeReadingViewHost = host
    }

    @After
    fun tearDown() {
        activity.composeReadingViewHost = null
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private val manager: CurrentPageManager get() = windowRepository.activeWindow.pageManager

    private fun assertOpensPassageSheet(start: (CurrentPage) -> Unit, select: (CurrentPageManager) -> CurrentPage) {
        start(select(manager))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(KeyChooserKind.Grid, (host.quickSheet.value as? ReadingQuickSheet.KeyChooser)?.kind,
            "the passage quick sheet: ${host.quickSheet.value}")
        assertNull(shadowOf(activity).nextStartedActivityForResult, "no full-screen chooser is started")
    }

    private fun launcher(page: CurrentPage) {
        AndroidKeyChooserLauncher { activity }.startKeyChooser(page)
    }

    private fun navigator(page: CurrentPage) = AndroidReadingNavigator({ activity }).startKeyChooser(page)

    @Test fun launcherOpensTheSheetForABible() = assertOpensPassageSheet(::launcher) { it.currentBible }
    @Test fun launcherOpensTheSheetForACommentary() = assertOpensPassageSheet(::launcher) { it.currentCommentary }
    @Test fun launcherOpensTheSheetForMyNotes() = assertOpensPassageSheet(::launcher) { it.currentMyNotePage }
    @Test fun readingNavigatorCtrlKeyBOpensTheSheet() = assertOpensPassageSheet(::navigator) { it.currentBible }

    @Test fun anInactiveWindowsChooserActivatesItAndOpensTheSheet() {
        val asking = windowRepository.activeWindow
        val other = windowControl.addNewWindow(asking)
        windowRepository.activeWindow = other
        assertSame(other, windowRepository.activeWindow)
        launcher(asking.pageManager.currentBible)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(KeyChooserKind.Grid, (host.quickSheet.value as? ReadingQuickSheet.KeyChooser)?.kind)
        assertSame(asking, windowRepository.activeWindow, "the sheet answers the active window, so the asker is activated")
    }

    @Test fun aDictionaryKeepsItsFullScreenChooser() {
        launcher(manager.currentDictionary)
        shadowOf(Looper.getMainLooper()).idle()
        val started = assertNotNull(shadowOf(activity).nextStartedActivityForResult)
        assertEquals(NavRoutes.CHOOSE_DICTIONARY_WORD, started.intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
        assertNull(host.quickSheet.value)
    }
}
