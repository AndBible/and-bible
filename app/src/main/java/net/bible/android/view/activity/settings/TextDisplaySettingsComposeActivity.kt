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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.android.view.activity.page.MainBibleActivity.Companion.COLORS_CHANGED
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.KEY_OPEN_GLOBAL_SETTINGS
import net.bible.sharedcore.settings.KEY_OPEN_WORKSPACE_SETTINGS
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsLabels
import net.bible.sharedcore.settings.TextDisplaySettingsScreenState
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.settings.TextDisplaySettingsScreen
import net.bible.sharedui.settings.TextDisplaySettingsScreenLabels
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

/**
 * Compose host for the text-display-settings screen — the new-path twin of classic
 * [TextDisplaySettingsActivity]/[TextDisplaySettingsFragment] (`R.xml.text_display_settings`).
 *
 * Unlike the other Batch 12d-A Compose settings hosts, this one is a SINGLE Activity that
 * internally implements the WINDOW → WORKSPACE → GLOBAL drill-up navigation classic did with
 * `onNewIntent`/`bundleStack` re-launches of the same Activity: [navStack] holds the stack of
 * [SettingsScope]s visited so far (GridChoosePassage-style internal step flow — see
 * [net.bible.android.view.activity.navigation.GridChoosePassageComposeActivity]), the top of the
 * stack is what's rendered, [BackHandler] pops one level (or [finish]es at the root), and each
 * scope gets its own persistent [TextDisplaySettingsController] instance (created once, cached in
 * [controllerCache] — NOT re-created every recomposition, so `collectAsState()` never loses its
 * subscription, and so [openColors]/[openHideLabels]/[onActivityResult] — which run outside
 * Composable context — can still resolve "the controller for scope X").
 *
 * Two more classic paths are bridged from here (interim, Plan A):
 * - COLORS: launches the STILL-classic [ColorSettingsActivity] (via [service]'s
 *   `colorsBundleJson`/`applyColorsResult` helpers), exactly like classic
 *   `TextDisplaySettingsActivity.onActivityResult(COLORS_CHANGED)`. Plan B replaces this with an
 *   internal Compose colours destination.
 * - BOOKMARKS_HIDELABELS: launches [Screen.ManageLabels] (old or new, via [ScreenLauncher]),
 *   reproducing classic `HideLabelsPreference.openDialog`'s payload + the
 *   `windowRepository.workspaceSettings.updateFrom(data)` recent-labels side-effect.
 */
class TextDisplaySettingsComposeActivity : ActivityBase() {
    // The CONCRETE service (not the `TextDisplaySettingsService` interface) — needed for the
    // Plan-A-only COLORS/HIDELABELS bridge helpers, which aren't part of the portable interface.
    private val service: TextDisplaySettingsServiceImpl by inject()

    private val windowRepository get() = CommonUtils.windowControl.windowRepository

    private val controllerLabels by lazy { buildControllerLabels() }
    private val screenLabels by lazy { buildScreenLabels() }

    /** One [TextDisplaySettingsController] per visited [SettingsScope], kept alive for the life of
     * the Activity so a pop back to an earlier scope reuses (and [refresh]es) the same instance
     * rather than losing edits made while drilled deeper. */
    private val controllerCache = mutableMapOf<SettingsScope, TextDisplaySettingsController>()

    private var navStack by mutableStateOf<List<SettingsScope>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialScope = scopeFromIntent(intent)
        navStack = listOf(initialScope)

        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    BackHandler { pop() }

                    val scope = navStack.last()
                    val controller = controllerFor(scope)
                    val state by controller.state.collectAsState()

