package net.bible.sharedui.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedcore.speak.SpeakTransportVd
import net.bible.sharedcore.window.RailEntry
import net.bible.sharedcore.window.RailLeading
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedcore.window.WindowTabBarModel
import net.bible.sharedui.reading.ReadingSearchBarCallbacks
import net.bible.sharedui.reading.ReadingToolbar
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.SpeakTransportBar
import net.bible.sharedui.reading.WindowPaneMenu
import net.bible.sharedui.reading.WindowTabBar
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme

/**
 * Every fixed-height bar must cap its text scale through [CappedBarFontScale] at its own chokepoint,
 * so every caller gets it, while content outside the bar keeps the system scale and a host that
 * provides no cap (Android) is unchanged.
 */
@OptIn(ExperimentalTestApi::class)
class BarFontScaleCoverageTest {
    @Composable
    private fun Themed(content: @Composable () -> Unit) =
        AbTheme { CompositionLocalProvider(LocalStrings provides iosStrings("en"), content = content) }

    private val system = 3.1f
    private val cap = 1.35f

    /** Size (width, height) of the text node [text] when [bar] is composed at [fontScale] with [barCap]. */
    private fun textWidth(fontScale: Float, barCap: Float?, text: String, bar: @Composable () -> Unit): Pair<Int, Int> {
        var size = -1 to -1
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = fontScale)) {
                    val provided = if (barCap == null) emptyList() else listOf(LocalBarFontScaleCap provides barCap)
                    CompositionLocalProvider(*provided.toTypedArray()) { Themed { bar() } }
                }
            }
            waitForIdle()
            size = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().size.let { it.width to it.height }
        }
        return size
    }

    /** Capped text must measure like a plain 1.35 render, and differ from an uncapped 3.1 render. */
    private fun assertBarCaps(text: String, bar: @Composable () -> Unit) {
        val capped = textWidth(system, cap, text, bar)
        val reference = textWidth(cap, null, text, bar)
        val uncapped = textWidth(system, null, text, bar)
        assertEquals(reference, capped, "bar text must measure at the capped scale")
        assertNotEquals(uncapped, capped, "without a cap the bar text follows the system scale (Android unchanged)")
    }

    private fun probe(scaleOut: (Float) -> Unit): @Composable () -> Unit = {
        scaleOut(LocalDensity.current.fontScale)
        Text("probe")
    }

    private fun probedScale(barCap: Float?, bar: @Composable (@Composable () -> Unit) -> Unit): Float {
        var seen = Float.NaN
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = system)) {
                    val provided = if (barCap == null) emptyList() else listOf(LocalBarFontScaleCap provides barCap)
                    CompositionLocalProvider(*provided.toTypedArray()) { Themed { bar(probe { seen = it }) } }
                }
            }
            waitForIdle()
        }
        return seen
    }

    @Test
    fun abTopAppBarTitleAndActionsAreCapped() {
        assertEquals(cap, probedScale(cap) { p -> AbTopAppBar(title = p) })
        var action = Float.NaN
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale = system),
                    LocalBarFontScaleCap provides cap,
                ) { Themed { AbTopAppBar(title = {}, actions = { action = LocalDensity.current.fontScale }) } }
            }
            waitForIdle()
        }
        assertEquals(cap, action)
    }

    @Test
    fun abTopAppBarUncappedWithoutProvider() {
        assertEquals(system, probedScale(null) { p -> AbTopAppBar(title = p) })
    }

    @Test
    fun abScaffoldBodyKeepsSystemScale() {
        var body = Float.NaN
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale = system),
                    LocalBarFontScaleCap provides cap,
                ) { Themed { AbScaffold(title = "T") { body = LocalDensity.current.fontScale } } }
            }
            waitForIdle()
        }
        assertEquals(system, body, "the cap must not leak into screen content")
    }

    @Test
    fun searchBarPlaceholderAndActionsAreCapped() {
        var action = Float.NaN
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale = system),
                    LocalBarFontScaleCap provides cap,
                ) {
                    Themed {
                        AbTopAppBar(
                            title = {},
                            search = AbTopBarSearchState(query = "", placeholder = "Find me"),
                            searchCallbacks = AbTopBarSearchCallbacks(
                                onQueryChange = {}, onClose = {}, onImeRequestHandled = {},
                            ),
                            searchActions = { action = LocalDensity.current.fontScale },
                        )
                    }
                }
            }
            waitForIdle()
        }
        assertEquals(cap, action)
        assertBarCaps("Find me") {
            AbTopAppBar(
                title = {},
                search = AbTopBarSearchState(query = "", placeholder = "Find me"),
                searchCallbacks = AbTopBarSearchCallbacks(onQueryChange = {}, onClose = {}, onImeRequestHandled = {}),
            )
        }
    }

    @Test
    fun selectionBarIsCapped() {
        assertBarCaps("42") {
            AbSelectionScaffold(
                title = "T", selectionMode = true, selectedCount = 42,
                onNavigateUp = {}, onExitSelection = {},
            ) {}
        }
    }

    @Test
    fun selectionScaffoldNormalBarIsCapped() {
        assertEquals(cap, probedScale(cap) { p ->
            AbSelectionScaffold(
                title = "T", selectionMode = false, selectedCount = 0,
                onNavigateUp = {}, onExitSelection = {}, actions = { p() },
            ) {}
        })
    }

    @Test
    fun overflowMenuInBarActionsKeepsSystemScaleWhileBarIsCapped() {
        var menu = Float.NaN
        var bar = Float.NaN
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale = system),
                    LocalBarFontScaleCap provides cap,
                ) {
                    Themed {
                        AbTopAppBar(title = {}, actions = {
                            bar = LocalDensity.current.fontScale
                            AbOverflowMenu(initiallyExpanded = true) { menu = LocalDensity.current.fontScale }
                        })
                    }
                }
            }
            waitForIdle()
        }
        assertEquals(cap, bar, "bar action stays capped")
        assertEquals(system, menu, "menu popup must follow the system scale")
    }

    /** Composes [content] and reports whether [text] is in the tree, optionally after [act]. */
    private fun rowPresent(text: String, act: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null, content: @Composable () -> Unit): Boolean {
        var present = false
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale = system),
                    LocalBarFontScaleCap provides cap,
                ) { Themed { content() } }
            }
            waitForIdle()
            act?.invoke(this)
            waitForIdle()
            present = onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        return present
    }

    @Test
    fun readingOverflowMenuRowsLiveInsideThePopup() {
        fun bar(expanded: Boolean): @Composable () -> Unit = {
            ReadingToolbar(
                toolbarState(), toolbarIcons(), toolbarCallbacks(),
                overflowItems = listOf(OptionsMenuItem("a", "Overflow item", false, false, true, false)),
                overflowExpanded = expanded,
            )
        }
        assertEquals(false, rowPresent("Overflow item", content = bar(false)), "closed menu must not render rows")
        assertEquals(true, rowPresent("Overflow item", content = bar(true)), "open menu shows rows")
    }

    @Test
    fun windowPaneMenuRowsLiveInsideThePopup() {
        fun menu(expanded: Boolean): @Composable () -> Unit = {
            WindowPaneMenu(
                items = listOf(WindowPaneMenuItem("p", "Pane item")),
                expanded = expanded, onItemClick = {}, onDismiss = {},
            )
        }
        assertEquals(false, rowPresent("Pane item", content = menu(false)), "closed menu must not render rows")
        assertEquals(true, rowPresent("Pane item", content = menu(true)), "open menu shows rows")
    }

    @Test
    fun recentTermsMenuRowsLiveInsideThePopup() {
        fun bar(open: Boolean): @Composable () -> Unit = {
            ReadingToolbar(
                toolbarState(), toolbarIcons(), toolbarCallbacks(),
                searchBar = ReadingSearchBarState(query = "", recentTerms = listOf("Recent term"), recentMenuOpen = open),
                searchBarCallbacks = searchCallbacks(),
            )
        }
        assertEquals(false, rowPresent("Recent term", content = bar(false)), "closed menu must not render rows")
        assertEquals(true, rowPresent("Recent term", content = bar(true)), "open menu shows rows")
    }

    @Test
    fun searchOverflowMenuRowsLiveInsideThePopup() {
        val rebuild = iosStrings("en").rebuildIndex
        val menuLabel = iosStrings("en").menu
        val bar: @Composable () -> Unit = {
            ReadingToolbar(
                toolbarState(), toolbarIcons(), toolbarCallbacks(),
                searchBar = ReadingSearchBarState(query = ""), searchBarCallbacks = searchCallbacks(),
            )
        }
        assertEquals(false, rowPresent(rebuild, content = bar), "closed menu must not render rows")
        assertEquals(
            true,
            rowPresent(rebuild, act = { onNodeWithContentDescription(menuLabel).performClick() }, content = bar),
            "open menu shows rows",
        )
    }

    @Test
    fun noCapMenuIsUnchanged() {
        var menu = Float.NaN
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = system)) {
                    Themed {
                        AbTopAppBar(title = {}, actions = {
                            AbOverflowMenu(initiallyExpanded = true) { menu = LocalDensity.current.fontScale }
                        })
                    }
                }
            }
            waitForIdle()
        }
        assertEquals(system, menu)
    }

    private fun assertMenuUncapped(text: String, bar: @Composable () -> Unit) {
        val withCap = textWidth(system, cap, text, bar)
        val noCap = textWidth(system, null, text, bar)
        assertEquals(noCap, withCap, "menu text must measure at the system scale")
    }

    @Test
    fun readingToolbarMenusKeepSystemScale() {
        assertMenuUncapped("Recent term") {
            ReadingToolbar(
                toolbarState(), toolbarIcons(), toolbarCallbacks(),
                searchBar = ReadingSearchBarState(
                    query = "", recentTerms = listOf("Recent term"), recentMenuOpen = true,
                ),
                searchBarCallbacks = searchCallbacks(),
            )
        }
        assertMenuUncapped("Overflow item") {
            ReadingToolbar(
                toolbarState(), toolbarIcons(), toolbarCallbacks(),
                overflowItems = listOf(OptionsMenuItem("a", "Overflow item", false, false, true, false)),
                overflowExpanded = true,
            )
        }
    }

    @Test
    fun windowPaneMenuKeepsSystemScale() {
        assertMenuUncapped("Pane item") {
            WindowTabBar(
                model = tabModel(), onRestore = {}, onWindowLongPress = {}, onAddWindow = {},
                onUnMaximise = {}, onToggleCollapse = {}, windowLabel = { if (it.id == "w1") "KJV" else "ESV" },
                menuWindowId = "w1", menuItems = listOf(WindowPaneMenuItem("p", "Pane item")),
            )
        }
    }

    @Test
    fun readingToolbarSearchRowIsCapped() {
        // The field's text layout records the density it was laid out with (the placeholder measures
        // 0 wide in this row, so width is not a usable oracle here).
        fun layoutFontScale(barCap: Float?): Float {
            var seen = Float.NaN
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = system)) {
                        val provided = if (barCap == null) emptyList() else listOf(LocalBarFontScaleCap provides barCap)
                        CompositionLocalProvider(*provided.toTypedArray()) {
                            Themed {
                                ReadingToolbar(
                                    toolbarState(), toolbarIcons(), toolbarCallbacks(),
                                    searchBar = ReadingSearchBarState(query = "Query text"),
                                    searchBarCallbacks = searchCallbacks(),
                                )
                            }
                        }
                    }
                }
                waitForIdle()
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                onNodeWithText("Query text", useUnmergedTree = true).fetchSemanticsNode().config
                    .getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)
                    ?.action?.invoke(layouts)
                seen = layouts.first().layoutInput.density.fontScale
            }
            return seen
        }
        assertEquals(cap, layoutFontScale(cap))
        assertEquals(system, layoutFontScale(null))
    }

    @Test
    fun quickSheetTabRowIsCapped() {
        assertBarCaps("Tab one") {
            AbQuickSheetContent(
                title = "Sheet", onClose = {},
                tabs = listOf(AbQuickSheetTab("1", "Tab one"), AbQuickSheetTab("2", "Tab two")),
                selectedTabId = "1",
            ) {}
        }
    }

    @Test
    fun longTitleKeepsFullTextInSemantics() {
        val full = "A very long screen title that cannot possibly fit in one line of a narrow bar at any scale"
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(1f, fontScale = system),
                    LocalBarFontScaleCap provides cap,
                ) { Themed { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(200.dp)) { AbTopAppBar(title = { AbTopBarTitle(full) }) } } }
            }
            waitForIdle()
            val texts = onNodeWithText(full, useUnmergedTree = true).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Text)
            assertTrue(texts?.any { it.text == full } == true, "full title must stay in semantics")
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            onNodeWithText(full, useUnmergedTree = true).fetchSemanticsNode().config
                .getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            assertTrue(layouts.firstOrNull()?.hasVisualOverflow == true, "the title must actually be truncated")
        }
    }

    @Test
    fun readingToolbarIsCapped() {
        assertBarCaps("Genesis 1") { ReadingToolbar(toolbarState(), toolbarIcons(), toolbarCallbacks()) }
    }

    @Test
    fun windowTabBarIsCapped() {
        val w = WindowSnapshot("w1", WindowStateValue.VISIBLE, 1f, true, false, false, 0, false)
        val w2 = w.copy(id = "w2")
        val model = WindowTabBarModel(
            RailLeading.CollapseToggle(true), true,
            listOf(RailEntry.WindowTab(w, true), RailEntry.WindowTab(w2, false)),
        )
        assertBarCaps("KJV") {
            WindowTabBar(
                model = model, onRestore = {}, onWindowLongPress = {}, onAddWindow = {},
                onUnMaximise = {}, onToggleCollapse = {}, windowLabel = { if (it.id == "w1") "KJV" else "ESV" },
            )
        }
    }

    @Test
    fun speakBarIsCapped() {
        assertBarCaps("Reading John 3") {
            SpeakTransportBar(
                state = SpeakTransportVd(visible = true, statusText = "Reading John 3"),
                onPlayPause = {}, onStop = {}, onRewind = {}, onForward = {}, onPrev = {}, onNext = {},
                onBookmark = {}, onConfig = {},
            )
        }
    }

    private fun tabModel(): WindowTabBarModel {
        val w = WindowSnapshot("w1", WindowStateValue.VISIBLE, 1f, true, false, false, 0, false)
        return WindowTabBarModel(
            RailLeading.CollapseToggle(true), true,
            listOf(RailEntry.WindowTab(w, true), RailEntry.WindowTab(w.copy(id = "w2"), false)),
        )
    }

    private fun searchCallbacks() = ReadingSearchBarCallbacks(
        onQueryChange = {}, onSubmit = {}, onRecentTermsOpen = {}, onRecentTermsDismiss = {},
        onRecentTermSelected = {}, onOpenSettings = {}, onClose = {}, onImeRequestHandled = {},
        onFieldFocusChanged = {}, onRebuildIndex = {}, onShowResults = {},
    )

    private fun toolbarState() =
        ToolbarState("Genesis 1", "KJV", false, false, false, false, 0, false, false, true, null, false)

    @Composable
    private fun toolbarIcons(): ReadingToolbarIcons {
        val p = ColorPainter(Color.Gray)
        return ReadingToolbarIcons(p, p, p, p, p, p, p, p, p)
    }

    private fun toolbarCallbacks() = ReadingToolbarCallbacks(
        onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
        onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
        onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {}, onSpeak = {},
        onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
    )
}
