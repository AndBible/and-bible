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

package net.bible.sharedui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.SyncSystemBars

/**
 * The floor the title is allowed to shrink to. Above this it autosizes; below it, it ellipsises.
 *
 * F76 (2026-10-02): titles stay on ONE line. The previous single-line attempt (`1d3e79b76`) was
 * reverted by `d2e8ecd71` because at a 15sp floor eight screens' normal-length titles ellipsised on
 * the 320dp golden canvas ("Default tool settings" -> "Default tool se…"); the lower floor is what
 * makes one line viable. A title that still ellipsises at 12sp is recorded, not shrunk further.
 */
private val AbTopBarTitleMinFontSize = 12.sp

/**
 * The shared screen title for [AbTopAppBar]'s `title` slot.
 *
 * Material3's `TopAppBar` GROWS with its title slot, and a bare `Text` wraps without bound — so a
 * long screen title inflated the whole bar (reported as F15 and again in the F45–F50 round). Here
 * the title shrinks to fit instead: on one line, autosized down to
 * [AbTopBarTitleMinFontSize], then ellipsised (as Classic's ActionBar did).
 *
 * Uses `BasicText` because that is what carries `autoSize` — and `BasicText` takes its colour from
 * its `style`, NOT from `LocalContentColor`, so the colour must be copied in explicitly. Omitting
 * that renders the title in the default (unset) colour, which looks exactly like the top-bar colour
 * work in F48 having no effect.
 */
@Composable
fun AbTopBarTitle(text: String) {
    // Reads MaterialTheme.typography.titleLarge directly rather than LocalTextStyle.current (the
    // style M3's TopAppBar actually provides into its title slot, from its own titleTextStyle
    // parameter). Equal today — nothing in this repo overrides titleTextStyle — but a future
    // TopAppBar call site that does would have its override silently ignored here, since the
    // autosizer would keep solving against titleLarge instead of what the bar is actually drawing.
    val style = MaterialTheme.typography.titleLarge
    BasicText(
        text = text,
        style = style.copy(color = LocalContentColor.current),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = AbTopBarTitleMinFontSize,
            maxFontSize = style.fontSize,
            stepSize = 0.5.sp,
        ),
    )
}

