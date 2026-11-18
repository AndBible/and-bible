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
package net.bible.android.control.page.window

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import net.bible.sharedcore.platform.OrderedLauncher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.service.download.FakeBookFactory
import net.bible.android.control.page.GlobalTextDisplaySettingsSource
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #4141: which windows the reading area shows while a window is maximised. The maximised window M is shown with its
 * links window L beside it (two panes, as in the split) exactly while L is open (`VISIBLE`); every other window stays
 * hidden. Maximising hides an open links window (plain M, as before) and un-maximising brings it back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaximisedLinksPaneLayoutTest {
    private val job = SupervisorJob()
    private lateinit var windowState: WindowStateServiceImpl
    private lateinit var repo: WindowRepository
    private lateinit var control: WindowControl
    private lateinit var main: Window
    private lateinit var other: Window
    private lateinit var links: Window

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        windowState = WindowStateServiceImpl()
        val settings = proxy<AppSettings> { method, args ->
            when (method) {
                "getString", "getStringSet", "getBoolean", "getInt", "getLong" -> args.getOrNull(1)
                else -> null
            }
        }
        startKoin {
            modules(module {
                single { settings }
                single { windowState }
                // Window deletions are not under test: a cancelled scope drops them.
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
        main = window(repo, control, links = false)
        other = window(repo, control, links = false, state = WindowState.MINIMISED)
        links = window(repo, control, links = true, state = WindowState.MINIMISED)
        repo.windowList.addAll(listOf(main, other, links))
        repo.primaryTargetLinksWindowId = links.id
        repo.activeWindow = main
    }

    @AfterTest
    fun tearDown() {
        repo.windowList.forEach { it.destroy(destroyView = false) }
        runBlocking { job.cancelAndJoin() }
        stopKoin()
        Dispatchers.resetMain()
    }

    private fun window(repo: WindowRepository, control: WindowControl, links: Boolean, state: WindowState = WindowState.VISIBLE) =
        Window(
            WorkspaceEntities.Window(
                workspaceId = IdType(), isSynchronized = false, isPinMode = false, isLinksWindow = links,
                windowLayout = WorkspaceEntities.WindowLayout(state.name), id = IdType(),
            ),
            CurrentPageManager(blank<BibleTraverser>(), blank<BookmarkControl>(), control),
            repo,
        )

    private val shownIds get() = windowState.layout.value.windows.filter { it.isVisible }.map { it.id }

    @Test
    fun aMaximisedWindowWithAnOpenLinksWindowShowsBothAndNothingElse() {
        other.windowState = WindowState.VISIBLE
        control.maximiseWindow(main)
        links.windowState = WindowState.VISIBLE
        repo.notifyWindowsChanged()

        assertEquals(listOf(main, links), repo.visibleWindows)
        assertTrue(links.isVisible)
        assertFalse(other.isVisible, "other windows stay hidden in maximised mode")
        assertEquals(listOf(main.id.toString(), links.id.toString()), shownIds, "the Compose split draws these two")
        assertEquals(main.id.toString(), windowState.layout.value.maximizedWindowId)
    }

    @Test
    fun aMaximisedWindowWithoutAnOpenLinksWindowShowsOnlyItself() {
        control.maximiseWindow(main)

        assertEquals(listOf(main), repo.visibleWindows)
        assertFalse(links.isVisible)
        assertEquals(listOf(main.id.toString()), shownIds)
    }

    @Test
    fun aLinksWindowOfAnotherWindowIsNotShownBesideTheMaximisedOne() {
        val otherLinks = window(repo, control, links = true)
        repo.windowList.add(otherLinks)
        control.maximiseWindow(main)

        assertEquals(listOf(main), repo.visibleWindows)
        assertFalse(otherLinks.isVisible)
    }

    @Test
    fun maximisingHidesAnOpenLinksWindowAndUnMaximisingBringsItBack() {
        links.windowState = WindowState.VISIBLE
        assertEquals(listOf(main, links), repo.visibleWindows, "premise: the links window is open in the split")

        control.maximiseWindow(main)
        assertEquals(listOf(main), repo.visibleWindows, "maximise shows the window alone, as before")

        control.unMaximise()
        assertEquals(listOf(main, links), repo.visibleWindows, "the split is as it was")
    }

    @Test
    fun unMaximisingKeepsTheLinksWindowOpenedBesideTheMaximisedWindow() {
        control.maximiseWindow(main)
        links.windowState = WindowState.VISIBLE

        control.unMaximise()

        assertNull(repo.maximizedWindowId)
        assertEquals(listOf(main, links), repo.visibleWindows, "L stays as the normal links window")
    }

    @Test
    fun dismissingTheLinksWindowReturnsToThePlainMaximisedWindow() {
        control.maximiseWindow(main)
        links.windowState = WindowState.VISIBLE
        repo.activeWindow = links
        assertTrue(control.isWindowMinimizable(links), "the links pane beside the maximised window can be dismissed")

        control.dismissWindow(links)

        assertEquals(main.id, repo.maximizedWindowId, "still maximised")
        assertEquals(listOf(main), repo.visibleWindows)
        assertEquals(main, repo.activeWindow)
    }

    @Test
    fun closingTheLinksWindowReturnsToThePlainMaximisedWindowWithoutTouchingItsSplitSize() {
        main.weight = 0.4f
        control.maximiseWindow(main)
        links.windowState = WindowState.VISIBLE

        control.closeWindow(links)

        assertEquals(main.id, repo.maximizedWindowId)
        assertEquals(listOf(main), repo.visibleWindows)
        assertEquals(0.4f, main.weight, "M's size in the normal split is kept")
    }

    @Test
    fun maximisingAnotherWindowShowsItAlone() {
        control.maximiseWindow(main)
        links.windowState = WindowState.VISIBLE
        control.unMaximise()

        control.maximiseWindow(other)

        assertEquals(listOf(other), repo.visibleWindows)
    }

    @Test
    fun maximisingAnotherWindowWhileMaximisedStillBringsTheHiddenLinksWindowBack() {
        other.windowState = WindowState.VISIBLE
        links.windowState = WindowState.VISIBLE
        control.maximiseWindow(main)

        control.maximiseWindow(other)
        control.unMaximise()

        assertEquals(WindowState.VISIBLE, links.windowState, "the links window hidden by the first maximise is back")
        assertTrue(links.isVisible)
    }

    @Test
    fun closingTheMaximisedWindowBringsTheHiddenLinksWindowBack() {
        other.windowState = WindowState.VISIBLE
        links.windowState = WindowState.VISIBLE
        control.maximiseWindow(main)

        control.closeWindow(main)

        assertNull(repo.maximizedWindowId)
        assertTrue(links.isVisible, "the split comes back with its links window")
        assertTrue(other.isVisible)
        assertEquals(listOf(other, links), repo.visibleWindows)
    }

    @Test
    fun aClosedPrimaryLinksWindowIsNotFallenBackFromToAnotherLinksWindow() {
        val chained = window(repo, control, links = true)
        repo.windowList.add(chained)
        control.maximiseWindow(main)
        links.windowState = WindowState.VISIBLE
        assertEquals(listOf(main, links), repo.visibleWindows)

        control.closeWindow(links)

        assertNull(repo.primaryTargetLinksWindowId)
        assertNull(repo.maximizedLinksWindow, "no links window is M's target until a link opens one")
        assertEquals(listOf(main), repo.visibleWindows)
        assertFalse(chained.isVisible)
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> call(method.name, args ?: emptyArray()) } as T

    private inline fun <reified T> blank(): T {
        val type = Class.forName("sun.misc.Unsafe")
        val field = type.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return type.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }
}