                    TextDisplaySettingsScreen(
                        state = state,
                        dialogLabels = screenLabels,
                        badgeFor = { key -> badgeLabel(state, key) },
                        onUp = { pop() },
                        onSwitch = controller::onSwitch,
                        onListChoice = controller::onListChoice,
                        onNumericChange = controller::onNumericChange,
                        onMarginsChange = controller::onMarginsChange,
                        onRevert = controller::onRevert,
                        onReset = controller::onReset,
                        onNavigate = { key -> onNavigate(scope, key) },
                    )
                }
            }
        }

        // EXTRA_START_DESTINATION == "colors": jump straight into the Plan-A colours bridge (e.g. a
        // caller that wants "edit colours for this scope" without showing the text-options list
        // first) — same target scope, the list is still there underneath once colours close.
        if (intent.getStringExtra(EXTRA_START_DESTINATION) == "colors") {
            openColors(initialScope)
        }
    }

    // --- Internal drill-up nav stack ------------------------------------------------------------

    private fun pop() {
        if (navStack.size > 1) {
            navStack = navStack.dropLast(1)
            // Mirrors classic TextDisplaySettingsActivity.onBackPressed's refreshFromInMemoryState:
            // edits made at the deeper scope may have changed what the shallower scope inherits.
            controllerFor(navStack.last()).refresh()
        } else {
            finish()
        }
    }

    private fun controllerFor(scope: SettingsScope): TextDisplaySettingsController =
        controllerCache.getOrPut(scope) {
            TextDisplaySettingsController(
                service = service,
                settingsScope = scope,
                labels = controllerLabels,
                onNavigateCallback = { key -> onNavigate(scope, key) },
            )
        }

    private fun onNavigate(scope: SettingsScope, key: String) {
        when (key) {
            // The workspace link only appears at WINDOW scope; the host always edits the active
            // workspace, so its id is windowRepository.id (matches the Window scope's workspaceId).
            KEY_OPEN_WORKSPACE_SETTINGS -> navStack = navStack + SettingsScope.Workspace(windowRepository.id.toString())
            KEY_OPEN_GLOBAL_SETTINGS -> navStack = navStack + SettingsScope.Global
            TextSettingType.COLORS.name -> openColors(scope)
            TextSettingType.BOOKMARKS_HIDELABELS.name -> openHideLabels(scope)
        }
    }

    private fun badgeLabel(state: TextDisplaySettingsScreenState, key: String): String? =
        runCatching { TextSettingType.valueOf(key) }.getOrNull()
            ?.let { state.rows[it]?.inheritedFrom }
            ?.let {
                when (it) {
                    InheritedFrom.WORKSPACE -> getString(R.string.text_options_inherited_workspace)
                    InheritedFrom.GLOBAL -> getString(R.string.text_options_inherited_global)
                    InheritedFrom.NONE -> null
                }
            }

    // --- COLORS bridge (Plan A; classic ColorSettingsActivity) ----------------------------------

    private fun openColors(scope: SettingsScope) {
        val intent = Intent(this, ColorSettingsActivity::class.java)
        intent.putExtra("settingsBundle", service.colorsBundleJson(scope))
        startActivityForResult(intent, COLORS_CHANGED)
    }

    /** Mirrors classic `TextDisplaySettingsActivity.onActivityResult`'s COLORS_CHANGED branch. */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == COLORS_CHANGED && resultCode == Activity.RESULT_OK) {
            val extras = data?.extras
            if (extras != null) {
                // ColorSettingsActivity always returns to the scope it was opened for -- the user
                // cannot drive this Activity's own nav stack while it's in the foreground.
                val scope = navStack.last()
                service.applyColorsResult(scope, colorsJson = extras.getString("colors"), reset = extras.getBoolean("reset"))
                controllerFor(scope).refresh()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // --- BOOKMARKS_HIDELABELS bridge (reproduces classic HideLabelsPreference.openDialog) -------

    private fun openHideLabels(scope: SettingsScope) {
        val controller = controllerFor(scope)
        val intent = ScreenLauncher.intentFor(this, Screen.ManageLabels)
        intent.putExtra(
            "data",
            ManageLabels.ManageLabelsData(
                mode = ManageLabels.Mode.HIDELABELS,
                selectedLabels = service.currentHideLabelsIds(scope).toMutableSet(),
                isWindow = scope is SettingsScope.Window,
            ).applyFrom(windowRepository.workspaceSettings).toJSON(),
        )
        lifecycleScope.launch(Dispatchers.Main) {
            val result = awaitIntent(intent)
            if (result.resultCode == Activity.RESULT_OK) {
                val resultData = ManageLabels.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
                if (resultData.reset) {
                    controller.onRevert(TextSettingType.BOOKMARKS_HIDELABELS.name)
                } else {
                    windowRepository.workspaceSettings.updateFrom(resultData)
                    controller.onHideLabelsChange(resultData.selectedLabels.map { it.toString() })
                }
            }
        }
    }

    // --- Labels ----------------------------------------------------------------------------------

    private fun buildControllerLabels() = TextDisplaySettingsLabels(
        categoryParent = getString(R.string.parent_settings_category_title),
        categoryFontColors = getString(R.string.prefs_font_and_colors_title),
        categoryTextLayout = getString(R.string.prefs_text_layout_title),
        categoryStrongsMorphology = getString(R.string.prefs_strongs_and_morphology_title),
        categoryFootnotesXrefs = getString(R.string.prefs_footnotes_and_xrefs_title),
        categoryVersesHeadings = getString(R.string.prefs_verses_and_headings_title),
        categoryPageScrolling = getString(R.string.prefs_page_scrolling_title),
        categoryBookmarks = getString(R.string.prefs_text_bookmarks_title),
        categoryReadingMemorization = getString(R.string.prefs_reading_and_memorization_title),
        // Contains "%s" -- left un-substituted here; the controller does its own .replace("%s", ...).
        workspaceLinkTitleFormat = getString(R.string.workspace_text_options_link),
        workspaceLinkSummary = getString(R.string.workspace_text_options_link_summary),
        globalLinkTitle = getString(R.string.global_text_options_link),
        globalLinkSummary = getString(R.string.global_text_options_link_summary),
        badgeWorkspace = getString(R.string.text_options_inherited_workspace),
        badgeGlobal = getString(R.string.text_options_inherited_global),
        titles = mapOf(
            TextSettingType.COLORS to getString(R.string.prefs_text_colors_menutitle),
            TextSettingType.FONTSIZE to getString(R.string.font_size_title),
            TextSettingType.FONTFAMILY to getString(R.string.pref_font_family_label),
            TextSettingType.LINE_SPACING to getString(R.string.line_spacing_title),
            TextSettingType.REDLETTERS to getString(R.string.prefs_red_letter_title),
            TextSettingType.MARGINSIZE to getString(R.string.prefs_margin_size_title),
            TextSettingType.TOPMARGIN to getString(R.string.prefs_top_margin_title),
            TextSettingType.JUSTIFY to getString(R.string.prefs_justify_title),
            TextSettingType.HYPHENATION to getString(R.string.prefs_hyphenation_title),
            TextSettingType.VERSEPERLINE to getString(R.string.prefs_verse_per_line_title),
            TextSettingType.STRONGS to getString(R.string.prefs_show_strongs_title),
            TextSettingType.MORPH to getString(R.string.prefs_show_morphology_title),
            TextSettingType.NON_STRONGS_WORD_ITALIC to getString(R.string.prefs_non_strongs_word_italic_title),
            TextSettingType.FOOTNOTES to getString(R.string.prefs_show_footnotes_title),
            TextSettingType.FOOTNOTES_INLINE to getString(R.string.prefs_show_footnotes_inline_title),
            TextSettingType.XREFS to getString(R.string.prefs_show_xrefs_title),
            TextSettingType.EXPAND_XREFS to getString(R.string.prefs_expand_footnotes_title),
            TextSettingType.VERSENUMBERS to getString(R.string.prefs_show_verseno_title),
            TextSettingType.SECTIONTITLES to getString(R.string.prefs_section_title_title),
            TextSettingType.TITLE_SCROLL_BUTTON to getString(R.string.prefs_title_scroll_button_title),
            TextSettingType.PAGENUMBER to getString(R.string.page_number_title),
            TextSettingType.INFINITE_SCROLL to getString(R.string.prefs_infinite_scroll_title),
            TextSettingType.PAGE_SCROLL_AMOUNT to getString(R.string.prefs_page_scroll_amount_title),
            TextSettingType.SCROLL_HELPER_LINES to getString(R.string.prefs_scroll_helper_lines_title),
            TextSettingType.SCROLL_HELPER_LINE_STYLE to getString(R.string.prefs_scroll_helper_line_style_title),
            TextSettingType.PAGE_BUTTONS to getString(R.string.prefs_page_buttons_title),
            TextSettingType.ORDINALS to getString(R.string.prefs_show_ordinals_title),
            TextSettingType.SHOW_READING_PROGRESS to getString(R.string.prefs_show_reading_progress_title),
            TextSettingType.BOOKMARKS_SHOW to getString(R.string.prefs_show_bookmarks_title),
            TextSettingType.MYNOTES to getString(R.string.prefs_show_mynotes_title),
            TextSettingType.AI_DOC_MARKERS to getString(R.string.prefs_show_ai_doc_markers_title),
            TextSettingType.BOOKMARKS_HIDELABELS to getString(R.string.bookmark_settings_hide_labels_title),
            TextSettingType.MARK_AS_READ_BUTTON to getString(R.string.prefs_mark_as_read_button_title),
            TextSettingType.MEMORIZATION_INDICATORS to getString(R.string.prefs_show_memorization_indicators_title),
            TextSettingType.AUTO_TRACK_READING to getString(R.string.prefs_auto_track_reading_title),
        ),
        summaries = mapOf(
            TextSettingType.COLORS to getString(R.string.prefs_text_colors_summary),
            TextSettingType.FONTSIZE to getString(R.string.prefs_font_text_size_summary),
            TextSettingType.FONTFAMILY to getString(R.string.prefs_font_family_summary),
            TextSettingType.LINE_SPACING to getString(R.string.line_spacing_summary),
            TextSettingType.REDLETTERS to getString(R.string.prefs_red_letter_summary),
            TextSettingType.MARGINSIZE to getString(R.string.prefs_margin_size_summary),
            TextSettingType.TOPMARGIN to getString(R.string.prefs_top_margin_summary),
            TextSettingType.JUSTIFY to getString(R.string.prefs_justify_summary),
            TextSettingType.HYPHENATION to getString(R.string.prefs_hyphenation_summary),
            TextSettingType.VERSEPERLINE to getString(R.string.prefs_verse_per_line_summary),
            TextSettingType.STRONGS to getString(R.string.prefs_show_strongs_summary),
            TextSettingType.MORPH to getString(R.string.prefs_show_morphology_summary),
            TextSettingType.NON_STRONGS_WORD_ITALIC to getString(R.string.prefs_non_strongs_word_italic_summary),
            TextSettingType.FOOTNOTES to getString(R.string.prefs_show_footnotes_summary),
            TextSettingType.FOOTNOTES_INLINE to getString(R.string.prefs_show_footnotes_inline_summary),
            TextSettingType.XREFS to getString(R.string.prefs_show_xrefs_summary),
            TextSettingType.EXPAND_XREFS to getString(R.string.prefs_expand_footnotes_summary),
            TextSettingType.VERSENUMBERS to getString(R.string.prefs_show_verseno_summary),
            TextSettingType.SECTIONTITLES to getString(R.string.prefs_section_title_summary),
            TextSettingType.TITLE_SCROLL_BUTTON to getString(R.string.prefs_title_scroll_button_summary),
            TextSettingType.PAGENUMBER to getString(R.string.page_number_summary),
            TextSettingType.INFINITE_SCROLL to getString(R.string.prefs_infinite_scroll_summary),
            TextSettingType.PAGE_SCROLL_AMOUNT to getString(R.string.prefs_page_scroll_amount_summary),
            TextSettingType.SCROLL_HELPER_LINES to getString(R.string.prefs_scroll_helper_lines_summary),
            TextSettingType.SCROLL_HELPER_LINE_STYLE to getString(R.string.prefs_scroll_helper_line_style_summary),
            TextSettingType.PAGE_BUTTONS to getString(R.string.prefs_page_buttons_summary),
            TextSettingType.ORDINALS to getString(R.string.prefs_show_ordinals_summary),
            TextSettingType.SHOW_READING_PROGRESS to getString(R.string.prefs_show_reading_progress_summary),
            TextSettingType.BOOKMARKS_SHOW to getString(R.string.prefs_show_bookmarks_summary),
            TextSettingType.MYNOTES to getString(R.string.prefs_show_mynotes_summary),
            TextSettingType.AI_DOC_MARKERS to getString(R.string.prefs_show_ai_doc_markers_summary),
            TextSettingType.BOOKMARKS_HIDELABELS to getString(R.string.bookmark_settings_hide_labels_summary),
            TextSettingType.MARK_AS_READ_BUTTON to getString(R.string.prefs_mark_as_read_button_summary),
            TextSettingType.MEMORIZATION_INDICATORS to getString(R.string.prefs_show_memorization_indicators_summary),
            TextSettingType.AUTO_TRACK_READING to getString(R.string.prefs_auto_track_reading_summary),
        ),
    )

    private fun buildScreenLabels() = TextDisplaySettingsScreenLabels(
        resetContentDescription = getString(R.string.reset_settings),
        resetConfirmMessage = getString(R.string.reset_are_you_sure),
        // No classic string exists for a single-row "revert to inherited?" confirmation (this
        // long-press interaction is new in the Compose screen -- classic's per-row reset lives
        // inside each value-editor dialog, with no separate confirm step). Reusing the generic
        // bulk-reset confirmation is an accepted, if imprecise, wording (see task report).
        revertMessage = getString(R.string.reset_are_you_sure),
        fontSizeDialogTitle = getString(R.string.font_size_title),
        topMarginDialogTitle = getString(R.string.prefs_top_margin_title),
        lineSpacingDialogTitle = getString(R.string.line_spacing_title),
        marginSizeDialogTitle = getString(R.string.prefs_margin_size_title),
        // Contain "%d" -- left un-substituted; TextDisplaySettingsScreen/MarginDialog does its own
        // .replace("%d", ...).
        marginLeftLabelFormat = getString(R.string.pref_left_margin_label_mm),
        marginRightLabelFormat = getString(R.string.pref_right_margin_label_mm),
        marginMaxWidthLabelFormat = getString(R.string.pref_maximum_width_of_text_label_mm),
        resetToInheritedLabel = getString(R.string.reset_generic),
        badgeWorkspace = getString(R.string.text_options_inherited_workspace),
        badgeGlobal = getString(R.string.text_options_inherited_global),
        okLabel = getString(R.string.okay),
        cancelLabel = getString(R.string.cancel),
    )

    companion object {
        const val EXTRA_START_DESTINATION = "startDestination"   // "text" | "colors"
        const val EXTRA_SCOPE_LEVEL = "scopeLevel"                // "window" | "workspace" | "global"
        const val EXTRA_WINDOW_ID = "windowId"
        const val EXTRA_WORKSPACE_ID = "workspaceId"

        fun intentFor(context: Context, scope: SettingsScope, startDestination: String = "text"): Intent {
            val intent = Intent(context, TextDisplaySettingsComposeActivity::class.java)
            intent.putExtra(EXTRA_START_DESTINATION, startDestination)
            when (scope) {
                is SettingsScope.Window -> {
                    intent.putExtra(EXTRA_SCOPE_LEVEL, "window")
                    intent.putExtra(EXTRA_WINDOW_ID, scope.windowId)
                    intent.putExtra(EXTRA_WORKSPACE_ID, scope.workspaceId)
                }
                is SettingsScope.Workspace -> {
                    intent.putExtra(EXTRA_SCOPE_LEVEL, "workspace")
                    intent.putExtra(EXTRA_WORKSPACE_ID, scope.workspaceId)
                }
                is SettingsScope.Global -> {
                    intent.putExtra(EXTRA_SCOPE_LEVEL, "global")
                }
            }
            return intent
        }
    }
}

/** Reconstructs the initial [SettingsScope] from the extras [TextDisplaySettingsComposeActivity.intentFor] set. */
private fun scopeFromIntent(intent: Intent): SettingsScope = when (intent.getStringExtra(TextDisplaySettingsComposeActivity.EXTRA_SCOPE_LEVEL)) {
    "window" -> SettingsScope.Window(
        windowId = intent.getStringExtra(TextDisplaySettingsComposeActivity.EXTRA_WINDOW_ID)!!,
        workspaceId = intent.getStringExtra(TextDisplaySettingsComposeActivity.EXTRA_WORKSPACE_ID)!!,
    )
    "workspace" -> SettingsScope.Workspace(
        workspaceId = intent.getStringExtra(TextDisplaySettingsComposeActivity.EXTRA_WORKSPACE_ID)!!,
    )
    else -> SettingsScope.Global
}