/**
 * Reusable Material3 top app bar: a title slot, optional up-navigation, and trailing actions.
 *
 * Round 6. [search] / [searchCallbacks] default to `null`, meaning "not in search mode" — the bar
 * this file has always drawn. Defaulted so every existing call site and all committed Roborazzi
 * goldens are unaffected by construction — the same contract ReadingToolbar documents at :219-223
 * for its own search parameters. When both are non-null, search mode REPLACES the whole bar
 * ([AbSearchTopAppBar]) rather than augmenting it: [title], [onNavigateUp] and [actions] are all
 * ignored in that case, not just visually superseded — [searchActions] is the one slot the search
 * branch does render, and it is separate from [actions] precisely so the two cannot be confused.
 * A caller that also wants to suppress them itself while search is active is being redundant with
 * this contract, not disagreeing with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbTopAppBar(
    title: @Composable () -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    search: AbTopBarSearchState? = null,
    searchCallbacks: AbTopBarSearchCallbacks? = null,
    searchActions: @Composable RowScope.() -> Unit = {},
) {
    // A/B batch 3 F1: every non-reading Compose screen draws this bar in the M3 small-top-app-bar
    // container colour. Since 2026-09-18 the bar itself insets for the system bars (the Activity
    // content frame no longer does -- see the host-inset-ownership spec, section 3.2, and this
    // file's TopAppBar windowInsets sites), so fillWindowBackground = true asks the host to fill the
    // strip the bars sit over with the same colour, and to set the icon appearance from its
    // luminance.
    val container = TopAppBarDefaults.topAppBarColors().containerColor
    SyncSystemBars(container = container, fillWindowBackground = true)

    if (search != null && searchCallbacks != null) {
        AbSearchTopAppBar(search, searchCallbacks, searchActions)
        return
    }

    TopAppBar(
        windowInsets = topBarWindowInsets(),
        title = title,
        navigationIcon = {
            if (onNavigateUp != null) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = LocalStrings.current.settingsEditorBack,
                        modifier = Modifier.size(AbActionIconSize),
                    )
                }
            }
        },
        actions = actions,
        // The M3 default gives the title `onSurface` but the action and navigation icons
        // `onSurfaceVariant`, so one bar drew its own contents in two colours. The search branch
        // already overrides this deliberately (see AbSearchTopAppBar); this is the same fix for the
        // normal branch. The CONTAINER colour is left at the M3 default, which `container` above
        // already reads for SyncSystemBars.
        colors = TopAppBarDefaults.topAppBarColors(
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        // The Compose hosts do NOT inset their content frame -- they set disableBaseSetupUi = true
        // and call applyComposeHostWindowSetup(), which omits ActivityBase's content-root padding on
        // purpose (host-inset-ownership spec, 2026-09-18). So this bar applies Material's real
        // window insets. Zeroing them here, as this line did until 2026-09-18, would put the bar
        // under the status bar; adding the host padding back would double it. The two go together.
    )
}

/**
 * The bar in search mode: a back arrow that leaves search, a bare text field, and a clear button.
 *
 * The field is deliberately NOT [AbSearchField]. That is an `OutlinedTextField` whose M3 minimum
 * height equals the app bar's own height, so its outline lands flush on both bar edges, and its
 * colours come from M3's field defaults rather than the bar's content colour. Both were diagnosed
 * on the reading toolbar (`ReadingToolbar.kt:342-355`) — in an app bar the bar IS the container, so
 * this is a bare field whose every colour derives from the bar's content colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AbSearchTopAppBar(
    search: AbTopBarSearchState,
    callbacks: AbTopBarSearchCallbacks,
    searchActions: @Composable RowScope.() -> Unit = {},
) {
    val s = LocalStrings.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val onContainer = TopAppBarDefaults.topAppBarColors().titleContentColor

    // Keyed on the instruction so the ack (which returns it to null) arms the next edge; a level
    // would not fire twice for two consecutive requests. `show()` follows `requestFocus()`
    // belt-and-braces — focusing a field usually raises the IME, and this repo's findings F23/F24
    // are its record of "usually" not being good enough.
    LaunchedEffect(search.imeRequest) {
        when (search.imeRequest) {
            null -> return@LaunchedEffect
            AbSearchImeRequest.Focus -> { focusRequester.requestFocus(); keyboard?.show() }
            AbSearchImeRequest.Release -> { focusManager.clearFocus(); keyboard?.hide() }
        }
        callbacks.onImeRequestHandled()
    }

    // Leaving search mode removes this whole bar from composition, so there is no Release edge to
    // deliver — and a focused field going away does not reliably take the IME with it (findings
    // F23/F24 again). Hide it on dispose so every consumer gets the release for free.
    DisposableEffect(Unit) {
        onDispose { keyboard?.hide() }
    }

    // The hoisted `search.query` can arrive a frame late (the host publishes it through
    // combine/stateIn on a non-immediate Main dispatcher). A String-valued BasicTextField then
    // recomposes against the STALE text, and TextFieldValue's constructor clamps the selection to
    // that text's length — the caret jumps to 0 and the next character lands in front (F44/B2,
    // diagnosed on ReadingToolbar.kt's identical field). Owning the TextFieldValue here makes the
    // field correct no matter how many hops the hoisted value takes. An external change (seeded
    // query, clear button) is adopted with the caret at the end; an echo of our own edit —
    // fieldValue's text already matches search.query — is left alone, selection intact.
    var fieldValue by remember { mutableStateOf(TextFieldValue(search.query, TextRange(search.query.length))) }
    if (fieldValue.text != search.query) {
        fieldValue = TextFieldValue(search.query, TextRange(search.query.length))
    }

    CompositionLocalProvider(LocalContentColor provides onContainer) {
        TopAppBar(
            windowInsets = topBarWindowInsets(),
            title = {
                BasicTextField(
                    value = fieldValue,
                    onValueChange = {
                        fieldValue = it
                        callbacks.onQueryChange(it.text)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = onContainer),
                    cursorBrush = SolidColor(onContainer),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                if (search.query.isEmpty()) {
                                    Text(
                                        search.placeholder ?: s.searchHint,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = onContainer.copy(alpha = 0.6f),
                                    )
                                }
                                innerTextField()
                            }
                        }
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = callbacks.onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = s.searchClose,
                        modifier = Modifier.size(AbActionIconSize),
                    )
                }
            },
            actions = {
                // Caller-supplied actions first, so the built-in Clear stays the edge-most action
                // whether or not the caller contributes any (Round 9a Plan A Task 1).
                searchActions()
                if (search.query.isNotEmpty()) {
                    AbActionIcon(Icons.Filled.Clear, s.searchClear) { callbacks.onQueryChange("") }
                }
            },
            // M3's TopAppBar re-provides content colour per slot from its own `colors`, so the outer
            // CompositionLocalProvider above does NOT reach navigationIcon/actions (it only colours
            // the placeholder text drawn directly in the title slot). Left at the default,
            // actionIconContentColor resolves to onSurfaceVariant while navigationIconContentColor/
            // titleContentColor resolve to onSurface — a different token, so the Clear icon would
            // render in a visibly different shade from the back arrow and field text. Pass all three
            // explicitly so every visible colour in this bar derives from the same onContainer.
            colors = TopAppBarDefaults.topAppBarColors(
                navigationIconContentColor = onContainer,
                titleContentColor = onContainer,
                actionIconContentColor = onContainer,
            ),
            // The Compose hosts do NOT inset their content frame -- they set disableBaseSetupUi =
            // true and call applyComposeHostWindowSetup(), which omits ActivityBase's content-root
            // padding on purpose (host-inset-ownership spec, 2026-09-18). So this bar applies
            // Material's real window insets. Zeroing them here, as this line did until 2026-09-18,
            // would put the bar under the status bar; adding the host padding back would double it.
            // The two go together.
        )
    }
}

/**
 * The content insets every app scaffold uses: Material's default (system bars) plus the IME (fix
 * batch 2 §2.1.2). On an edge-to-edge window (the nav host from API 35) `adjustResize` does not shrink
 * the content, so without the IME here a text field in the lower part of a screen stays under the
 * keyboard and cannot scroll into view. Where the decor still fits system windows (API < 35
 * non-reading destinations) Compose sees ime = 0 and this adds nothing. Not used by the reading
 * destination, which owns its IME lift itself (`ReadingViewScreen.imeBottomPadding`).
 */
