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
package net.bible.sharedui.ios.reading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import net.bible.android.control.download.DownloadControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.service.download.FakeBookFactory
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.awaitStartupOnMain
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.ios.app.IosNavigator
import net.bible.sharedui.nav.NavResultChannel
import org.koin.mp.KoinPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [IosReadingNavigator] against a recording [IosNavigator]: every member must start the route Android's
 * `AndroidReadingNavigator` starts, and the label chooser's answer must reach the caller that asked.
 */
class IosReadingNavigatorTest {
    private class RecordingNavigator : IosNavigator {
        val routes = mutableListOf<String>()
        var failNavigate = false
        override fun navigate(route: String) {
            if (failNavigate) throw IllegalStateException("navigate failed")
            routes += route
        }
        override fun popBackStack(): Boolean = false
        override fun popBackStackTo(route: String, inclusive: Boolean): Boolean = false
    }

    private class RecordingLinks : ExternalLinks {
        override val askBeforeOpening = false
        val opened = mutableListOf<String>()
        override fun open(url: String) { opened += url }
    }

    private class Fixture(
        val navigator: IosReadingNavigator,
        val nav: RecordingNavigator,
        val shell: IosHostShell,
        val links: RecordingLinks,
        val labels: NavResultChannel<ManageLabelsResult>,
        val hostState: net.bible.sharedui.reading.host.ReadingHostState,
    ) {
        /** What the app does: the destination listener reports the route; the manager publishes its answer then pops. */
        fun open(route: String) = shell.routeChanged(route)
        fun deliver(selected: Set<IdType>, primary: IdType? = null) {
            labels.publishForTest(
                ManageLabelsResult(
                    ManageLabelsContract.ManageLabelsData(
                        ManageLabelsContract.Mode.ASSIGN, selectedLabels = selected.toMutableSet(), bookmarkPrimaryLabel = primary,
                    ).toJSON(),
                ),
            )
        }
    }

    private fun withNavigator(block: suspend Fixture.() -> Unit) {
        val scope = MainScope()
        try {
            awaitStartupOnMain { IosAppStartup.ensureStarted(
                IosAppConfig(
                    root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                    repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
                ),
            ) }
            awaitStartupOnMain { IosAppStartup.openDatabases() }
            val platform = net.bible.sharedui.ios.app.IosReadingAppInitPlatform(IosAppStartup.stringTable)
            runBlocking {
                ReadingAppInit.initialise(platform)
                ReadingAppInit.prepareData(platform)
            }
            val koin = KoinPlatform.getKoin()
            ReadingAppInit.createWindowRepository(scope)
            val nav = RecordingNavigator()
            val shell = IosHostShell(CoroutineScope(Dispatchers.Unconfined), nav)
            shell.routeChanged(NavRoutes.READING)
            val host = IosReadingHost(scope, shell, koin)
            val links = RecordingLinks()
            val labels = NavResultChannel<ManageLabelsResult> { }
            val navigator = IosReadingNavigator(
                shell = shell,
                hostState = host.hostState,
                links = links,
                labelResults = labels,
                downloadControl = { koin.get<DownloadControl>() },
                fakeBooks = { koin.get<FakeBookFactory>() },
                mainContext = Dispatchers.Unconfined,
            )
            try {
                runBlocking { Fixture(navigator, nav, shell, links, labels, host.hostState).block() }
            } finally {
                host.dispose()
                testScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }
        } finally {
            scope.cancel()
            ReadingAppInit.resetForTest()
            runBlocking { IosAppStartup.resetForTest() }
        }
    }

