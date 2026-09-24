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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import net.bible.sharedcore.navigation.ChooserError
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.GridOptions
import net.bible.sharedcore.navigation.GridStep
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
    private lateinit var passageResults: NavResultChannel<PassageResult>
    private lateinit var documentResults: NavResultChannel<DocumentResult>

    /** How often ANY channel took its `exitWithResult` branch. Design §1.1: must stay 0. */
    private var channelExits = 0
    private var exitHostCalls = 0

    /** Every HOST WINDOW title the graph set, in order. The empty-key-list paths must record
     *  nothing -- see [anEmptyGeneralBookKeyListLeavesNoTitleOnTheWindow]. */
    private val windowTitles = mutableListOf<String>()

    // — what the arms handed the host-side factories, captured for the argument assertions —
    private var generalBookController: ChooseGeneralBookKeyController? = null
    private var mapController: ChooseMapKeyController? = null
    private var dictionaryController: ChooseDictionaryWordController? = null
    private var gridController: GridChoosePassageController? = null
    private var gridIsScripture: Boolean? = null
    private var documentController: DocumentSelectionController? = null
    private var documentTypeFilter: DocTypeFilter? = null
    private var documentsLoaded = 0
    private var generalBookEmptyResults = 0
    private var mapEmptyResults = 0

    /**
     * What the host's `chooseDocumentInitialTypeFilter` reads out of `selected_document_filter_no`
     * when the route carries no type. A fixture VALUE rather than a literal in the fake, so that
     * "the arm resolved the filter through the deps slot" is distinguishable from "the arm assumed
     * ALL" -- deliberately not ALL for exactly that reason.
     */
    private var storedFilter = DocTypeFilter.DICTIONARY

    /**
     * Classic's `doc!!.globalKeyList`, which is where `ChooseGeneralBookKeyComposeActivity`'s
     * `buildResult(null)` fallback takes its `osisRef` from -- a DIFFERENT list from the chooser's
     * own `keyChooserKeys()`, which is why the empty-list test can tell the fallback apart from a
     * key the chooser could have offered.
     */
    private val generalBookGlobalKeys = listOf("Job.1", "Job.2")
    private val generalBookDocument = "KJV"
    private val mapDocument = "Maps"

    private fun deps(
        generalBookKeys: List<String> = listOf("Chapter 1", "Chapter 2"),
        mapKeys: List<String> = listOf("Map 1"),
        dictionaryDocument: Boolean = true,
        dictionaryRows: List<DictRow>? = listOf(DictRow("0", "aaron")),
    ): ChooserNavDeps {
        keyChooserResults = NavResultChannel { channelExits++ }
        passageResults = NavResultChannel { channelExits++ }
        documentResults = NavResultChannel { channelExits++ }
        return ChooserNavDeps(
            exitHost = { exitHostCalls++ },
            setWindowTitle = { windowTitles += it },
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
                // Classic's `buildResult(null)`: the fallback comes off the DOCUMENT's global key
                // list, not off the (empty) chooser list -- derived here rather than written as a
                // literal so the test asserts the shape and not a constant it supplied itself.
                emptyResult = {
                    generalBookEmptyResults++
                    KeyChooserResult(key = generalBookGlobalKeys.first(), book = generalBookDocument)
                },
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
                // Classic `ChooseMapKeyComposeActivity.buildResult(null)`: `key?.osisRef` is null and
                // the book is the page's document. A null KEY inside a delivered result is NOT the
                // same thing as nothing delivered -- see [anEmptyMapKeyListDeliversTheFallbackResult].
                emptyResult = {
                    mapEmptyResults++
                    KeyChooserResult(key = null, book = mapDocument)
                },
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
                // Null is the host's already-logged FAILURE, which is the whole reason this slot's
                // return type is nullable (`ChooseDictionaryWordDeps.loadRows`).
                loadRows = { dictionaryRows },
                loadSnippet = { "" },
            ),
            gridChoosePassage = GridChoosePassageDeps(
                windowTitle = "AndBible",
                controllerFor = { isScripture, onResult ->
                    gridIsScripture = isScripture
                    gridPassageController(onResult).also { gridController = it }
                },
            ),
            chooseDocument = ChooseDocumentDeps(
                title = "Choose document",
                controllerFor = { initialTypeFilter, onResult ->
                    documentTypeFilter = initialTypeFilter
                    DocumentSelectionController(
                        langComparator = { _, _ -> 0 },
                        onSelect = { onResult(DocumentResult(book = it)) },
                        onDelete = {}, onDeleteIndex = {}, onAbout = {}, onUnlock = {},
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

    /**
     * A minimal but REAL [GridChoosePassageController]: the arm drives its `ui`/`options` flows, its
     * `pick()` and its `back()`, so a stub would not compose. Each step offers ONE button with a step-
     * specific label, which is what lets [gridChoosePassageReadsIsScriptureFromTheRouteAndDeliversTheVerse]
     * walk Book -> Chapter -> Verse by clicking the screen rather than by calling the arm's own
     * `onResult` back to it.
     */
    private fun gridPassageController(onResult: (PassageResult) -> Unit) = GridChoosePassageController(
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
                title = "Step ${step.name}",
                columns = 1,
                showLongNames = opts.longNames,
                showProgress = opts.showProgress,
                showDeutToggle = false,
                buttons = listOf(GridButton(id = 0, label = gridCellLabel(step))),
            )
        },
        onPersistOptions = {},
        onPickBook = { BookPick.GoChapter },
        onPickChapter = { ChapterPick.GoVerse },
        onPickVerse = { "Gen.1.1" },
        onFinish = { osisId -> onResult(PassageResult(verse = osisId)) },
    )

    private fun gridCellLabel(step: GridStep) = when (step) {
        GridStep.BOOK -> "BookCell"
        GridStep.CHAPTER -> "ChapterCell"
        GridStep.VERSE -> "VerseCell"
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

    private fun clickCell(label: String) {
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }

    /**
     * Catches: an arm that hands the screen a controller other than the one it built with the
     * channel-delivering `onResult`, and any regression in deliver-then-pop. Also pins that the
     * window title is set EXACTLY once, on the value the deps carried.
     */
    @Test
    fun aGeneralBookKeyChosenInTheGraphReachesTheParentAndPops() {
        setGraph(deps())
        navigateTo(NavRoutes.CHOOSE_GENERAL_BOOK_KEY)
        assertEquals(NavRoutes.CHOOSE_GENERAL_BOOK_KEY, currentRoute)
        assertEquals(listOf("General book"), windowTitles, "the destination's own android:label")

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

        assertEquals(listOf("Maps"), windowTitles, "the destination's own android:label")

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
     *
     * Catches: an arm that publishes a result of its own making instead of asking the deps for
     * `emptyResult()` (the delivered key is the DOCUMENT's first global key, which no arm can
     * synthesise), and an arm whose empty-list effect runs more than once.
     */
    @Test
    fun anEmptyGeneralBookKeyListDeliversTheFallbackResultAndPops() {
        setGraph(deps(generalBookKeys = emptyList()))
        navigateTo(NavRoutes.CHOOSE_GENERAL_BOOK_KEY)

        assertEquals(0, channelExits)
        assertEquals(NavRoutes.READING, currentRoute, "an empty key list must pop straight back")
        assertNull(generalBookController, "the empty arm must not build a controller at all")
        assertEquals(1, generalBookEmptyResults, "the fallback must be asked for exactly once")
        val result = assertNotNull(keyChooserResults.consume(), "nothing was published")
        assertEquals(generalBookGlobalKeys.first(), result.key)
        assertEquals(generalBookDocument, result.book)
    }

    /**
     * The [ChooserNavDeps.setWindowTitle] kdoc argues that the empty path must NOT leave its title on
     * the host window: the destination pops in the frame it composed, and the parent's own
     * `LaunchedEffect(title)` will not re-run to put the parent's title back. Nothing tested that
     * until fix round 1.
     *
     * Catches: hoisting `LaunchedEffect(d.title) { setWindowTitle(d.title) }` above the
     * `if (controller == null)` early return -- a change that compiles, delivers the same result,
     * pops the same way, and leaves "General book" on the window of the screen behind.
     */
    @Test
    fun anEmptyGeneralBookKeyListLeavesNoTitleOnTheWindow() {
        setGraph(deps(generalBookKeys = emptyList()))
        navigateTo(NavRoutes.CHOOSE_GENERAL_BOOK_KEY)

        assertEquals(NavRoutes.READING, currentRoute)
        assertEquals(
            emptyList(),
            windowTitles,
            "a destination that pops in the frame it composed must leave no title behind it",
        )
    }

    /**
     * `ChooseMapKeyComposeActivity:70-74`, the map chooser's own empty-list exit. Its result is the
     * one shape that can be mistaken for silence: `buildResult(null)` yields a KEY-LESS result, which
     * is still a delivery.
     *
     * Catches: the map arm's null branch being written as the DICTIONARY arm's (a plain
     * `popOrExit` with nothing published) -- until fix round 1 the `mapKeys` fixture knob was never
     * passed a value by any test, so that whole branch was unexercised and the swap was free.
     */
    @Test
    fun anEmptyMapKeyListDeliversTheFallbackResult() {
        setGraph(deps(mapKeys = emptyList()))
        navigateTo(NavRoutes.CHOOSE_MAP_KEY)

        assertEquals(0, channelExits)
        assertEquals(NavRoutes.READING, currentRoute)
        assertNull(mapController, "the empty arm must not build a controller at all")
        assertEquals(1, mapEmptyResults)
        val result = assertNotNull(
            keyChooserResults.consume(),
            "a key-less fallback is still a DELIVERY; the map chooser must not pop in silence",
        )
        assertNull(result.key)
        assertEquals(mapDocument, result.book)
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
        assertTrue(controller.loading.value, "a failed load must not look like a finished, empty one")
        assertEquals(NavRoutes.CHOOSE_DICTIONARY_WORD, currentRoute, "a failed load stays put; classic did")
        assertEquals(0, channelExits)
        assertNull(keyChooserResults.consume(), "a failed load publishes nothing")
    }

    /**
     * Spec §6.1: `isScripture` is the route's ONLY argument, and the arm must read it off the route
     * rather than from host state. §6.1.1: `navigateToVerse` is NOT an argument -- the destination
     * keeps its own pref fallback, host-side, which is why nothing about it appears here.
     *
     * The verse is reached by CLICKING through the three grid steps, not by calling the arm's own
     * `onResult` back to it: until fix round 1 this test invoked the captured `onResult` by hand,
     * which re-tested `NavResultChannel` (which has its own test) and nothing else.
     *
     * Catches: `onPick` wired to anything but `controller::pick`; the arm handing the screen a
     * controller other than the one it gave the channel-delivering `onResult` to; and a step machine
     * that never reaches `onFinish`.
     */
    @Test
    fun gridChoosePassageReadsIsScriptureFromTheRouteAndDeliversTheVerse() {
        setGraph(deps())
        navigateTo(NavRoutes.gridChoosePassage(isScripture = true))

        assertEquals(true, gridIsScripture, "the arm did not pass the route's isScripture to the host factory")
        val controller = assertNotNull(gridController, "the arm never built a controller")

        clickCell("BookCell")
        assertEquals(GridStep.CHAPTER, controller.step.value, "the book pick did not reach the controller")
        clickCell("ChapterCell")
        assertEquals(GridStep.VERSE, controller.step.value)
        clickCell("VerseCell")

        assertEquals(0, channelExits)
        assertEquals(NavRoutes.READING, currentRoute)
        assertEquals("Gen.1.1", passageResults.consume()?.verse)
    }

    /**
     * Classic `GridChoosePassageComposeActivity:84`: the grid's INTERNAL step back-stack is walked
     * first, and only an exhausted one leaves the screen. The second of the two back handlers this
     * graph declares, and it had no coverage either.
     *
     * Catches: a back handler that pops the destination straight away (the step stack then becomes
     * unreachable by back, and a user three steps in loses the whole screen on one press), and one
     * gated on an `enabled` flag instead of on `controller.back()`'s own answer -- at the root step
     * the handler must still run and hand the press on by popping.
     */
    @Test
    fun gridChoosePassageBackWalksTheStepStackBeforeLeaving() {
        setGraph(deps())
        navigateTo(NavRoutes.gridChoosePassage(isScripture = true))
        val controller = assertNotNull(gridController, "the arm never built a controller")

        clickCell("BookCell")
        assertEquals(GridStep.CHAPTER, controller.step.value)

        pressBack()
        assertEquals(GridStep.BOOK, controller.step.value, "back must pop the grid's own step first")
        assertEquals(NavRoutes.GRID_CHOOSE_PASSAGE_PATTERN, currentRoute, "a step back is not a destination pop")

        pressBack()
        assertEquals(NavRoutes.READING, currentRoute, "an exhausted step stack lets back leave")
        assertEquals(0, exitHostCalls)
        assertNull(passageResults.consume(), "backing out publishes nothing (classic set no result)")
    }

    @Test
    fun gridChoosePassageDefaultsIsScriptureToFalse() {
        setGraph(deps())
        navigateTo(NavRoutes.gridChoosePassage())
        assertEquals(false, gridIsScripture)
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
     * its follow-up (`UpdateMainBibleActivityDocuments` + reload) when the Download row navigates, and
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