@Composable
fun abScaffoldContentInsets(): WindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime)

/**
 * Scaffold + a simple string-title top app bar. Backward-compatible with the Batch 1 call sites.
 *
 * [search] / [searchCallbacks] default to `null` (see [AbTopAppBar]); `content` stays the LAST
 * parameter so every existing trailing-lambda call site is unaffected.
 */
@Composable
fun AbScaffold(
    title: String,
    onNavigateUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    search: AbTopBarSearchState? = null,
    searchCallbacks: AbTopBarSearchCallbacks? = null,
    searchActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            AbTopAppBar(
                title = { AbTopBarTitle(title) },
                onNavigateUp = onNavigateUp,
                actions = actions,
                search = search,
                searchCallbacks = searchCallbacks,
                searchActions = searchActions,
            )
        },
        // A bottom bar here is Scaffold-managed on purpose: it reserves space in the content
        // PaddingValues, which is what makes the screen's scrolling content stop ABOVE the bar --
        // classic's layout_above="@+id/transportWidget" (speak_bible.xml:28, speak_settings.xml:30).
        bottomBar = bottomBar,
        // The Compose hosts do NOT inset their content frame -- they set disableBaseSetupUi = true
        // and call applyComposeHostWindowSetup(), which omits ActivityBase's content-root padding on
        // purpose (host-inset-ownership spec, 2026-09-18). So this Scaffold applies Material's real
        // window insets. Zeroing them here, as this line did until 2026-09-18, would put the content
        // under the status bar; adding the host padding back would double it. The two go together.
        contentWindowInsets = abScaffoldContentInsets(),
        content = content,
    )
}

/** Scaffold with a fully custom top bar (e.g. a clickable two-line title). */
@Composable
fun AbScaffold(
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = topBar,
        // The Compose hosts do NOT inset their content frame -- they set disableBaseSetupUi = true
        // and call applyComposeHostWindowSetup(), which omits ActivityBase's content-root padding on
        // purpose (host-inset-ownership spec, 2026-09-18). So this Scaffold applies Material's real
        // window insets. Zeroing them here, as this line did until 2026-09-18, would put the content
        // under the status bar; adding the host padding back would double it. The two go together.
        contentWindowInsets = abScaffoldContentInsets(),
        content = content,
    )
}
