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

package net.bible.sharedui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.QuickDocMenuItem
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.reading.SearchFieldImeRequest
import net.bible.sharedcore.reading.SearchFieldLeadingAction
import net.bible.sharedcore.reading.ToolbarButton
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.reading.fitToolbarButtons
import net.bible.sharedcore.reading.isWorkspaceColorSet
import net.bible.sharedcore.reading.readingToolbarContainerArgb
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.components.AbHelpMenuIcon
import net.bible.sharedui.components.AbInfoDialog
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbSearchField
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.LocalDisplayColorMode
import net.bible.sharedui.theme.LocalIsDarkTheme
import net.bible.sharedui.theme.SyncSystemBars
import kotlin.math.abs

/**
 * The set of icons [ReadingToolbar] draws, supplied by the host as [Painter]s (never
 * `R.drawable`/`ImageVector` resource ids) so this file stays iOS-clean. The host (Task 5) wires
 * these to the same drawables the classic View toolbar uses (`main_bible_view.xml`).
 */
data class ReadingToolbarIcons(
    val home: Painter,
    val search: Painter,
    val speak: Painter,
    val strongs: Painter,
    val bible: Painter,
    val commentary: Painter,
    val workspace: Painter,
    val overflow: Painter,
)

/**
 * All the interaction callbacks [ReadingToolbar] can invoke. Mirrors the classic
 * `MainBibleActivity` toolbar's click/long-click/fling handlers 1:1 (see `updateActions()` and
 * `setupToolbarFlingDetection()`), minus anything view-specific (no `View`/`MotionEvent` in the
 * signatures — the host resolves those on the Android side).
 */
data class ReadingToolbarCallbacks(
    val onHome: () -> Unit,
    val onTitleTap: () -> Unit,
    val onTitleLongPress: () -> Unit,
    val onTitleFlingVertical: () -> Unit,
    val onTitleFlingHorizontal: (forward: Boolean) -> Unit,
    val onBible: () -> Unit,
    val onBibleLong: () -> Unit,
    val onCommentary: () -> Unit,
    val onCommentaryLong: () -> Unit,
    val onStrongs: () -> Unit,
    val onStrongsLong: () -> Unit,
    val onSearch: () -> Unit,
    val onSpeak: () -> Unit,
    val onSpeakLong: () -> Unit,
    val onWorkspace: () -> Unit,
    val onOverflow: () -> Unit,
)

/** Host-owned quick-document picker state for one toolbar doc button (Bible or Commentary). */
data class QuickDocMenuState(val expanded: Boolean = false, val items: List<QuickDocMenuItem> = emptyList())

/**
 * The interactions [ReadingToolbar]'s search mode can invoke (F6 Task 4). Paired with a non-null
 * [ReadingSearchBarState]; Task 8 wires both to `ReadingSearchController`.
 */
data class ReadingSearchBarCallbacks(
    val onQueryChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onRecentTermsOpen: () -> Unit,
    val onRecentTermsDismiss: () -> Unit,
    val onRecentTermSelected: (String) -> Unit,
    val onOpenSettings: () -> Unit,
    val onClose: () -> Unit,
    /** The field has been focused or released as [ReadingSearchBarState.imeRequest] asked. */
    val onImeRequestHandled: () -> Unit,
    /**
     * The field gained or lost focus. Reported because `MainBibleActivity` keys its IME padding on
     * this and not on search mode being active: search mode outlives the results sheet, so a WebView
     * note editor can be opened while it is still on, and that editor must still be lifted above the
     * keyboard (spec §4).
     */
    val onFieldFocusChanged: (Boolean) -> Unit,
    /** The overflow's "Rebuild index" — see `ReadingSearchController.requestRebuildIndex`. */
    val onRebuildIndex: () -> Unit,
    /** The leading results button — see `ReadingSearchController.showResults`. */
    val onShowResults: () -> Unit,
)

/** Height of the toolbar row — matches the classic `@dimen/toolbar_height` (56dp). */
private val ToolbarHeight = 56.dp

/** Touch-target width for a single icon button (home / quick button / overflow). */
private val ToolbarButtonWidth = 48.dp

/** Classic's own help link (`Search.kt:283`), kept verbatim so the help says the same thing. */
private const val LUCENE_QUERY_SYNTAX_URL = "https://lucene.apache.org/core/2_9_4/queryparsersyntax.html"

