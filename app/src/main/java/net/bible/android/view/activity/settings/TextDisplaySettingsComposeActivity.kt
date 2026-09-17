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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.bible.android.activity.R
import net.bible.android.database.SettingsBundle
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.applyComposeHostWindowSetup
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.updateFrom
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.search.SearchModeController
import net.bible.sharedcore.settings.ColorSettingsController
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.KEY_OPEN_GLOBAL_SETTINGS
import net.bible.sharedcore.settings.KEY_OPEN_WORKSPACE_SETTINGS
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsScreenState
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.settings.BackgroundImageChooserScreen
import net.bible.sharedui.settings.ColorSettingsScreen
import net.bible.sharedui.settings.TextDisplaySettingsScreen
import org.koin.android.ext.android.inject

/**
 * Compose host for the text-display-settings screen — the new-path twin of the classic
 * TextDisplaySettingsActivity/TextDisplaySettingsFragment (`text_display_settings.xml`), all
 * deleted in Z-late slice S12.
 *
 * Unlike the other Batch 12d-A Compose settings hosts, this one is a SINGLE Activity that
 * internally implements the WINDOW → WORKSPACE → GLOBAL drill-up navigation classic did with
 * `onNewIntent`/`bundleStack` re-launches of the same Activity: [navStack] holds the stack of
 * [SettingsScope]s visited so far (GridChoosePassage-style internal step flow — see
 * [net.bible.android.view.activity.navigation.GridChoosePassageComposeActivity]), the top of the
 * stack is what's rendered, [BackHandler] pops one level (or [finish]es at the root), and each
 * scope gets its own persistent [TextDisplaySettingsController] instance (created once, cached in
 * [controllerCache] — NOT re-created every recomposition, so `collectAsState()` never loses its
 * subscription, and so [onNavigate]/[openHideLabels] — which run outside Composable context —
 * can still resolve "the controller for scope X").
 *
 * COLORS opens an internal Compose destination (Batch 12d-B T7): [colorsScope] non-null selects
 * the [ColorSettingsScreen] for that scope, rendered by its own persistent [ColorSettingsController]
 * ([colorControllerFor]); [chooserNight] non-null further drills into [BackgroundImageChooserScreen]
 * for that day/night slot. This retires the earlier interim bridge (Plan A) that launched the
 * classic `ColorSettingsActivity` via `service.colorsBundleJson`/`applyColorsResult`.
 *
 * One classic path is still bridged from here:
 * - BOOKMARKS_HIDELABELS: launches the `ManageLabels` nav-graph destination directly (via
 *   [net.bible.android.view.activity.nav.NavHostComposeActivity.intentFor] +
 *   [net.bible.sharedcore.nav.NavRoutes.manageLabels] -- `Screen.ManageLabels` stays out of
 *   `ScreenLauncher.MIGRATED` since its `data` argument is required, and the classic host
 *   `ScreenLauncher.targetFor` used to resolve it to is gone, nav-graph slices 2+4 Task 7),
 *   reproducing classic `HideLabelsPreference.openDialog`'s payload + the
 *   `windowRepository.workspaceSettings.updateFrom(data)` recent-labels side-effect.
 */
class TextDisplaySettingsComposeActivity : ActivityBase() {
    /**
     * This host's [TextDisplaySettingsScreen] / [ColorSettingsScreen] / [BackgroundImageChooserScreen]
     * destinations all render through `AbSettingsScreen` / `AbScaffold`, which own the system-bar
     * insets -- so this host must not pad its content root too. See the host-inset-ownership spec,
     * section 3.2.
     */
    override val disableBaseSetupUi = true

    /** Non-null only for a selector-originated launch: the in-memory workspace edit this screen
     *  returns to the workspace selector. See spec 11.4 and [DetachedWorkspaceEdit]. */
    private val detachedEdit: DetachedWorkspaceEdit? by lazy {
        intent.getStringExtra(EXTRA_DETACHED_BUNDLE)?.let { DetachedWorkspaceEdit(SettingsBundle.fromJson(it)) }
    }

    // The CONCRETE service (not the `TextDisplaySettingsService` interface) — needed for the
    // HIDELABELS bridge helper, which isn't part of the portable interface.
    private val sharedService: TextDisplaySettingsServiceImpl by inject()

