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

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.KeyChooserResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.navigation.ChooseDictionaryWordController
import net.bible.sharedcore.navigation.ChooseGeneralBookKeyController
import net.bible.sharedcore.navigation.ChooseMapKeyController
import net.bible.sharedcore.navigation.BookPick
import net.bible.sharedcore.navigation.ChapterPick
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedcore.navigation.DictRow
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedcore.navigation.KeyRow
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.navigation.nav.ChooseDictionaryWordDeps
import net.bible.sharedui.navigation.nav.ChooseDocumentDeps
import net.bible.sharedui.navigation.nav.ChooseGeneralBookKeyDeps
import net.bible.sharedui.navigation.nav.ChooseMapKeyDeps
import net.bible.sharedui.navigation.nav.ChooserNavDeps
import net.bible.sharedui.navigation.nav.GridChoosePassageDeps
import net.bible.sharedui.navigation.nav.chooserNavGraph
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
 * Nothing routes to these destinations yet (`ScreenLauncher.MIGRATED` is Task 8's, the reading
 * destination that consumes the channels is Task 9's), so the parent below is a stand-in
 * `composable(NavRoutes.READING)` and the test consumes the channel itself.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ChooserInGraphResultTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var navController: NavHostController

    private lateinit var keyChooserResults: NavResultChannel<KeyChooserResult>
    private lateinit var passageResults: NavResultChannel<PassageResult>
    private lateinit var documentResults: NavResultChannel<DocumentResult>

    /** How often ANY channel took its `exitWithResult` branch. Design §1.1: must stay 0. */
    private var channelExits = 0
    private var exitHostCalls = 0

    // — what the arms handed the host-side factories, captured for the argument assertions —
    private var generalBookController: ChooseGeneralBookKeyController? = null
    private var mapController: ChooseMapKeyController? = null
    private var dictionaryController: ChooseDictionaryWordController? = null
    private var gridIsScripture: Boolean? = null
    private var gridOnResult: ((PassageResult) -> Unit)? = null
    private var documentTypeFilter: DocTypeFilter? = null
    private var documentOnResult: ((DocumentResult) -> Unit)? = null
    private var documentsLoaded = 0

    private fun deps(
        generalBookKeys: List<String> = listOf("Chapter 1", "Chapter 2"),
        mapKeys: List<String> = listOf("Map 1"),
        dictionaryDocument: Boolean = true,
    ): ChooserNavDeps {
        keyChooserResults = NavResultChannel { channelExits++ }
        passageResults = NavResultChannel { channelExits++ }
        documentResults = NavResultChannel { channelExits++ }
        return ChooserNavDeps(
            exitHost = { exitHostCalls++ },
            setWindowTitle = {},
            keyChooserResults = keyChooserResults,
            passageResults = passageResults,
            documentResults = documentResults,
            chooseGeneralBookKey = ChooseGeneralBookKeyDeps(
                title = "General book",
                controllerFor = { onResult ->
                    if (generalBookKeys.isEmpty()) {
                        null
                    } else {
                        ChooseGeneralBookKeyController(
                            loadRows = { generalBookKeys.mapIndexed { i, k -> KeyRow(i.toString(), k) } },
                            currentRow = { "0" },
                            onSelect = { keyId ->
                                onResult(KeyChooserResult(bookAndKeyJson = """{"key":"$keyId"}"""))
                            },
                        ).also { generalBookController = it }
                    }
                },
                emptyResult = { KeyChooserResult(key = "Gen.1", book = "KJV") },
            ),
            chooseMapKey = ChooseMapKeyDeps(
                title = "Maps",
                controllerFor = { onResult ->
                    if (mapKeys.isEmpty()) {
                        null
                    } else {
                        ChooseMapKeyController(
                            loadRows = { mapKeys.mapIndexed { i, k -> KeyRow(i.toString(), k) } },
                            currentRow = { null },
                            onSelect = { keyId -> onResult(KeyChooserResult(key = keyId, book = "Maps")) },
                        ).also { mapController = it }
                    }
                },
                emptyResult = { KeyChooserResult(key = null, book = "Maps") },
            ),
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
                loadRows = { listOf(DictRow("0", "aaron")) },
                loadSnippet = { "" },
            ),
            gridChoosePassage = GridChoosePassageDeps(
                windowTitle = "AndBible",
                controllerFor = { isScripture, onResult ->
                    gridIsScripture = isScripture
                    gridOnResult = onResult
                    gridController(onResult)
                },
            ),
            chooseDocument = ChooseDocumentDeps(
                title = "Choose document",
                controllerFor = { initialTypeFilter, onResult ->
                    documentTypeFilter = initialTypeFilter
                    documentOnResult = onResult
                    DocumentSelectionController(
                        langComparator = { _, _ -> 0 },
                        onSelect = { onResult(DocumentResult(book = it)) },
                        onDelete = {}, onDeleteIndex = {}, onAbout = {}, onUnlock = {},
                        onStickyLanguage = {},
                    )
                },
                initialTypeFilter = { type ->
                    when (type) {
                        "BIBLE" -> DocTypeFilter.BIBLE
                        "COMMENTARY" -> DocTypeFilter.COMMENTARY
                        else -> DocTypeFilter.ALL
                    }
                },
                persistTypeFilter = {},
                loadDocuments = { documentsLoaded++ },
                topBarActions = {},
            ),
        )
    }

    /** A minimal but REAL [GridChoosePassageController]: the arm drives its `ui`/`options` flows and
     *  its `back()`, so a stub would not compose. */
    private fun gridController(onResult: (PassageResult) -> Unit) = GridChoosePassageController(
        initialOptions = GridOptions(
            showScripture = true,
            alphabetical = false,
            ltr = true,
            groupByCategory = false,
            longNames = false,
            showProgress = false,
        ),
        buildStep = { step, opts ->
            GridUi(
                step = step,
                title = "Choose book",
                columns = 1,
                showLongNames = opts.longNames,
                showProgress = opts.showProgress,
                showDeutToggle = false,
                buttons = emptyList(),
            )
        },
        onPersistOptions = {},
        onPickBook = { BookPick.GoChapter },
        onPickChapter = { ChapterPick.GoVerse },
        onPickVerse = { "Gen.1.1" },
        onFinish = { osisId -> onResult(PassageResult(verse = osisId)) },
    )

    private fun setGraph(d: ChooserNavDeps, startDestination: String = NavRoutes.READING) {
        compose.setContent {
            navController = rememberNavController()
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    NavHost(navController = navController, startDestination = startDestination) {
                        // Task 9's destination, as a stand-in: this task's five are registered but
                        // nothing routes to them yet, so the graph has no real parent of its own.
                        composable(NavRoutes.READING) { Text("reading") }
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

    @Test
    fun aGeneralBookKeyChosenInTheGraphReachesTheParentAndPops() {
        setGraph(deps())
        navigateTo(NavRoutes.CHOOSE_GENERAL_BOOK_KEY)
        assertEquals(NavRoutes.CHOOSE_GENERAL_BOOK_KEY, currentRoute)

        val controller = assertNotNull(generalBookController, "the arm never built a controller")
        compose.runOnIdle { controller.select("1") }
        compose.waitForIdle()

        assertEquals(0, channelExits, "deliver() took its EXIT branch; design §1.1 forbids it for this slice")
        assertEquals(0, exitHostCalls)
        assertEquals(NavRoutes.READING, currentRoute, "the chooser must pop back to its parent")
        assertEquals("""{"key":"1"}""", keyChooserResults.consume()?.bookAndKeyJson)
    }

    @Test
    fun theMapChooserDeliversOnTheSameChannelAsTheGeneralBookChooser() {
        setGraph(deps())
        navigateTo(NavRoutes.CHOOSE_MAP_KEY)

        val controller = assertNotNull(mapController, "the arm never built a controller")
        compose.runOnIdle { controller.select("0") }
        compose.waitForIdle()

        assertEquals(0, channelExits)
        assertEquals(NavRoutes.READING, currentRoute)
        val result = assertNotNull(keyChooserResults.consume(), "the map chooser used a different channel")
        assertEquals("0", result.key)
        assertEquals("Maps", result.book)
    }

    @Test
    fun theDictionaryChooserDeliversOnTheSameChannelAsTheGeneralBookChooser() {
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
     * Spec §6.2. Three destinations, ONE channel -- if a later change splits them into five, this
     * fails loudly rather than growing a second mechanism silently.
     */
    @Test
    fun theDepsCarryThreeResultChannelsNotFive() {
        val channelFields = ChooserNavDeps::class.java.declaredFields
            .filter { NavResultChannel::class.java.isAssignableFrom(it.type) }
            .map { it.name }
            .sorted()
        assertEquals(
            listOf("documentResults", "keyChooserResults", "passageResults"),
            channelFields,
            "the three key choosers share ONE channel (spec §6.2); five channels would be five ways " +
                "to say the same thing",
        )
    }

    /**
     * Classic `ChooseGeneralBookKeyComposeActivity.onCreate`'s `keys.isEmpty()` early exit
     * (`:79-83`): it `setResult`s the FALLBACK selection and finishes without ever showing a list.
     * In-graph that is a deliver-and-pop, not a silent pop -- a caller waiting for a key must not be
     * left hanging.
     */
    @Test
    fun anEmptyGeneralBookKeyListDeliversTheFallbackResultAndPops() {
        setGraph(deps(generalBookKeys = emptyList()))
        navigateTo(NavRoutes.CHOOSE_GENERAL_BOOK_KEY)

        assertEquals(0, channelExits)
        assertEquals(NavRoutes.READING, currentRoute, "an empty key list must pop straight back")
        assertEquals("Gen.1", keyChooserResults.consume()?.key)
    }

    /**
     * Classic `ChooseDictionaryWordComposeActivity.onCreate`'s `page.currentDocument == null`
     * branch (`:72`): a bare `finish()` with NO `setResult` at all. The in-graph equivalent is a pop
     * with nothing published -- unlike the general-book chooser above, which has a fallback key to
     * hand back.
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
     * Spec §6.1: `isScripture` is the route's ONLY argument, and the arm must read it off the route
     * rather than from host state. §6.1.1: `navigateToVerse` is NOT an argument -- the destination
     * keeps its own pref fallback, host-side, which is why nothing about it appears here.
     */
    @Test
    fun gridChoosePassageReadsIsScriptureFromTheRouteAndDeliversTheVerse() {
        setGraph(deps())
        navigateTo(NavRoutes.gridChoosePassage(isScripture = true))

        assertEquals(true, gridIsScripture, "the arm did not pass the route's isScripture to the host factory")

        val onResult = assertNotNull(gridOnResult, "the arm never handed the factory an onResult")
        compose.runOnIdle { onResult(PassageResult(verse = "Gen.1.1")) }
        compose.waitForIdle()

        assertEquals(0, channelExits)
        assertEquals(NavRoutes.READING, currentRoute)
        assertEquals("Gen.1.1", passageResults.consume()?.verse)
    }

    @Test
    fun gridChoosePassageDefaultsIsScriptureToFalse() {
        setGraph(deps())
        navigateTo(NavRoutes.gridChoosePassage())
        assertEquals(false, gridIsScripture)
    }

    /** Spec §6.1: `type` is the route's only argument, and it seeds the initial document filter. */
    @Test
    fun chooseDocumentReadsTypeFromTheRouteAndDeliversTheBook() {
        setGraph(deps())
        navigateTo(NavRoutes.chooseDocument(type = "COMMENTARY"))

        assertEquals(DocTypeFilter.COMMENTARY, documentTypeFilter)
        assertEquals(1, documentsLoaded, "classic loaded the document list once per entry")

        val onResult = assertNotNull(documentOnResult, "the arm never handed the factory an onResult")
        compose.runOnIdle { onResult(DocumentResult(book = "KJV")) }
        compose.waitForIdle()

        assertEquals(0, channelExits)
        assertEquals(NavRoutes.READING, currentRoute)
        assertEquals("KJV", documentResults.consume()?.book)
    }

    @Test
    fun chooseDocumentWithNoTypeFallsBackToTheStoredFilter() {
        setGraph(deps())
        navigateTo(NavRoutes.chooseDocument())
        assertEquals(DocTypeFilter.ALL, documentTypeFilter)
    }
}
