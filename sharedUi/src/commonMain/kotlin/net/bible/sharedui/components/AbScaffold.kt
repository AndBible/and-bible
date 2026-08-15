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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.SyncSystemBars

/**
 * Reusable Material3 top app bar: a title slot, optional up-navigation, and trailing actions.
 *
 * Round 6. [search] / [searchCallbacks] default to `null`, meaning "not in search mode" — the bar
 * this file has always drawn. Defaulted so every existing call site and all committed Roborazzi
 * goldens are unaffected by construction — the same contract ReadingToolbar documents at :219-223
 * for its own search parameters. When both are non-null, search mode REPLACES the whole bar
 * ([AbSearchTopAppBar]) rather than augmenting it: [title], [onNavigateUp] and [actions] are all
 * ignored in that case, not just visually superseded. A caller that also wants to suppress them
 * itself while search is active is being redundant with this contract, not disagreeing with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbTopAppBar(
    title: @Composable () -> Unit,
    onNavigateUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    search: AbTopBarSearchState? = null,
    searchCallbacks: AbTopBarSearchCallbacks? = null,
) {
    // A/B batch 3 F1: every non-reading Compose screen draws this bar in the M3 small-top-app-bar
    // container colour but does NOT paint behind the system bars (the Activity content frame is
    // already inset — see the windowInsets = WindowInsets(0,0,0,0) note below). fillWindowBackground
    // = true asks the host to fill the strip the bars sit over with the same colour, and to set the
    // icon appearance from its luminance.
    val container = TopAppBarDefaults.topAppBarColors().containerColor
    SyncSystemBars(container = container, fillWindowBackground = true)

    if (search != null && searchCallbacks != null) {
        AbSearchTopAppBar(search, searchCallbacks)
        return
    }

    TopAppBar(
        title = title,
        navigationIcon = {
            if (onNavigateUp != null) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(AbActionIconSize),
                    )
                }
            }
        },
        actions = actions,
        // F2: the Compose hosts run inside an AppCompatActivity (ActivityBase) whose content
        // frame already insets for the status bar (like the classic View screens). The M3
        // default here would add the status-bar inset a SECOND time → the bar sat one bar-
        // height too low. Zero the M3 inset so the single AppCompat/system inset positions it.
        windowInsets = WindowInsets(0, 0, 0, 0),
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

    CompositionLocalProvider(LocalContentColor provides onContainer) {
        TopAppBar(
            title = {
                BasicTextField(
                    value = search.query,
                    onValueChange = callbacks.onQueryChange,
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
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
    }
}

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
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            AbTopAppBar(
                title = { Text(title) },
                onNavigateUp = onNavigateUp,
                actions = actions,
                search = search,
                searchCallbacks = searchCallbacks,
            )
        },
        // A bottom bar here is Scaffold-managed on purpose: it reserves space in the content
        // PaddingValues, which is what makes the screen's scrolling content stop ABOVE the bar --
        // classic's layout_above="@+id/transportWidget" (speak_bible.xml:28, speak_settings.xml:30).
        bottomBar = bottomBar,
        // F2: see AbTopAppBar — the AppCompat host frame provides the system insets, so the
        // Scaffold must not add them again (would double the top/bottom gap).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
        // F2: see the string-title overload — avoid double system insets under the AppCompat host.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}
