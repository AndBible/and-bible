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
package net.bible.sharedui.reading.toolbar

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.GlobalTextDisplaySettingsSource
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.page.window.WorkspacePersistence
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.service.download.FakeBookFactory
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedlogic.platform.NightModeState
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * #4169 review: [activateWindowOf] activates a window for a key-chooser sheet only when that window is on screen.
 * A minimised window, or one hidden behind a maximised window, must not become the active window (a sheet for an
 * invisible pane would change the user's active window).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivateWindowOfTest {
    private val job = SupervisorJob()
    private lateinit var repo: WindowRepository
    private lateinit var control: WindowControl
    private lateinit var active: Window
    private lateinit var sibling: Window

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val settings = proxy<AppSettings> { method, args ->
            when (method) {
                "getString", "getStringSet", "getBoolean", "getInt", "getLong" -> args.getOrNull(1)
                else -> null
            }
        }
        startKoin {
            modules(module {
                single { settings }
                single { WindowStateServiceImpl() }
                single { WorkspacePersistence(OrderedLauncher(CoroutineScope(Job().apply { cancel() }))) { error("no database") } }
                single { FakeBookFactory(proxy<CoreStrings> { method, _ -> method }) }
                single<GlobalTextDisplaySettingsSource> { object : GlobalTextDisplaySettingsSource {
                    override val current = WorkspaceEntities.TextDisplaySettings.default
                } }
                single<NightModeState> { object : NightModeState { override val nightMode = false } }
            })
        }
        control = WindowControl()
        repo = WindowRepository(CoroutineScope(Dispatchers.Unconfined + job))
        control.windowRepository = repo
        active = window()
        sibling = window()
        repo.windowList.addAll(listOf(active, sibling))
        repo.activeWindow = active
    }

    @AfterTest
    fun tearDown() {
        repo.windowList.forEach { it.destroy(destroyView = false) }
        runBlocking { job.cancelAndJoin() }
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun aVisibleWindowBecomesActive() {
        assertTrue(activateWindowOf(sibling.pageManager.currentPage))
        assertSame(sibling, repo.activeWindow)
    }

    @Test
    fun theActiveWindowStaysActiveAndIsAccepted() {
        assertTrue(activateWindowOf(active.pageManager.currentPage))
        assertSame(active, repo.activeWindow)
    }

    @Test
    fun aMinimisedWindowIsNotActivated() {
        sibling.windowState = WindowState.MINIMISED
        assertFalse(activateWindowOf(sibling.pageManager.currentPage))
        assertSame(active, repo.activeWindow)
    }

    @Test
    fun aWindowHiddenBehindAMaximisedWindowIsNotActivated() {
        control.maximiseWindow(active)
        assertFalse(activateWindowOf(sibling.pageManager.currentPage))
        assertSame(active, repo.activeWindow)
    }

    @Test
    fun aWindowNoLongerInTheWorkspaceIsNotActivated() {
        repo.windowList.remove(sibling)
        assertFalse(activateWindowOf(sibling.pageManager.currentPage))
        assertSame(active, repo.activeWindow)
    }

    private fun window() = Window(
        WorkspaceEntities.Window(
            workspaceId = IdType(), isSynchronized = false, isPinMode = false, isLinksWindow = false,
            windowLayout = WorkspaceEntities.WindowLayout(WindowState.VISIBLE.name), id = IdType(),
        ),
        CurrentPageManager(blank<BibleTraverser>(), blank<BookmarkControl>(), control),
        repo,
    )

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args ?: emptyArray()) } as T

    private inline fun <reified T> blank(): T {
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return type.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }
}