/** The EPUB search engine's own syntax reference (`EpubSearchComposeActivity.help`'s link). */
private const val FTS5_QUERY_SYNTAX_URL = "https://www.sqlite.org/fts5.html#full_text_query_syntax"

/**
 * Stateless port of the classic `MainBibleActivity` toolbar (`main_bible_view.xml`'s
 * `toolbarLayout`): a home (drawer) button, a tappable title block (page title + document title +
 * sync indicator), the width-fitted "quick" buttons (Bible/Commentary/Strongs/Search/Speak/
 * Workspace — see [fitToolbarButtons]), and an always-present overflow button.
 *
 * The available width for [fitToolbarButtons] is measured from this composable's own laid-out
 * width via [BoxWithConstraints] (mirroring classic `updateActions()`'s
 * `resources.displayMetrics.widthPixels` full-screen-width budget).
 *
 * The overflow button anchors a [ReadingOverflowMenu] (Batch 12b-C Task 2): [overflowItems] /
 * [overflowExpanded] / [onOverflowItemClick] / [onOverflowDismiss] are host-owned state — tapping
 * the button still only calls [ReadingToolbarCallbacks.onOverflow]; it's the host's job to build
 * the item list and flip [overflowExpanded] to `true` in response. All four are defaulted
 * (empty list / collapsed / no-ops) so existing call sites and their goldens are unaffected.
 *
 * The Bible and Commentary quick buttons each anchor their own [QuickDocMenu] (Batch 12g) the same
 * way — [bibleQuickDoc]/[commentaryQuickDoc] are host-owned [QuickDocMenuState]s, and
 * [onQuickDocSelect]/[onQuickDocDismiss] are shared by both menus (only one can be open at a
 * time). All four default to collapsed/empty/no-op so existing call sites and their goldens are
 * unaffected.
 *
 * [overflowIcon] is forwarded verbatim to [ReadingOverflowMenu]'s `icon` parameter — the host
 * lambda resolving each row's [OptionsMenuItem.iconKey] to a `Painter`. Defaulted to always-`null`
 * so existing call sites and their goldens are unaffected.
 *
 * A non-null [searchBar] (with its [searchBarCallbacks]) replaces the whole normal row with the
 * search field — the F6 in-place search mode. Both default to `null`, i.e. "not searching", which is
 * why every existing call site is unchanged and the normal-mode goldens are byte-identical. The
 * field lives HERE rather than in its own composable because the three colours it needs
 * (`container`, `onContainer`, `documentTitleColor`) are computed inline below and exposed nowhere.
 */
/** [Modifier.testTag] on [ReadingSearchField], so [SearchFieldCaretTest] (`:app`) can drive it. */
const val SEARCH_FIELD_TAG = "reading-search-field"

/**
 * The bare `BasicTextField` the reading toolbar's search mode renders (F44/B2).
 *
 * Owns a local [TextFieldValue] rather than trusting [value] for the selection too: the host
 * publishes [value] through a `combine`/`stateIn` hop that can land one recomposition AFTER the
 * keystroke that produced it. A `String`-valued `BasicTextField` recomposing against that stale text
 * has its selection clamped to 0 by `TextFieldValue`'s own constructor — the caret jumps to the start
 * and the next character lands in front of the first ("ba" instead of "ab"). Keeping the
 * `TextFieldValue` here makes the field correct no matter how many hops the hoisted value takes.
 *
 * An external change (a seeded query, the clear button, a recent-term pick) is adopted with the
 * caret placed at the end; an echo of our own edit — [fieldValue]'s text already matches [value] —
 * is left alone, selection intact.
 *
 * Extracted to its own (public, not just inlined) composable so [SearchFieldCaretTest] in `:app` can
 * drive the SAME field the toolbar renders, via [SEARCH_FIELD_TAG] — a test against a copy of this
 * field would pass while the real field stayed broken.
 */