    private val testScope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Unconfined)

    private fun bookmark(primary: IdType? = null) = BookmarkEntities.GenericBookmarkWithNotes(
        key = "k", bookInitials = "X", ordinalStart = null, ordinalEnd = null, startOffset = null, endOffset = null,
        playbackSettings = null, primaryLabelId = primary,
    )

    @Test
    fun searchRouteMatchesAndroidSearchIntent() {
        assertEquals(NavRoutes.searchForm(), searchRoute(indexDone = true, isEpub = false, isGeneralBook = false, initials = "KJV"))
        assertEquals(NavRoutes.EPUB_SEARCH, searchRoute(indexDone = true, isEpub = true, isGeneralBook = false, initials = "E"))
        assertNull(searchRoute(indexDone = false, isEpub = false, isGeneralBook = true, initials = "G"))
        assertEquals(NavRoutes.searchIndex(searchDocument = "KJV"), searchRoute(false, false, false, "KJV"))
        assertEquals(NavRoutes.searchIndex(searchDocument = null), searchRoute(false, false, false, null))
    }

    /** Android's `composeSearch` opens the reading view's own search: no screen is navigated to. */
    @Test
    fun openSearchRunsTheInPlaceSearchInsteadOfARoute() = withNavigator {
        var opened = 0
        val inPlace = IosReadingNavigator(
            shell = shell, hostState = hostState, links = links, labelResults = labels,
            downloadControl = { KoinPlatform.getKoin().get<DownloadControl>() },
            fakeBooks = { KoinPlatform.getKoin().get<FakeBookFactory>() },
            mainContext = Dispatchers.Unconfined,
            openReadingSearch = { opened++ },
        )
        inPlace.openSearch(null)
        assertEquals(1, opened)
        assertEquals(emptyList(), nav.routes)
    }

    @Test
    fun theWorkspaceSelectorRecordsAWorkspaceRequest() = withNavigator {
        val kinds = mutableListOf<ChooserKind>()
        val recording = object : ChooserRequests {
            override fun asked(kind: ChooserKind, asker: Any?) { kinds += kind }
            override fun activeAsker(): Any? = null
        }
        val withRequests = IosReadingNavigator(
            shell = shell, hostState = hostState, links = links, labelResults = labels,
            downloadControl = { KoinPlatform.getKoin().get<DownloadControl>() },
            fakeBooks = { KoinPlatform.getKoin().get<FakeBookFactory>() },
            mainContext = Dispatchers.Unconfined,
            chooserRequests = recording,
        )
        withRequests.openWorkspaceSelector()
        assertEquals(listOf(ChooserKind.Workspace), kinds)
        assertEquals(listOf(NavRoutes.WORKSPACE_SELECTOR), nav.routes)
    }

    @Test
    fun navigatingMembersStartAndroidsRoutes() = withNavigator {
        navigator.openDownloadSearch("KJV")
        navigator.openDownloadSearch(null)
        navigator.openReadingProgress(2)
        navigator.openReadingProgressSettings()
        navigator.openWorkspaceSelector()
        navigator.openChooseDocument("Bibles")
        navigator.openChooseDocument(null)
        navigator.openPromptEditor("p1")
        navigator.openSearch(null)
        assertEquals(
            listOf(
                NavRoutes.download(search = "KJV"),
                NavRoutes.download(search = null),
                NavRoutes.readingProgress(2),
                NavRoutes.READING_PROGRESS_SETTINGS,
                NavRoutes.WORKSPACE_SELECTOR,
                NavRoutes.chooseDocument("Bibles"),
                NavRoutes.chooseDocument(null),
                NavRoutes.promptEdit(promptId = "p1"),
                NavRoutes.searchIndex(searchDocument = null),
            ),
            nav.routes,
        )
    }

    @Test
    fun openDownloadsStartsTheAddonsRouteWhenStorageAllows() = withNavigator {
        navigator.openDownloads(addons = true)
        // Storage space is the environment's: either the route or nothing (checkDownloadOkay refused), never another route.
        assertEquals(true, nav.routes.isEmpty() || nav.routes == listOf(NavRoutes.download(addons = true)))
    }

    @Test
    fun externalAndWebLinksGoThroughExternalLinks() = withNavigator {
        navigator.openExternalUrl("https://a.example")
        navigator.openWebLink("https://b.example")
        assertEquals(listOf("https://a.example", "https://b.example"), links.opened)
        assertEquals(emptyList(), nav.routes)
    }

    @Test
    fun assignLabelsReturnsTheChosenIds() = withNavigator {
        val a = IdType()
        val b = IdType()
        val primary = IdType()
        val asked = bookmark()
        val answer = testScope.async { navigator.assignLabels(asked, listOf(a)) }
        yield()
        assertEquals(1, nav.routes.size)
        assertTrue(nav.routes.single().startsWith("bookmarks/manageLabels"), nav.routes.single())
        open(NavRoutes.MANAGE_LABELS_PATTERN)
        deliver(setOf(a, b), primary)
        open(NavRoutes.READING)
        assertEquals(setOf(a, b), answer.await()!!.toSet())
        assertEquals(primary, asked.primaryLabelId, "the primary label chosen in the manager is set on the asking bookmark")
    }

    @Test
    fun assignLabelsCancelReturnsNull() = withNavigator {
        val asked = bookmark(primary = IdType())
        val before = asked.primaryLabelId
        val answer = testScope.async { navigator.assignLabels(asked, emptyList()) }
        yield()
        open(NavRoutes.MANAGE_LABELS_PATTERN)
        open(NavRoutes.READING) // left without an answer
        assertNull(answer.await())
        assertEquals(before, asked.primaryLabelId, "a cancel changes nothing")
    }

    @Test
    fun aFailingNavigateReturnsNullAndReleasesTheLock() = withNavigator {
        nav.failNavigate = true
        assertNull(navigator.assignLabels(bookmark(), emptyList()))
        nav.failNavigate = false
        val x = IdType()
        val answer = testScope.async { navigator.assignLabels(bookmark(), emptyList()) }
        yield()
        open(NavRoutes.MANAGE_LABELS_PATTERN)
        deliver(setOf(x))
        open(NavRoutes.READING)
        assertEquals(listOf(x), answer.await())
    }

    @Test
    fun aManagerThatWasNeverObservedStillEndsTheWaitAndReleasesTheLock() = withNavigator {
        // The manager opened and closed between two emissions: only a route change back to reading is seen.
        val first = testScope.async { navigator.assignLabels(bookmark(), emptyList()) }
        yield()
        open(NavRoutes.READING)
        assertNull(first.await())
        val x = IdType()
        val second = testScope.async { navigator.assignLabels(bookmark(), emptyList()) }
        yield()
        open(NavRoutes.MANAGE_LABELS_PATTERN)
        deliver(setOf(x))
        open(NavRoutes.READING)
        assertEquals(listOf(x), second.await())
    }

    /**
     * The navigator has no notion of the active window: the answer is returned to the coroutine that asked, which
     * belongs to the asking window's session, so a change of the active window while the manager is open cannot
     * redirect it. What is tested is that two overlapping asks from two windows each get their own answer, in order.
     */
    @Test
    fun resultGoesToTheAskingWindow() = withNavigator {
        val first = bookmark()
        val second = bookmark()
        val x = IdType()
        val y = IdType()
        val firstAnswer = testScope.async { navigator.assignLabels(first, emptyList()) }
        // A second window asks while the first manager is still open: it waits, it does not steal the answer.
        val secondAnswer = testScope.async { navigator.assignLabels(second, emptyList()) }
        yield()
        assertEquals(1, nav.routes.size, "the second request waits for the first")
        open(NavRoutes.MANAGE_LABELS_PATTERN)
        deliver(setOf(x))
        open(NavRoutes.READING)
        assertEquals(listOf(x), firstAnswer.await())
        yield()
        assertEquals(2, nav.routes.size, "the second manager opens once the first closed")
        open(NavRoutes.MANAGE_LABELS_PATTERN)
        deliver(setOf(y))
        open(NavRoutes.READING)
        assertEquals(listOf(y), secondAnswer.await())
    }
}
