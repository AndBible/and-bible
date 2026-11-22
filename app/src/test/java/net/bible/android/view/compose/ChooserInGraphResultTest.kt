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
package net.bible.android.view.compose

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.KeyChooserResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.navigation.ChooseDictionaryWordController
import net.bible.sharedcore.navigation.ChooserError
import net.bible.sharedcore.navigation.DictRow
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.navigation.nav.ChooseDictionaryWordDeps
import net.bible.sharedui.navigation.nav.ChooseDocumentDeps
import net.bible.sharedui.navigation.nav.ChooserNavDeps
import net.bible.sharedui.navigation.nav.chooserNavGraph
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The composed test of nav-graph slice 7 Task 4: the five chooser destinations deliver their results
 * IN-GRAPH, through a real [NavResultChannel] over a real back stack -- the twin of
 * [CustomRepositoryEditorInGraphResultTest] and [MyDocumentsInGraphResultTest], and it exists for
 * the same reason they do. `NavHostRoutingGuardTest` and `NavResultChannelGuardTest` prove the graph
 * source CONTAINS the right calls by scanning its text; neither can tell you that
 * [NavResultChannel.deliver] takes its `previousBackStackEntry != null` branch once a real back stack
 * is involved, that the destination pops afterwards, or that the arm read the route argument it was
 * handed.
 *
 * Design §1.1 is what is pinned here: for this slice's destinations the host-side `exitWithResult`
 * shim is NOT an allowed exit -- every one of them is entered only from inside the graph. So every
 * test below asserts `channelExits` stayed at zero as well as asserting the payload: a `deliver` that
 * silently took the exit branch is the defect this whole slice's result plumbing exists to prevent,
 * and it would otherwise show up only as a missing side effect.
 *
 * **The fakes below reproduce classic's SHAPES, never its literals**, which is the fix round 1
 * correction to this file. Every controller, result and fallback here is built by [deps], so none of
 * the 625 ported host lines runs in this test -- what CAN still be pinned is the arm's side of each
 * seam, and that only holds if the fake would answer differently when the arm asks differently. A
 * fake `initialTypeFilter` whose `else` is the literal `DocTypeFilter.ALL`, for instance, cannot tell
 * "the arm resolved the route's type through the deps slot" from "the arm ignored the slot and
 * passed ALL", so its test could not fail; it now answers [storedFilter], which no arm can guess.
 * Each test's kdoc names the mutation it catches.
 *
 * Nothing routes to these destinations yet (`ScreenLauncher.MIGRATED` is Task 8's, the reading
 * destination that consumes the channels is Task 9's), so the parent below is a stand-in
 * `composable(NavRoutes.READING)` and the test consumes the channel itself.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ChooserInGraphResultTest {
    /** An ANDROID rule, not `createComposeRule()`: the two `PlatformBackHandler` tests below need a
     *  real `onBackPressedDispatcher` to press, which only the activity-backed rule exposes. */
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    private lateinit var keyChooserResults: NavResultChannel<KeyChooserResult>
    private lateinit var documentResults: NavResultChannel<DocumentResult>

    /** How often ANY channel took its `exitWithResult` branch. Design §1.1: must stay 0. */
    private var channelExits = 0
    private var exitHostCalls = 0

    /** Every HOST WINDOW title the graph set, in order. */
    private val windowTitles = mutableListOf<String>()

    // — what the arms handed the host-side factories, captured for the argument assertions —
    private var dictionaryController: ChooseDictionaryWordController? = null
    private var documentController: DocumentSelectionController? = null
    private var documentTypeFilter: DocTypeFilter? = null
    private var documentsLoaded = 0

    /**
     * What the host's `chooseDocumentInitialTypeFilter` reads out of `selected_document_filter_no`
     * when the route carries no type. A fixture VALUE rather than a literal in the fake, so that
     * "the arm resolved the filter through the deps slot" is distinguishable from "the arm assumed
     * ALL" -- deliberately not ALL for exactly that reason.
     */
    private var storedFilter = DocTypeFilter.DICTIONARY

    private fun deps(
        dictionaryDocument: Boolean = true,
        dictionaryRows: List<DictRow>? = listOf(DictRow("0", "aaron")),
    ): ChooserNavDeps {
        keyChooserResults = NavResultChannel { channelExits++ }
        documentResults = NavResultChannel { channelExits++ }
        return ChooserNavDeps(
            exitHost = { exitHostCalls++ },
            setWindowTitle = { windowTitles += it },
            keyChooserResults = keyChooserResults,
            documentResults = documentResults,
            chooseDictionaryWord = ChooseDictionaryWordDeps(
                title = "Dictionary",
                hint = "Search",
                controllerFor = { onResult ->
                    if (!dictionaryDocument) {
                        null
                    } else {
                        ChooseDictionaryWordController(
                            onSelect = { keyId -> onResult(KeyChooserResult(key = keyId, book = "Strongs")) },
                        ).also { dictionaryController = it }
                    }
                },
                // Null is the host's already-logged FAILURE, which is the whole reason this slot's
                // return type is nullable (`ChooseDictionaryWordDeps.loadRows`).
                loadRows = { dictionaryRows },
                loadSnippet = { "" },
            ),
            chooseDocument = ChooseDocumentDeps(
                title = "Choose document",
                controllerFor = { initialTypeFilter, onResult ->
                    documentTypeFilter = initialTypeFilter
                    DocumentSelectionController(
                        langComparator = { _, _ -> 0 },
                        onSelect = { onResult(DocumentResult(book = it)) },
                        onDelete = {}, onAbout = {}, onUnlock = {},
                        onStickyLanguage = {},
                    ).also { documentController = it }
                },
                // The host's `chooseDocumentInitialTypeFilter` shape: the route's type when there is
                // one, ELSE the stored `selected_document_filter_no`. [storedFilter] stands in for
                // that settings read; a literal here would make the no-type test unfailable.
                initialTypeFilter = { type ->
                    when (type) {
                        "BIBLE" -> DocTypeFilter.BIBLE
                        "COMMENTARY" -> DocTypeFilter.COMMENTARY
                        else -> storedFilter
                    }
                },
                persistTypeFilter = {},
                loadDocuments = { documentsLoaded++ },
                topBarActions = {},
            ),
        )
    }

    private fun setGraph(d: ChooserNavDeps, startDestination: String = NavRoutes.READING) {
        compose.setContent {
            navController = rememberNavController()
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    NavHost(navController = navController, startDestination = startDestination) {
                        // Task 9's destination, as a stand-in: this task's five are registered but
                        // nothing routes to them yet, so the graph has no real parent of its own.
                        composable(NavRoutes.READING) { Text("reading") }
                        // A stand-in for a destination the chooser hops to in-graph (its Download row).
                        composable(STAND_IN_ROUTE) { Text("stand-in") }
                        chooserNavGraph(navController, d)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private val currentRoute: String?
        get() = navController.currentBackStackEntry?.destination?.route

    private fun navigateTo(route: String) {
        compose.runOnIdle { navController.navigate(route) }
        compose.waitForIdle()
    }

    /** A real system back press, so the arm's `PlatformBackHandler` is what decides. */
    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun aDictionaryWordChosenInTheGraphReachesTheParentAndPops() {
        setGraph(deps())
        navigateTo(NavRoutes.CHOOSE_DICTIONARY_WORD)

        val controller = assertNotNull(dictionaryController, "the arm never built a controller")
        compose.runOnIdle { controller.select("0") }
        compose.waitForIdle()

        assertEquals(0, channelExits)
        assertEquals(NavRoutes.READING, currentRoute)
        val result = assertNotNull(keyChooserResults.consume(), "the dictionary chooser used a different channel")
        assertEquals("0", result.key)
    }

    /**
     * Spec §6.2: one key channel (the dictionary's; the general-book and map key choosers it once shared
     * were removed by #4172) and one document channel.
     */
    @Test
    fun theDepsCarryTwoResultChannelsNotFour() {
        val channelFields = ChooserNavDeps::class.java.declaredFields
            .filter { NavResultChannel::class.java.isAssignableFrom(it.type) }
            .map { it.name }
            .sorted()
        assertEquals(
            listOf("documentResults", "keyChooserResults"),
            channelFields,
            "one key channel and one document channel (spec §6.2)",
        )
    }

    /**
     * Classic `ChooseDictionaryWordComposeActivity.onCreate`'s `page.currentDocument == null`
     * branch (`:72`): a bare `finish()` with NO `setResult` at all. The in-graph equivalent is a pop
     * with nothing published.
     */
    @Test
    fun aDictionaryWithNoDocumentPopsWithoutDeliveringAResult() {
        setGraph(deps(dictionaryDocument = false))
        navigateTo(NavRoutes.CHOOSE_DICTIONARY_WORD)

        assertEquals(0, channelExits)
        assertEquals(NavRoutes.READING, currentRoute)
        assertNull(keyChooserResults.consume(), "classic finished with no result here; nothing may be published")
    }

    /**
     * [ChooseDictionaryWordDeps.loadRows]'s null is "the load FAILED, the host has already logged
     * it", and the arm's job on that branch is `controller.showError()` -- classic's own `catch`
     * reaction. That is the entire reason the slot's return type is nullable, and nothing exercised
     * it until fix round 1.
     *
     * Catches: collapsing the branch to `controller.setAllRows(rows ?: emptyList())`, which shows an
     * empty dictionary and no error at all, and any arm that treats a failed load as a reason to pop.
     */
    @Test
    fun aDictionaryKeyListThatFailsToLoadShowsTheErrorAndStays() {
        setGraph(deps(dictionaryRows = null))
        navigateTo(NavRoutes.CHOOSE_DICTIONARY_WORD)

        val controller = assertNotNull(dictionaryController, "the arm never built a controller")
        assertEquals(ChooserError.FAILED, controller.error.value, "a null row list is classic's error path")
        // CR-3: a failed load ends the spinner, or it spins under (and after) the error dialog forever.
        // The error field is what distinguishes the failure from a finished, empty list.
        assertFalse(controller.loading.value, "a failed load has nothing left to load; the spinner must end")
        assertEquals(NavRoutes.CHOOSE_DICTIONARY_WORD, currentRoute, "a failed load stays put; classic did")
        assertEquals(0, channelExits)
        assertNull(keyChooserResults.consume(), "a failed load publishes nothing")
    }

    /**
     * Spec §6.1: `type` is the route's only argument, and it seeds the initial document filter.
     *
     * Catches: an arm that passes a filter of its own instead of `d.initialTypeFilter(type)` (the
     * fake's stored fallback is [storedFilter], not ALL, so a hardcoded ALL fails); a `loadDocuments`
     * effect keyed so that it runs more or less than once per entry; and a controller handed to the
     * screen that is not the one wired to the channel -- the selection is now made THROUGH the
     * controller rather than by calling the arm's `onResult` by hand.
     */
    /**
     * Slice 8 final review, finding 2: the premise of the host's in-graph Download hop. The host arms
     * its follow-up (a `DocumentChanges.installedChanged` emission + reload) when the Download row navigates, and
     * performs it from `loadDocuments` -- so `loadDocuments` must run again when the chooser entry is
     * RETURNED to, not only on its first entry. A one-shot load (e.g. guarded by saved state) would
     * silently drop the follow-up.
     */
    @Test
    fun chooseDocumentReloadsItsDocumentsOnReturnFromAnInGraphHop() {
        setGraph(deps())
        navigateTo(NavRoutes.chooseDocument())
        assertEquals(1, documentsLoaded)

        navigateTo(STAND_IN_ROUTE)
        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()

        assertEquals(NavRoutes.CHOOSE_DOCUMENT_PATTERN, currentRoute)
        assertEquals(2, documentsLoaded, "a return to the chooser entry must run loadDocuments again")
    }

    @Test
    fun chooseDocumentReadsTypeFromTheRouteAndDeliversTheBook() {
        setGraph(deps())
        navigateTo(NavRoutes.chooseDocument(type = "COMMENTARY"))

        assertEquals(DocTypeFilter.COMMENTARY, documentTypeFilter)
        assertEquals(1, documentsLoaded, "classic loaded the document list once per entry")

        val controller = assertNotNull(documentController, "the arm never built a controller")
        compose.runOnIdle { controller.select("KJV") }
        compose.waitForIdle()

        assertEquals(0, channelExits)
        assertEquals(NavRoutes.READING, currentRoute)
        assertEquals("KJV", documentResults.consume()?.book)
    }

    /**
     * Catches: an arm that ignores the deps' `initialTypeFilter` slot on the no-type route and
     * assumes `DocTypeFilter.ALL`. Before fix round 1 the fake's `else` WAS that literal, so deleting
     * the host's `selected_document_filter_no` read changed nothing this test could see.
     */
    @Test
    fun chooseDocumentWithNoTypeFallsBackToTheStoredFilter() {
        storedFilter = DocTypeFilter.MAPS
        setGraph(deps())
        navigateTo(NavRoutes.chooseDocument())
        assertEquals(DocTypeFilter.MAPS, documentTypeFilter)
    }

    /**
     * Classic's `onBackPressed` override (`:223-231`): back dismisses what is visually on top, so the
     * SELECTION bar goes before the search bar, and only a screen with neither open lets back pop the
     * destination. Untested until fix round 1 -- neither `PlatformBackHandler` in this graph had any
     * coverage at all.
     *
     * Catches: the two branches swapped (with both bars open, back would close the search bar and
     * leave the selection bar on top of a screen that no longer has one); the handler's `enabled`
     * flag reduced to one of the two states, which strands the other bar open under a back press
     * that pops the whole destination instead.
     */
    @Test
    fun chooseDocumentBackDismissesTheSelectionBarBeforeTheSearchBar() {
        setGraph(deps())
        navigateTo(NavRoutes.chooseDocument())
        val controller = assertNotNull(documentController, "the arm never built a controller")

        compose.runOnIdle {
            controller.openSearch()
            controller.enterSelection()
            controller.toggle("KJV")
        }
        compose.waitForIdle()
        assertTrue(controller.selectionMode.value)
        assertTrue(controller.searchModeActive.value)

        pressBack()
        assertFalse(controller.selectionMode.value, "back must dismiss the SELECTION bar first")
        assertTrue(controller.searchModeActive.value, "the search bar is underneath it, not with it")
        assertEquals(NavRoutes.CHOOSE_DOCUMENT_PATTERN, currentRoute, "neither bar's dismissal may pop")

        pressBack()
        assertFalse(controller.searchModeActive.value, "the second press closes the search bar")
        assertEquals(NavRoutes.CHOOSE_DOCUMENT_PATTERN, currentRoute)

        pressBack()
        assertEquals(NavRoutes.READING, currentRoute, "with both bars gone the handler is off and back pops")
        assertEquals(0, exitHostCalls, "there was a parent to pop to")
    }

    private companion object {
        const val STAND_IN_ROUTE = "test/standIn"
    }
}