@Composable
fun ReadingSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    cursorBrush: Brush,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { it() },
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (fieldValue.text != value) {
        fieldValue = TextFieldValue(value, TextRange(value.length))
    }
    BasicTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = cursorBrush,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.testTag(SEARCH_FIELD_TAG),
        decorationBox = decorationBox,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingToolbar(
    state: ToolbarState,
    icons: ReadingToolbarIcons,
    callbacks: ReadingToolbarCallbacks,
    searchBar: ReadingSearchBarState? = null,
    searchBarCallbacks: ReadingSearchBarCallbacks? = null,
    searchMoreRecent: Boolean = true,
    overflowItems: List<OptionsMenuItem> = emptyList(),
    overflowExpanded: Boolean = false,
    onOverflowItemClick: (id: String) -> Unit = {},
    onOverflowDismiss: () -> Unit = {},
    bibleQuickDoc: QuickDocMenuState = QuickDocMenuState(),
    commentaryQuickDoc: QuickDocMenuState = QuickDocMenuState(),
    onQuickDocSelect: (id: String) -> Unit = {},
    onQuickDocDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    overflowIcon: @Composable (iconKey: String) -> Painter? = { null },
) {
    val density = LocalDensity.current
    // A/B batch 4b §6: the derived variant takes the scheme's own container role (and with it the
    // matching onPrimaryContainer, so contrast is guaranteed by M3 rather than by our luminance
    // call). Derived is the default (unconditionally since the 2026-09-18 master-switch
    // retirement -- see net.bible.sharedui.deriveToolbarFromTheme); the literal path below,
    // batch 3 F3's original, survives only behind the TOOLBAR_LITERAL_COLOR_FEATURE opt-out.
    //
    // A/B batch 3 F3: the container is the workspace colour when the user set one (see
    // readingToolbarContainerArgb's sentinel), otherwise today's plain surface.
    val container = if (state.deriveToolbarFromTheme) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color(
            readingToolbarContainerArgb(
                workspaceArgb = state.workspaceColorArgb,
                surfaceArgb = MaterialTheme.colorScheme.surface.toArgb(),
                nightMode = LocalIsDarkTheme.current,
                colorMode = LocalDisplayColorMode.current,
            )
        )
    }
    // Not-set case must stay pixel-identical to before this feature: today's two M3 tokens
    // (onSurface/onSurfaceVariant), not a luminance-derived black/white — those tokens are tuned for
    // the theme's own surface, which is exactly what "not set" means. Only when the user actually
    // picked a workspace colour do we abandon them for a luminance call over that arbitrary colour
    // (M3's tokens are not tuned for it), same 0.45 threshold as
    // ReadingProgressPalette.textColorForBackground. Branch on isWorkspaceColorSet (not e.g.
    // "container == surface") so this never drifts from readingToolbarContainerArgb's own sentinel.
    val workspaceColorSet = isWorkspaceColorSet(state.workspaceColorArgb)
    val onContainer = if (state.deriveToolbarFromTheme) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else if (workspaceColorSet) {
        if (container.luminance() < 0.45f) Color.White else Color.Black
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    // The document title keeps its own, more-secondary M3 token in the not-set case
    // (onSurfaceVariant is not simply onSurface at reduced alpha), but once a workspace colour is in
    // play there is no equivalent "variant" token for an arbitrary user colour, so it is derived the
    // same way the rest of this feature derives secondary text: the primary content colour at 0.75
    // alpha. The derived-theme variant reuses onContainer the same way, rather than recomputing.
    val documentTitleColor = if (state.deriveToolbarFromTheme || workspaceColorSet) {
        onContainer.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    // A/B batch 3 F1: tell the host what colour sits under the status bar so it can set
    // statusBarColor (API < 35) and the icon appearance (all API levels). fillWindowBackground is
    // false because the background modifier below already paints the strip on API 35+.
    SyncSystemBars(container = container, fillWindowBackground = false)
    // F6 search mode. Deliberately BELOW SyncSystemBars (so the status bar keeps the toolbar's
    // colour while searching) and ABOVE BoxWithConstraints (which applies `modifier`, the background,
    // the insets and the height itself, and measures the quick-button budget this row does not use).
    // Wrapped in its own LocalContentColor provider so the field, its icons and its placeholder
    // inherit the toolbar's content colour in all four display modes and on a workspace-coloured
    // toolbar, exactly as the normal row does.
    if (searchBar != null && searchBarCallbacks != null) {
        val strings = LocalStrings.current
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val focusManager = LocalFocusManager.current
        var searchHelpOpen by remember { mutableStateOf(false) }
        var searchMenuOpen by remember { mutableStateOf(false) }
        // F6-B2/B3. Keyed on the instruction so the ack (which returns it to null) is what arms the
        // next edge — a level would not fire twice for two submits in a row. `show()` follows
        // `requestFocus()` belt-and-braces: focusing a field usually raises the IME, and findings
        // F23/F24 are this repo's record of "usually" not being good enough.
        LaunchedEffect(searchBar.imeRequest) {
            when (searchBar.imeRequest) {
                null -> return@LaunchedEffect
                SearchFieldImeRequest.Focus -> { focusRequester.requestFocus(); keyboard?.show() }
                SearchFieldImeRequest.Release -> { focusManager.clearFocus(); keyboard?.hide() }
            }
            searchBarCallbacks.onImeRequestHandled()
        }
        CompositionLocalProvider(LocalContentColor provides onContainer) {
            Row(
                modifier
                    .fillMaxWidth()
                    // .background BEFORE .windowInsetsPadding, same reason as the normal row below:
                    // a background covers the padding applied after it, so the container colour
                    // extends under the status bar instead of stopping below it.
                    .background(container)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    // The normal row does the same below: without it the field slides under a display
                    // cutout in landscape.
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Horizontal)
                    )
                    .height(ToolbarHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarVectorButton(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    strings.searchClose,
                    searchBarCallbacks.onClose,
                )
                Box(Modifier.weight(1f)) {
                    // Deliberately NOT AbSearchField. That is an `OutlinedTextField`, and it fails here
                    // twice over — both found by inspecting the recorded goldens zoomed, neither
                    // detectable by any assertion:
                    //
                    //  1. Its M3 minimum height is exactly [ToolbarHeight], so its outline lands flush on
                    //     the toolbar's top and bottom edges (measured: the border occupied rows 0 and 55
                    //     of a 56px capture), pressing against the status bar with no breathing room.
                    //  2. Its colours come from M3's own defaults rather than this toolbar's `onContainer`,
                    //     so on a saturated workspace colour the text and both field icons rendered dark on
                    //     purple while the neighbouring toolbar icons were white. Same defect family as
                    //     batch 3's F1.
                    //
                    // In an app bar the bar IS the container, so this is a bare field whose every colour
                    // derives from `onContainer`.
                    ReadingSearchField(
                        value = searchBar.query,
                        onValueChange = searchBarCallbacks.onQueryChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = onContainer),
                        cursorBrush = SolidColor(onContainer),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchBarCallbacks.onSubmit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { searchBarCallbacks.onFieldFocusChanged(it.isFocused) },
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (searchBar.leadingAction) {
                                    SearchFieldLeadingAction.ShowResults -> ToolbarVectorButton(
                                        Icons.AutoMirrored.Filled.FormatListBulleted,
                                        strings.searchShowResults,
                                        searchBarCallbacks.onShowResults,
                                    )
                                    SearchFieldLeadingAction.RecentTerms -> ToolbarVectorButton(
                                        Icons.Filled.History,
                                        strings.recentSearches,
                                        searchBarCallbacks.onRecentTermsOpen,
                                    )
                                    SearchFieldLeadingAction.None -> Unit
                                }
                                Box(Modifier.weight(1f)) {
                                    if (searchBar.query.isEmpty()) {
                                        Text(
                                            strings.search,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = onContainer.copy(alpha = 0.6f),
                                        )
                                    }
                                    innerTextField()
                                }
                                if (searchBar.query.isNotEmpty()) {
                                    ToolbarVectorButton(
                                        Icons.Filled.Clear,
                                        strings.searchClear,
                                    ) { searchBarCallbacks.onQueryChange("") }
                                }
                            }
                        },
                    )
                    DropdownMenu(
                        expanded = searchBar.recentMenuOpen,
                        onDismissRequest = searchBarCallbacks.onRecentTermsDismiss,
                    ) {
                        searchBar.recentTerms.forEach { term ->
                            AbMenuItem(
                                text = term,
                                onClick = { searchBarCallbacks.onRecentTermSelected(term) },
                                icon = { Icon(Icons.Filled.History, contentDescription = null) },
                            )
                        }
                    }
                }
                // `Tune` rather than the host's overflow painter: this opens settings, and Tune is what
                // `AbSettingsSummarySheet` already uses for exactly that. A vertical ⋮ reads as "more
                // actions", which this is not.
                ToolbarVectorButton(Icons.Filled.Tune, strings.searchOptions, searchBarCallbacks.onOpenSettings)
                // The submit button is gone: the IME already carries `ImeAction.Search`, and a button
                // duplicating it does not earn a slot in a row this crowded. The slot holds the
                // overflow classic had on its search screen instead (F6-B5).
                //
                // Hand-rolled from ToolbarVectorButton + DropdownMenu rather than reusing
                // AbOverflowMenu, for the same reason the recent-terms menu just above is: this row
                // is not a TopAppBar, its buttons are fixed-width ToolbarVectorButtons, and
                // AbOverflowMenu's IconButton would be a different width — changing the row's
                // geometry to add a menu item.
                Box {
                    ToolbarVectorButton(Icons.Filled.MoreVert, strings.menu) { searchMenuOpen = true }
                    DropdownMenu(expanded = searchMenuOpen, onDismissRequest = { searchMenuOpen = false }) {
                        AbMenuItem(
                            text = strings.rebuildIndex,
                            onClick = { searchMenuOpen = false; searchBarCallbacks.onRebuildIndex() },
                            icon = { Icon(Icons.Filled.Autorenew, contentDescription = null) },
                        )
                        AbMenuItem(
                            text = strings.help,
                            onClick = { searchMenuOpen = false; searchHelpOpen = true },
                            icon = AbHelpMenuIcon,
                        )
                    }
                }
            }
        }
        if (searchHelpOpen) {
            // Round 17d: EPUB search is SQLite FTS5, not Lucene, and this dialog is where an EPUB
            // search is actually helped — the toolbar IS the EPUB search form. Showing the Lucene
            // body here documented an engine this session is not using.
            val forEpub = searchBar.forEpub
            val linkLabel = if (forEpub) strings.helpFts5 else strings.helpApacheLucene
            AbInfoDialog(
                title = strings.help,
                body = "${if (forEpub) strings.helpSearchEpub else strings.helpSearchBible}" +
                    "\n\n${strings.helpSearchDetails(linkLabel)}",
                onDismiss = { searchHelpOpen = false },
                readMoreLabel = linkLabel,
                readMoreUrl = if (forEpub) FTS5_QUERY_SYNTAX_URL else LUCENE_QUERY_SYNTAX_URL,
            )
        }
        return
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            // A/B batch 3 F1: .background BEFORE .windowInsetsPadding — a background covers the
            // padding applied after it, so the container colour extends under the status bar
            // instead of stopping below it (the reported "light strip, white clock" bug).
            .background(container)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
            .height(ToolbarHeight),
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val buttons = remember(state, widthPx, density.density, searchMoreRecent) {
            fitToolbarButtons(state, widthPx, density.density, searchMoreRecent)
        }
        CompositionLocalProvider(LocalContentColor provides onContainer) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                ToolbarIconButton(
                    icon = icons.home,
                    // TODO: no LocalStrings field for this yet — keep literal until one is added.
                    contentDescription = "Menu",
                    onClick = callbacks.onHome,
                )
                ReadingToolbarTitle(state, callbacks, documentTitleColor, Modifier.weight(1f).fillMaxHeight())
                buttons.forEach { button ->
                    QuickToolbarButton(
                        button = button,
                        state = state,
                        icons = icons,
                        callbacks = callbacks,
                        bibleQuickDoc = bibleQuickDoc,
                        commentaryQuickDoc = commentaryQuickDoc,
                        onQuickDocSelect = onQuickDocSelect,
                        onQuickDocDismiss = onQuickDocDismiss,
                    )
                }
                Box {
                    ToolbarIconButton(
                        icon = icons.overflow,
                        // TODO: no LocalStrings field for this yet — keep literal until one is added.
                        contentDescription = "Options",
                        onClick = callbacks.onOverflow,
                    )
                    ReadingOverflowMenu(
                        items = overflowItems,
                        expanded = overflowExpanded,
                        onItemClick = onOverflowItemClick,
                        onDismiss = onOverflowDismiss,
                        icon = overflowIcon,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickToolbarButton(
    button: ToolbarButton,
    state: ToolbarState,
    icons: ReadingToolbarIcons,
    callbacks: ReadingToolbarCallbacks,
    bibleQuickDoc: QuickDocMenuState = QuickDocMenuState(),
    commentaryQuickDoc: QuickDocMenuState = QuickDocMenuState(),
    onQuickDocSelect: (id: String) -> Unit = {},
    onQuickDocDismiss: () -> Unit = {},
) {
    val strings = LocalStrings.current
    when (button) {
        ToolbarButton.BIBLE -> Box {
            ToolbarIconButton(icons.bible, strings.bible, callbacks.onBible, callbacks.onBibleLong)
            QuickDocMenu(bibleQuickDoc.expanded, bibleQuickDoc.items, onQuickDocSelect, onQuickDocDismiss)
        }
        // TODO: no LocalStrings field for this yet — keep literal until one is added.
        ToolbarButton.COMMENTARY -> Box {
            ToolbarIconButton(icons.commentary, "Commentary", callbacks.onCommentary, callbacks.onCommentaryLong)
            QuickDocMenu(commentaryQuickDoc.expanded, commentaryQuickDoc.items, onQuickDocSelect, onQuickDocDismiss)
        }
        ToolbarButton.STRONGS -> ToolbarIconButton(
            icon = icons.strongs,
            // TODO: no LocalStrings field for this yet — keep literal until one is added.
            contentDescription = "Strong's numbers",
            onClick = callbacks.onStrongs,
            onLongClick = callbacks.onStrongsLong,
            alpha = if (state.strongsMode == 0) 0.5f else 1f,
        )
        ToolbarButton.SEARCH -> ToolbarIconButton(icons.search, strings.search, callbacks.onSearch)
        ToolbarButton.SPEAK -> ToolbarIconButton(icons.speak, strings.speak, callbacks.onSpeak, callbacks.onSpeakLong)
        // TODO: no LocalStrings field for this yet — keep literal until one is added.
        ToolbarButton.WORKSPACE -> ToolbarIconButton(icons.workspace, "Workspace", callbacks.onWorkspace)
    }
}

/**
 * [ToolbarIconButton] for a Material vector icon rather than a host-supplied [Painter]. The search row
 * uses vectors throughout: its affordances (back, recent searches, clear, settings, submit) are generic
 * Material ones, so routing them through [ReadingToolbarIcons] would mean five new host resources for no
 * gain.
 */
@Composable
private fun ToolbarVectorButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .width(ToolbarButtonWidth)
            .combinedClickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(AbActionIconSize),
        )
    }
}

