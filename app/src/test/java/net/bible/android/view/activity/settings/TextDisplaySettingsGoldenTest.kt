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
package net.bible.android.view.activity.settings

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.android.view.compose.golden.GoldenMode
import net.bible.android.view.compose.golden.captureGolden
import net.bible.android.view.compose.golden.captureMatrix
import net.bible.android.view.compose.golden.captureRtl
import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsLabels
import net.bible.sharedcore.settings.TextDisplaySettingsScreenState
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.settings.TextSettingRow
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import net.bible.sharedcore.settings.TextSettingsSnapshot
import net.bible.sharedui.settings.MarginDialog
import net.bible.sharedui.settings.NumericSliderDialog
import net.bible.sharedui.settings.TextDisplaySettingsScreen
import net.bible.sharedui.settings.TextDisplaySettingsScreenLabels
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for [TextDisplaySettingsScreen] (Batch 12d-A Task 4) — the 9-category text-display
 * settings screen with numeric/margin dialog editors, drill-up parent links, badges, and revert/
 * reset. Renders through the REAL [TextDisplaySettingsController] (a fake [TextDisplaySettingsService]
 * feeds it a hand-built 35-type [TextSettingsSnapshot], mirroring `TextDisplaySettingsControllerTest`'s
 * `FakeService`/`snapshot()` fixture shape).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class TextDisplaySettingsGoldenTest {

    /** Minimal fake: the golden only needs a fixed snapshot, so writes are no-ops. The colours/
     *  background members (Batch 12d-B) aren't exercised by this (text-options-screen) golden --
     *  stubbed just to satisfy the interface. */
    private class FakeService(private val snap: TextSettingsSnapshot) : TextDisplaySettingsService {
        override fun loadText(scope: SettingsScope) = snap
        override fun setValue(scope: SettingsScope, type: TextSettingType, value: TextSettingValue) {}
        override fun revert(scope: SettingsScope, type: TextSettingType) {}
        override fun reset(scope: SettingsScope) {}

        override fun loadColors(scope: SettingsScope) = ColorsSnapshot(
            title = "Colours", dayTextColor = -16777216, dayBackground = -1, dayNoise = 0,
            nightTextColor = -1, nightBackground = -16777216, nightNoise = 0,
            workspaceColor = -12303292, workspaceColorVisible = true,
            dayBackgroundImageInitials = null, dayBackgroundImageName = "None", dayBackgroundImageOpacity = 100,
            nightBackgroundImageInitials = null, nightBackgroundImageName = "None", nightBackgroundImageOpacity = 100,
            inheritedFrom = InheritedFrom.NONE,
        )
        override fun loadBackgroundOptions(): List<BackgroundImageOption> = emptyList()
        override fun setColor(scope: SettingsScope, field: ColorField, argb: Int) {}
        override fun setNoise(scope: SettingsScope, night: Boolean, value: Int) {}
        override fun setWorkspaceColor(scope: SettingsScope, argb: Int) {}
        override fun setBackgroundImage(scope: SettingsScope, night: Boolean, initials: String?) {}
        override fun setBackgroundOpacity(scope: SettingsScope, night: Boolean, opacity: Int) {}
        override fun resetColors(scope: SettingsScope) {}
        override suspend fun importBackgroundImage(): BackgroundImageOption? = null
        override fun deleteBackgroundImage(initials: String) {}
    }

    private fun row(
        t: TextSettingType,
        v: TextSettingRowValue,
        inh: InheritedFrom = InheritedFrom.NONE,
        enabled: Boolean = true,
        visible: Boolean = true,
    ) = TextSettingRow(t, v, inh, enabled, visible)

    /** A full 35-type snapshot with representative row-values for the non-bool types (mirrors
     *  `TextDisplaySettingsControllerTest.snapshot()`). [overrides] replaces individual rows, e.g.
     *  to seed a mix of inheritance states for the "badges" fixture. */
    private fun snapshot(
        scope: SettingsScope = SettingsScope.Workspace("ws"),
        overrides: Map<TextSettingType, TextSettingRow> = emptyMap(),
    ): TextSettingsSnapshot {
        val choice = TextSettingRowValue.Choice("0", listOf(SettingsItem.Choice("0", "Off")))
        val numeric = TextSettingRowValue.Numeric(16, 1, 60, "16 pt")
        val margins = TextSettingRowValue.Margins(3, 3, 170, 30, 30, 500, "3/3/170 mm")
        val rows = TextSettingType.entries.associateWith { t ->
            overrides[t] ?: when (t) {
                TextSettingType.STRONGS, TextSettingType.PAGE_SCROLL_AMOUNT,
                TextSettingType.SCROLL_HELPER_LINE_STYLE -> row(t, choice)
                TextSettingType.FONTFAMILY -> row(t, TextSettingRowValue.Choice("sans-serif", listOf(SettingsItem.Choice("sans-serif", "Sans serif"))))
                TextSettingType.FONTSIZE, TextSettingType.TOPMARGIN, TextSettingType.LINE_SPACING -> row(t, numeric)
                TextSettingType.MARGINSIZE -> row(t, margins)
                TextSettingType.COLORS -> row(t, TextSettingRowValue.ColorsNav("Colours"))
                TextSettingType.BOOKMARKS_HIDELABELS -> row(t, TextSettingRowValue.HideLabels("2 labels hidden"))
                else -> row(t, TextSettingRowValue.Bool(true))
            }
        }
        return TextSettingsSnapshot(
            scope, "Text options", "My workspace", rows,
            showParentCategory = scope !is SettingsScope.Global,
            showWorkspaceLink = scope is SettingsScope.Window,
            showGlobalLink = scope !is SettingsScope.Global,
        )
    }

    private fun testDialogLabels() = TextDisplaySettingsScreenLabels(
        resetContentDescription = "Reset",
        resetConfirmMessage = "Are you sure that you want to reset all of these values?",
        revertMessage = "Revert this setting to the inherited value?",
        fontSizeDialogTitle = "Font size",
        topMarginDialogTitle = "Top margin",
        lineSpacingDialogTitle = "Line spacing",
        marginSizeDialogTitle = "Change margin size",
        marginLeftLabelFormat = "Left margin (%d mm)",
        marginRightLabelFormat = "Right margin (%d mm)",
        marginMaxWidthLabelFormat = "Maximum width of text (%d mm)",
        resetToInheritedLabel = "Reset",
        badgeWorkspace = "Workspace",
        badgeGlobal = "Global",
        okLabel = "OK",
        cancelLabel = "Cancel",
    )

    /** [InheritedFrom.WORKSPACE] -> "Workspace", [InheritedFrom.GLOBAL] -> "Global", else no badge —
     *  the mapping any real host builds from a row's `inheritedFrom` (non-type keys, e.g. the parent
     *  links or category headers, never resolve to a [TextSettingType] and get no badge). */
    private fun badgeLabel(state: TextDisplaySettingsScreenState, key: String): String? {
        val type = runCatching { TextSettingType.valueOf(key) }.getOrNull() ?: return null
        return when (state.rows[type]?.inheritedFrom) {
            InheritedFrom.WORKSPACE -> testDialogLabels().badgeWorkspace
            InheritedFrom.GLOBAL -> testDialogLabels().badgeGlobal
            else -> null
        }
    }

    private fun controllerFor(scope: SettingsScope, overrides: Map<TextSettingType, TextSettingRow> = emptyMap()) =
        TextDisplaySettingsController(
            service = FakeService(snapshot(scope, overrides)),
            settingsScope = scope,
            labels = TextDisplaySettingsLabels.forTest(),
            onNavigateCallback = {},
        )

    private fun screen(scope: SettingsScope, overrides: Map<TextSettingType, TextSettingRow> = emptyMap()): @Composable () -> Unit {
        val controller = controllerFor(scope, overrides)
        return {
            TextDisplaySettingsScreen(
                state = controller.state.value,
                dialogLabels = testDialogLabels(),
                badgeFor = { key -> badgeLabel(controller.state.value, key) },
                onUp = {},
                onSwitch = { _, _ -> },
                onListChoice = { _, _ -> },
                onNumericChange = { _, _ -> },
                onMarginsChange = { _, _, _, _ -> },
                onRevert = {},
                onReset = {},
                onNavigate = {},
            )
        }
    }

    /** A mix of NONE/WORKSPACE/GLOBAL inheritance on the first category's rows (COLORS/FONTSIZE/
     *  FONTFAMILY/LINE_SPACING/REDLETTERS), so the badge chip renders on some rows and not others. */
    private fun badgeScreen(): @Composable () -> Unit {
        val overrides = mapOf(
            TextSettingType.COLORS to row(TextSettingType.COLORS, TextSettingRowValue.ColorsNav("Colours"), InheritedFrom.WORKSPACE),
            TextSettingType.FONTSIZE to row(TextSettingType.FONTSIZE, TextSettingRowValue.Numeric(16, 1, 60, "16 pt"), InheritedFrom.GLOBAL),
            TextSettingType.FONTFAMILY to row(TextSettingType.FONTFAMILY, TextSettingRowValue.Choice("sans-serif", listOf(SettingsItem.Choice("sans-serif", "Sans serif"))), InheritedFrom.NONE),
            TextSettingType.LINE_SPACING to row(TextSettingType.LINE_SPACING, TextSettingRowValue.Numeric(20, 10, 30, "20"), InheritedFrom.WORKSPACE),
            TextSettingType.REDLETTERS to row(TextSettingType.REDLETTERS, TextSettingRowValue.Bool(true), InheritedFrom.GLOBAL),
        )
        return screen(SettingsScope.Workspace("ws"), overrides)
    }

    // heightDp=2400: the full 9-category / 35-row screen (plus the two parent-link rows and the
    // search field) clips heavily at the default viewport; render the whole thing.
    @Test fun full_matrix() =
        captureMatrix("TextDisplaySettings", "full", heightDp = 2400, content = screen(SettingsScope.Workspace("ws")))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun full_rtl() =
        captureRtl("TextDisplaySettings", "full", heightDp = 2400, content = screen(SettingsScope.Workspace("ws")))

    @Test fun window_scope_light() =
        captureGolden("TextDisplaySettings", "window", GoldenMode.LIGHT, heightDp = 2400, content = screen(SettingsScope.Window("w", "ws")))

    @Test fun badges_light() =
        captureGolden("TextDisplaySettings", "badges", GoldenMode.LIGHT, heightDp = 900, content = badgeScreen())

    // --- Numeric/margin dialog editors (review Finding 2, Batch 12d-A T4 fix) ---
    // NumericSliderDialog/MarginDialog are public composables taking explicit params (un-privated
    // from TextDisplaySettingsScreen.kt for exactly this), golden-tested DIRECTLY here the same way
    // AbColorPickerGoldenTest calls AbColorPicker(...) -- no gesture simulation needed, an open
    // AlertDialog tree captures fine as a static render (same technique as AbInfoDialogGoldenTest).
    // Specifically exercises whether Material3 Slider's default `primary`-track color leaks a hue
    // into BW/COLOR_EINK (it shouldn't: AbTheme grayscales the WHOLE base ColorScheme, including
    // `primary`, in BW/COLOR_EINK -- see AbTheme.grayscale -- so the default Slider colors, which are
    // all derived from the theme's ColorScheme, should already come out grayscale with no extra
    // theming). Eyeball the _bw/_eink renders to confirm before trusting that reasoning.

    @Test fun numeric_dialog_matrix() =
        captureMatrix("TextDisplaySettings", "numeric_dialog") {
            NumericSliderDialog(
                title = testDialogLabels().fontSizeDialogTitle,
                numeric = TextSettingRowValue.Numeric(16, 1, 60, "16 pt"),
                okLabel = testDialogLabels().okLabel,
                cancelLabel = testDialogLabels().cancelLabel,
                resetLabel = testDialogLabels().resetToInheritedLabel,
                onConfirm = {},
                onReset = {},
                onDismiss = {},
            )
        }

    @Test fun margin_dialog_matrix() =
        captureMatrix("TextDisplaySettings", "margin_dialog") {
            MarginDialog(
                title = testDialogLabels().marginSizeDialogTitle,
                margins = TextSettingRowValue.Margins(3, 3, 170, 30, 30, 500, "3/3/170 mm"),
                leftLabelFormat = testDialogLabels().marginLeftLabelFormat,
                rightLabelFormat = testDialogLabels().marginRightLabelFormat,
                maxWidthLabelFormat = testDialogLabels().marginMaxWidthLabelFormat,
                okLabel = testDialogLabels().okLabel,
                cancelLabel = testDialogLabels().cancelLabel,
                resetLabel = testDialogLabels().resetToInheritedLabel,
                onConfirm = { _, _, _ -> },
                onReset = {},
                onDismiss = {},
            )
        }
}