    /** The detached launch builds its OWN service instance -- see [DetachedWorkspaceEdit]'s kdoc for
     *  why this must not be the Koin singleton. */
    private val service: TextDisplaySettingsServiceImpl by lazy {
        detachedEdit?.let { TextDisplaySettingsServiceImpl(it) } ?: sharedService
    }

    private val windowRepository get() = CommonUtils.windowControl.windowRepository

    private val controllerLabels by lazy { buildTextDisplayControllerLabels(this) }
    private val screenLabels by lazy { buildTextDisplayScreenLabels(this) }
    private val colorSettingsLabels by lazy { buildColorSettingsLabels(this) }
    private val backgroundImageChooserLabels by lazy { buildBackgroundImageChooserLabels(this) }

    /** One [TextDisplaySettingsController] per visited [SettingsScope], kept alive for the life of
     * the Activity so a pop back to an earlier scope reuses (and [refresh]es) the same instance
     * rather than losing edits made while drilled deeper. */
    private val controllerCache = mutableMapOf<SettingsScope, TextDisplaySettingsController>()

    private var navStack by mutableStateOf<List<SettingsScope>>(emptyList())

    // Search state is hoisted here (activity-level, NOT inside the composition) so pop()/BackHandler
    // below can reach it — :sharedUi has commonMain only, so TextDisplaySettingsScreen cannot use
    // BackHandler itself.
    private val searchQuery = MutableStateFlow("")
    private val searchMode = SearchModeController(onClearQuery = { searchQuery.value = "" })

    /** Non-null while the internal `colors` destination is shown, holding the [SettingsScope] it
     * was opened for. Null pops back to the text-settings list ([TextDisplaySettingsScreen]). */
    private var colorsScope by mutableStateOf<SettingsScope?>(null)

    /** Non-null while the `chooser` sub-destination ([BackgroundImageChooserScreen]) is shown, for
     * that day (`false`)/night (`true`) slot. Only meaningful while [colorsScope] is non-null. */
    private var chooserNight by mutableStateOf<Boolean?>(null)

    /** True for an [intentForColors] launch, where the colours destination is the ROOT rather than
     * something pushed on top of the text-settings list — so backing out of it must [finish], not
     * reveal a list the user never opened. Classic `ColorPreference.openDialog` launched a separate
     * `ColorSettingsActivity`, whose back went straight to the caller; this preserves that. */
    private var startedAtColors = false