/** A single toolbar icon button: fixed touch-target width, full-height, tap + optional long-press. */
@Composable
private fun ToolbarIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    alpha: Float = 1f,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .width(ToolbarButtonWidth)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(AbActionIconSize).alpha(alpha),
        )
    }
}

/**
 * The tappable title block: page title (large, single line, ellipsized) over the document title
 * (small, secondary) with a sync indicator alongside it while [ToolbarState.syncRunning]. Gestures
 * mirror classic `setupToolbarFlingDetection()`: a plain tap re-opens the key chooser, a long-press
 * opens the document chooser, and a fling is routed to a vertical (workspace selector) or
 * horizontal (cycle workspace) callback depending on its dominant axis — see [detectTitleGestures].
 */
@Composable
private fun ReadingToolbarTitle(
    state: ToolbarState,
    callbacks: ReadingToolbarCallbacks,
    documentTitleColor: Color,
    modifier: Modifier = Modifier,
) {
    // Keyed on Unit (stable) rather than `callbacks` — a recomposition mid-gesture (e.g.
    // state.syncRunning flipping) must not restart the gesture-detector coroutine and abort an
    // in-flight tap/long-press/fling. rememberUpdatedState lets the long-lived gesture block
    // always read the LATEST callbacks without needing pointerInput to be re-keyed on them.
    val currentCallbacks = rememberUpdatedState(callbacks)
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .pointerInput(Unit) { detectTitleGestures(currentCallbacks) },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = state.pageTitle,
            style = MaterialTheme.typography.titleMedium,
            color = LocalContentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // weight(1f, fill = false) makes this Text yield width to the trailing SyncIndicator:
            // a Row measures non-weighted children (the indicator) FIRST and subtracts their size
            // from the available width before dividing the remainder among weighted children, so
            // the indicator always gets its fixed size reserved — unlike two plain non-weighted
            // siblings, where each is measured against the FULL available width independently and
            // the ellipsized Text claims all of it, leaving the indicator no room (invisible even
            // at a non-zero progress value). `fill = false` keeps the shrink-to-content sizing this
            // Text had before (it doesn't force-expand into the weighted share when the title is
            // short).
            Text(
                text = state.documentTitle,
                style = MaterialTheme.typography.labelSmall,
                color = documentTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (state.syncRunning) {
                SyncIndicator(Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * Small spinner shown next to the document title while a background sync is running. Under
 * inspection (Roborazzi goldens / previews) it renders a frozen determinate frame instead of the
 * animated indeterminate one — same reasoning as [net.bible.sharedui.components.AbLoadingIndicator]:
 * `LocalInspectionMode` does not freeze Compose's `InfiniteTransition`, so an indeterminate spinner
 * would capture a non-deterministic frame and make the "syncing" golden flaky. The frozen fraction
 * is a non-zero [FrozenSyncIndicatorProgress] — a `progress = 0f` frame draws a zero-sweep (fully
 * invisible) arc, which would make the "syncing" golden indistinguishable from "not syncing".
 */
@Composable
private fun SyncIndicator(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        CircularProgressIndicator(
            progress = { FrozenSyncIndicatorProgress },
            modifier = modifier.size(10.dp),
            strokeWidth = 1.5.dp,
        )
    } else {
        CircularProgressIndicator(modifier = modifier.size(10.dp), strokeWidth = 1.5.dp)
    }
}

/** Frozen progress fraction [SyncIndicator] draws under [LocalInspectionMode] — see its kdoc. */
private const val FrozenSyncIndicatorProgress = 0.65f

/** Distance (px) a drag must cover on its dominant axis before it counts as a fling, not a tap-adjacent wobble. */
private const val MinFlingDistanceDp = 40

/**
 * Single hand-rolled pointer-input gesture recognizer for the title block, combining tap,
 * long-press and directional fling detection in ONE gesture (rather than layering
 * `detectTapGestures`/`detectDragGestures` in separate `pointerInput` modifiers, which would race
 * each other for the same down event). Distance-only thresholds are used for the fling axis
 * decision (loosely mirroring classic `setupToolbarFlingDetection`'s distance+velocity check —
 * exact velocity parity is not required for this port).
 *
 * [currentCallbacks] is a [State] (from [rememberUpdatedState] at the call site) rather than a
 * plain [ReadingToolbarCallbacks], so the caller can key its `pointerInput` on a stable value
 * (`Unit`) without this long-lived gesture loop ever invoking a stale lambda.
 */
private suspend fun PointerInputScope.detectTitleGestures(currentCallbacks: State<ReadingToolbarCallbacks>) {
    val touchSlop = viewConfiguration.touchSlop
    val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis
    val minFlingDistancePx = MinFlingDistanceDp.dp.toPx()

    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            var totalDx = 0f
            var totalDy = 0f
            var isDrag = false
            var longPressFired = false

            // Single long-press deadline armed ONCE from the initial down (mirrors Compose
            // Foundation's `detectTapGestures`) instead of a per-pointer-event timeout that
            // measured "time since the last event" — touch jitter (ACTION_MOVE) restarted that
            // clock on every move, so it could starve the long-press indefinitely. Cancelled as
            // soon as the gesture resolves into a drag (a fling in progress is never also a
            // long-press) or the pointer lifts/cancels.
            val longPressJob = launch {
                delay(longPressTimeoutMillis)
                longPressFired = true
                currentCallbacks.value.onTitleLongPress()
            }

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change: PointerInputChange = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y
                    if (!isDrag && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                        isDrag = true
                        longPressJob.cancel()
                    }
                    change.consume()
                }
            } finally {
                longPressJob.cancel()
            }

            if (!longPressFired) {
                if (isDrag) {
                    if (abs(totalDy) > abs(totalDx) && abs(totalDy) > minFlingDistancePx) {
                        currentCallbacks.value.onTitleFlingVertical()
                    } else if (abs(totalDx) > minFlingDistancePx) {
                        currentCallbacks.value.onTitleFlingHorizontal(totalDx < 0)
                    }
                } else {
                    currentCallbacks.value.onTitleTap()
                }
            }
        }
    }
}
