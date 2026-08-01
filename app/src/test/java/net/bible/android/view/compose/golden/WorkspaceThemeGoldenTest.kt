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

package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.reading.ReadingToolbar
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.settings.AbSettingsScreen
import net.bible.sharedui.theme.AbTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A/B batch 4b Task 7: rendered evidence for the workspace-colour Material 3 seed
 * ([AbTheme]'s `seedArgb`, Task 1) and for the reading toolbar's two colour variants
 * ([ToolbarState.deriveToolbarFromTheme], Task 4).
 *
 * [GoldenHarness]'s own `capture()` (used by [captureGolden]/[captureMatrix]) always wraps its
 * content in an UNSEEDED `AbTheme` (`seedArgb` defaults to `null`) — exactly why every existing
 * golden in this module stays byte-identical to before batch 4b. These tests therefore nest
 * their OWN `AbTheme(seedArgb = ...)` inside the `content` lambda passed to [captureGolden]; the
 * inner `AbTheme` simply supersedes the harness's outer (unseeded) one for everything inside it,
 * the same way a real host (`AbAppTheme`) supersedes it in the running app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WorkspaceThemeGoldenTest {

    // A distinctly-non-default orange workspace colour, used as both the AbTheme seed and (for
    // the toolbar pair) the literal workspaceColorArgb, so the two toolbar captures differ ONLY
    // in deriveToolbarFromTheme -- not in which colour is involved.
    private val seed = 0xFFFF8000.toInt()

    // --- Seeded scheme: a settings screen, the densest source of buttons/switches --------------

    // A hand-built SettingsScreenState covering every row type AbSettingsScreen renders
    // (Category/SwitchRow incl. a disabled one/NavigationRow/ListChoiceRow/SliderRow), so the
    // seeded scheme's effect on container colours, text contrast and control states is visible
    // across the widest practical variety of widgets in one capture.
    private fun settingsState() = SettingsScreenState(
        title = "Display settings",
        items = listOf(
            SettingsItem.Category(key = "cat_general", title = "General"),
            SettingsItem.SwitchRow(key = "sw_night", title = "Night mode", summary = "Follow system", checked = true),
            SettingsItem.SwitchRow(key = "sw_fullscreen", title = "Fullscreen", checked = false),
            SettingsItem.SwitchRow(key = "sw_disabled", title = "Disabled toggle", checked = true, enabled = false),
            SettingsItem.NavigationRow(key = "nav_fonts", title = "Fonts", summary = "Choose reading fonts"),
            SettingsItem.ListChoiceRow(
                key = "lc_strongs",
                title = "Strong's numbers",
                summary = "On",
                entries = listOf(SettingsItem.Choice("on", "On"), SettingsItem.Choice("off", "Off")),
                selectedValue = "on",
            ),
            SettingsItem.SliderRow(key = "sl_speed", title = "Speak speed", value = 150, min = 0, max = 300, valueLabel = "150 %"),
            SettingsItem.Category(key = "cat_advanced", title = "Advanced"),
            SettingsItem.NavigationRow(key = "nav_backup", title = "Backup & restore"),
        ),
    )

    private fun seededSettingsScreen(dark: Boolean): @Composable () -> Unit = {
        AbTheme(seedArgb = seed, darkTheme = dark, disableAnimations = true) {
            AbSettingsScreen(
                state = settingsState(),
                onUp = {},
                onSwitch = { _, _ -> },
                onListChoice = { _, _ -> },
                onTextInput = { _, _ -> },
                onNavigate = {},
            )
        }
    }

    @Test
    fun seeded_light() =
        captureGolden("WorkspaceTheme", "seeded", EDGE_MODE, heightDp = 900, content = seededSettingsScreen(dark = false))

    @Test
    fun seeded_dark() =
        captureGolden("WorkspaceTheme", "seeded", GoldenMode.DARK, heightDp = 900, content = seededSettingsScreen(dark = true))

    // --- Toolbar literal vs. derived: same seed, same workspaceColorArgb, differ ONLY in --------
    // --- deriveToolbarFromTheme -------------------------------------------------------------------

    // Same drawables ReadingToolbarGoldenTest uses (main_bible_view.xml's toolbarLayout buttons).
    @Composable
    private fun icons() = ReadingToolbarIcons(
        home = painterResource(R.drawable.ic_menu),
        search = painterResource(R.drawable.ic_search_24dp),
        speak = painterResource(R.drawable.ic_baseline_headphones_24),
        strongs = painterResource(R.drawable.ic_strongs_hebrew),
        bible = painterResource(R.drawable.ic_bible_24dp),
        commentary = painterResource(R.drawable.ic_commentary),
        workspace = painterResource(R.drawable.ic_workspace_solid_24dp),
        overflow = painterResource(R.drawable.ic_more_vert_black_24dp),
    )

    private val noopCallbacks = ReadingToolbarCallbacks(
        onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
        onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
        onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {}, onSpeak = {},
        onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
    )

    private val toolbarState = ToolbarState(
        pageTitle = "Genesis 1:1-3",
        documentTitle = "King James Version (KJV)",
        syncRunning = false,
        showBible = true,
        showCommentary = false,
        showStrongs = false,
        strongsMode = 1,
        searchable = true,
        speakable = false,
        speakStopped = true,
        workspaceColorArgb = seed,
    )

    // Both toolbar captures render inside the SAME seeded AbTheme (light, seed) and the SAME
    // workspaceColorArgb -- the only difference is deriveToolbarFromTheme, so the pair is a clean
    // literal-vs-derived comparison (§6): literal takes workspaceColorArgb directly via
    // readingToolbarContainerArgb, derived takes MaterialTheme.colorScheme.primaryContainer /
    // onPrimaryContainer from this same seeded scheme.
    private fun toolbarScreen(derive: Boolean): @Composable () -> Unit = {
        AbTheme(seedArgb = seed, darkTheme = false, disableAnimations = true) {
            ReadingToolbar(
                state = toolbarState.copy(deriveToolbarFromTheme = derive),
                icons = icons(),
                callbacks = noopCallbacks,
            )
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun toolbar_literal() =
        captureGolden("WorkspaceTheme", "toolbar_literal", EDGE_MODE, content = toolbarScreen(derive = false))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun toolbar_derived() =
        captureGolden("WorkspaceTheme", "toolbar_derived", EDGE_MODE, content = toolbarScreen(derive = true))
}