    // Registered eagerly (constructor-time property, like classic BackgroundImageChooserActivity's
    // photoPicker) so it's ready well before RESUMED, whichever destination is showing.
    private var pendingPick: CancellableContinuation<String?>? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pendingPick?.resume(uri?.toString())
        pendingPick = null
    }

    /**
     * This Activity's own photo picker (Batch 12d-B T5/T6/T7, T9): bridges [photoPicker]'s
     * callback-shaped `ActivityResultLauncher` to a suspend fun via [pendingPick]/[CancellableContinuation],
     * handed to each [ColorSettingsController] this Activity builds ([colorControllerFor]) — a
     * PARAMETER, not a property [TextDisplaySettingsServiceImpl] (a Koin singleton) could own itself.
     * See `TextDisplaySettingsService.importBackgroundImage`'s kdoc for why that matters.
     */
    private val imagePicker: suspend () -> String? = {
        suspendCancellableCoroutine { cont ->
            pendingPick = cont
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            cont.invokeOnCancellation { pendingPick = null }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyComposeHostWindowSetup()
        val initialScope = scopeFromIntent(intent)
        navStack = listOf(initialScope)
        // A colours-originated launch (intentForColors) opens straight at the internal `colors`
        // destination for the SAME scope the list would otherwise have shown. Backing out of it
        // finishes rather than revealing that list -- see [startedAtColors] and pop().
        if (intent.getBooleanExtra(EXTRA_START_AT_COLORS, false)) {
            colorsScope = initialScope
            startedAtColors = true
        }

        setContent {
            AbAppTheme {
                    // Fix round 1 (Task 7 review): searchMode is hoisted to ACTIVITY level (see the
                    // field above), so — unlike the old `remember`-in-composable design, which was
                    // disposed the instant the composition navigated away — it now OUTLIVES pushing
                    // the Colors/chooser sub-destinations. Those destinations render no search UI at
                    // all, so `searchMode.active` gate must not fire there: a back press from inside
                    // Colors must always `pop()`. Gating unconditionally on `searchMode.active.value`
                    // would `close()` an invisible search bar and swallow that press entirely (pop()
                    // never runs), leaving the user apparently stuck needing two back presses.
                    // INVARIANT: only close search from the list destination itself (no colorsScope/
                    // chooserNight pushed) — do not widen this to "search active" alone.
                    BackHandler {
                        val atListDestination = colorsScope == null && chooserNight == null
                        if (shouldCloseSearchOnBack(atListDestination, searchMode.active.value)) {
                            searchMode.close()
                        } else {
                            pop()
                        }
                    }

                    val activeColorsScope = colorsScope
                    if (activeColorsScope != null) {
                        // Fresh controller every time the colours destination is (re-)entered -- see
                        // the kdoc on colorControllerFor for why this must NOT be a long-lived cache.
                        val colorController = remember(activeColorsScope) { colorControllerFor(activeColorsScope) }
                        val colorState by colorController.state.collectAsState()
                        val night = chooserNight

                        if (night != null) {
                            BackgroundImageChooserScreen(
                                options = colorState.backgroundOptions,
                                labels = backgroundImageChooserLabels,
                                loading = colorState.loading,
                                deleteConfirm = colorState.deleteConfirm,
                                thumbnailFor = thumbnailResolver::resolve,
                                onUp = { pop() },
                                onSelect = { initials ->
                                    colorController.onSelectBackgroundImage(night, initials)
                                    chooserNight = null
                                },
                                onImport = colorController::onImportBackgroundImage,
                                onRequestDelete = colorController::onRequestDeleteBackgroundImage,
                                onConfirmDelete = colorController::onConfirmDeleteBackgroundImage,
                                onDismissDelete = colorController::onDismissDeleteConfirm,
                            )
                        } else {
                            ColorSettingsScreen(
                                state = colorState,
                                labels = colorSettingsLabels,
                                onUp = { pop() },
                                onReset = colorController::onReset,
                                // Final fix wave, Fix 4: restores the confirm classic always had
                                // (ColorSettings.kt's AlertDialog) but this Compose port never did --
                                // reuses screenLabels verbatim, the SAME resolved strings
                                // TextDisplaySettingsScreen's own reset confirm passes below.
                                resetConfirmMessage = screenLabels.resetConfirmMessage,
                                confirmLabel = screenLabels.okLabel,
                                cancelLabel = screenLabels.cancelLabel,
                                onColorChange = colorController::onColorChange,
                                onNoiseChange = colorController::onNoiseChange,
                                onWorkspaceColorChange = colorController::onWorkspaceColorChange,
                                onOpacityChange = colorController::onOpacityChange,
                                onChangeBackgroundImage = { n -> chooserNight = n },
                            )
                        }
                    } else {
                        val scope = navStack.last()
                        val controller = controllerFor(scope)
                        val state by controller.state.collectAsState()
                        val query by searchQuery.collectAsState()
                        val searchModeActive by searchMode.active.collectAsState()

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
                            searchQuery = query,
                            searchModeActive = searchModeActive,
                            onSearchQueryChange = { searchQuery.value = it },
                            onOpenSearch = searchMode::open,
                            onCloseSearch = searchMode::close,
                        )
                    }
            }
        }
    }

    /**
     * The whole result contract for a selector-originated (detached) launch: returns
     * `settingsBundle` + `reset` -- but only when [DetachedWorkspaceEdit.changed] (plan D3), so
     * merely opening and leaving the screen does not mark the workspace changed on the selector's
     * next Save. Covers every exit path, since [pop]'s `else -> finish()`, the action bar's Up, and
     * the system back all end up here rather than setting a result of their own.
     */
    override fun finish() {
        val edit = detachedEdit
        if (edit != null && edit.changed) {
            setResult(
                Activity.RESULT_OK,
                Intent()
                    .putExtra(EXTRA_DETACHED_BUNDLE, edit.bundle.toJson())
                    .putExtra("reset", edit.reset),
            )
        }
        super.finish()
    }

    // --- Internal drill-up nav stack ------------------------------------------------------------

    private fun pop() {
        when {
            chooserNight != null -> chooserNight = null
            // A colours-originated launch has nothing underneath the colours destination — see
            // [startedAtColors].
            colorsScope != null && startedAtColors -> finish()
            colorsScope != null -> {
                val scope = colorsScope!!
                colorsScope = null
                // Colours are edited via a separate ColorSettingsController, so the cached text
                // controller for this scope may now show a stale COLORS inheritance badge — refresh it.
                controllerFor(scope).refresh()
            }
            navStack.size > 1 -> {
                navStack = navStack.dropLast(1)
                // Mirrors classic TextDisplaySettingsActivity.onBackPressed's refreshFromInMemoryState:
                // edits made at the deeper scope may have changed what the shallower scope inherits.
                controllerFor(navStack.last()).refresh()
            }
            else -> finish()
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

    /** NOT cached across colours-destination visits (unlike [controllerFor]): its [ColorSettingsUiState]
     * is loaded once in the constructor, and a whole-scope reset from the text-settings list (
     * [TextDisplaySettingsController.onReset]) can change colours behind this screen's back while
     * it isn't shown -- a cached, stale instance would then reopen showing pre-reset values. */
    private fun colorControllerFor(scope: SettingsScope): ColorSettingsController =
        ColorSettingsController(service = service, scope = scope, coroutineScope = lifecycleScope, imagePicker = imagePicker)

    private fun onNavigate(scope: SettingsScope, key: String) {
        when (key) {
            // The workspace link only appears at WINDOW scope; the host always edits the active
            // workspace, so its id is windowRepository.id (matches the Window scope's workspaceId).
            KEY_OPEN_WORKSPACE_SETTINGS -> {
                // The pushed scope is rendered fresh; a query left over from the scope being left
                // would silently filter a list it was never typed against (e.g. "workspace", tap the
                // Workspace settings row, land on a list filtered by a query describing the row just
                // tapped rather than the new screen).
                searchMode.close()
                navStack = navStack + SettingsScope.Workspace(windowRepository.id.toString())
            }
            KEY_OPEN_GLOBAL_SETTINGS -> {
                searchMode.close()
                navStack = navStack + SettingsScope.Global
            }
            TextSettingType.COLORS.name -> {
                chooserNight = null
                colorsScope = scope
            }
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

    // --- Background-image thumbnails ------------------------------------------------------------

    /** Hoisted to [BackgroundThumbnailResolver] (Settings editor sheets T10) so
     * [net.bible.android.view.activity.page.screen.ComposeReadingViewHost]'s in-place editor can
     * resolve thumbnails the same way, with its own cache instance -- see that class's kdoc. */
    private val thumbnailResolver = BackgroundThumbnailResolver()

    // --- BOOKMARKS_HIDELABELS bridge (reproduces classic HideLabelsPreference.openDialog) -------

    private fun openHideLabels(scope: SettingsScope) {
        val controller = controllerFor(scope)
        // Screen.ManageLabels is deliberately not in ScreenLauncher.MIGRATED (its `data` argument is
        // required), and the classic ManageLabelsComposeActivity ScreenLauncher.targetFor used to
        // resolve it to is gone (nav-graph slices 2+4 Task 7), so this builds the nav-host Intent
        // directly. The "data" extra on the RESULT is unchanged -- NavResultIntents.forManageLabels
        // still writes it under that key.
        val data = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.HIDELABELS,
            selectedLabels = service.currentHideLabelsIds(scope).toMutableSet(),
            isWindow = scope is SettingsScope.Window,
        ).applyFrom(windowRepository.workspaceSettings).toJSON()
        val intent = NavHostComposeActivity.intentFor(this, NavRoutes.manageLabels(data))
        lifecycleScope.launch(Dispatchers.Main) {
            val result = awaitIntent(intent)
            if (result.resultCode == Activity.RESULT_OK) {
                val resultData = ManageLabelsContract.ManageLabelsData.fromJSON(result.data?.getStringExtra("data")!!)
                if (resultData.reset) {
                    controller.onRevert(TextSettingType.BOOKMARKS_HIDELABELS.name)
                } else {
                    windowRepository.workspaceSettings.updateFrom(resultData)
                    controller.onHideLabelsChange(resultData.selectedLabels.map { it.toString() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCOPE_LEVEL = "scopeLevel"                // "window" | "workspace" | "global"
        const val EXTRA_WINDOW_ID = "windowId"
        const val EXTRA_WORKSPACE_ID = "workspaceId"

        /** Carries the workspace the selector NAMED, as SettingsBundle JSON. Same extra name as
         *  classic TextDisplaySettingsActivity used, so the selector's onActivityResult is unchanged. */
        const val EXTRA_DETACHED_BUNDLE = "settingsBundle"

        /** Marker for a launch that should open directly at the internal colours destination —
         *  see [intentForColors]. Deliberately NOT part of [intentFor]'s extras: a plain launch
         *  must still land on the text-settings list. */
        const val EXTRA_START_AT_COLORS = "startAtColors"

        /** Selector-originated launch (spec 11.4): edits the named workspace against a detached copy
         *  and returns `settingsBundle` + `reset` when -- and only when -- something changed. */
        fun intentForDetachedWorkspace(context: Context, settingsBundleJson: String): Intent =
            Intent(context, TextDisplaySettingsComposeActivity::class.java)
                .putExtra(EXTRA_DETACHED_BUNDLE, settingsBundleJson)

        fun intentFor(context: Context, scope: SettingsScope): Intent {
            val intent = Intent(context, TextDisplaySettingsComposeActivity::class.java)
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

        /**
         * [intentFor]'s extras plus [EXTRA_START_AT_COLORS]: opens this Activity with the internal
         * colours destination ([ColorSettingsScreen]) already pushed for [scope].
         *
         * Slice S12's replacement for classic `ColorPreference.openDialog`'s raw
         * `ColorSettingsActivity` launch. That launch was a `startActivityForResult` round-trip
         * (`MainBibleActivity.COLORS_CHANGED`); this one is a plain `startActivity`, because the
         * Compose colours destination writes its edits through [ColorSettingsController] as they
         * are made and has no result to return.
         */
        fun intentForColors(context: Context, scope: SettingsScope): Intent =
            intentFor(context, scope).putExtra(EXTRA_START_AT_COLORS, true)
    }
}

/**
 * Whether a back press at the CURRENT destination should close search mode instead of falling
 * through to normal back navigation ([TextDisplaySettingsComposeActivity.pop]).
 *
 * Pure Kotlin (no Compose/Robolectric needed) so this can be unit-tested directly — see
 * [net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivityBackTest], which
 * proves the previous unconditional `searchMode.active.value` check swallowed a back press from
 * inside the Colors/chooser sub-destinations (Task 7 review Finding 1: search state is hoisted to
 * ACTIVITY level so it now outlives navigating into those destinations, unlike the old
 * `remember`-in-composable design, which was disposed the instant the composition navigated away).
 *
 * [atListDestination] must be `false` whenever the Colors or background-image-chooser
 * sub-destination is showing (they render no search UI at all) — do not simplify this to
 * `searchActive` alone, or a back press from Colors while search happens to still be active would
 * silently close it and eat the press instead of popping one level.
 */
internal fun shouldCloseSearchOnBack(atListDestination: Boolean, searchActive: Boolean): Boolean =
    atListDestination && searchActive

/**
 * Reconstructs the initial [SettingsScope] from the extras [TextDisplaySettingsComposeActivity.intentFor]
 * or [TextDisplaySettingsComposeActivity.intentForDetachedWorkspace] set. A present
 * [TextDisplaySettingsComposeActivity.EXTRA_DETACHED_BUNDLE] wins over [TextDisplaySettingsComposeActivity.EXTRA_SCOPE_LEVEL] --
 * a detached launch scopes to the bundle's own workspace, never the active one.
 */
private fun scopeFromIntent(intent: Intent): SettingsScope {
    intent.getStringExtra(TextDisplaySettingsComposeActivity.EXTRA_DETACHED_BUNDLE)?.let {
        return SettingsScope.Workspace(SettingsBundle.fromJson(it).workspaceId.toString())
    }
    return when (intent.getStringExtra(TextDisplaySettingsComposeActivity.EXTRA_SCOPE_LEVEL)) {
        "window" -> SettingsScope.Window(
            windowId = intent.getStringExtra(TextDisplaySettingsComposeActivity.EXTRA_WINDOW_ID)!!,
            workspaceId = intent.getStringExtra(TextDisplaySettingsComposeActivity.EXTRA_WORKSPACE_ID)!!,
        )
        "workspace" -> SettingsScope.Workspace(
            workspaceId = intent.getStringExtra(TextDisplaySettingsComposeActivity.EXTRA_WORKSPACE_ID)!!,
        )
        else -> SettingsScope.Global
    }
}
