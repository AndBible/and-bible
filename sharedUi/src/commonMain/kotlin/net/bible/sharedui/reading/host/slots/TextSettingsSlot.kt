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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.host.slots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import net.bible.sharedcore.reading.ReadingOverlay
import net.bible.sharedcore.reading.ReadingOverlayExclusion
import net.bible.sharedcore.settings.ColorSettingsController
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsEditorStack
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedui.app.ports.ImagePicker
import net.bible.sharedui.app.workspace.buildBackgroundImageChooserLabels
import net.bible.sharedui.app.workspace.buildColorSettingsLabels
import net.bible.sharedui.app.workspace.buildTextDisplayControllerLabels
import net.bible.sharedui.app.workspace.buildTextDisplayScreenLabels
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.settings.ColorSettingsEditorSheet
import net.bible.sharedui.settings.GenericSettingsEditorSheet
import net.bible.sharedui.settings.TextSettingRowEditorSheet
import net.bible.sharedui.strings.Strings

/**
 * The reading view's in-place text-settings editor: its page stack, the scope it edits, the cached
 * controllers and the sheet body. Owned by [ReadingHostSlots]. The stack and scope live here (not in
 * composition), so a configuration change that recreates the composition finds the editor exactly
 * where it was.
 *
 * [service] is a provider because the Android Koin singleton must not be resolved at construction.
 * [imagePicker] and [thumbnailFor] are the platform ports (`ImagePicker`,
 * `TextDisplayPlatform.thumbnail`); the four label bundles come from [strings] and stay lazy.
 */
@kotlin.native.HiddenFromObjC
internal class TextSettingsEditorState(
    private val hostState: ReadingHostState,
    private val service: () -> TextDisplaySettingsService,
    private val scope: CoroutineScope,
    private val strings: () -> Strings,
    private val imagePicker: ImagePicker,
    private val thumbnailFor: (String) -> ImageBitmap?,
) {
    /** The editor's page stack; independent of the settings screen's own stack. */
    val editor = SettingsEditorStack()

    /** `mutableStateOf`: the slot reads it in composition via `remember(scope)`. */
    private var editedScope: SettingsScope? by mutableStateOf(null)
    private var onReady: () -> Unit = {}

    private val controllers = mutableMapOf<SettingsScope, TextDisplaySettingsController>()

    private val controllerLabels by lazy { strings().buildTextDisplayControllerLabels() }
    private val screenLabels by lazy { strings().buildTextDisplayScreenLabels() }
    private val colorLabels by lazy { strings().buildColorSettingsLabels() }
    private val chooserLabels by lazy { strings().buildBackgroundImageChooserLabels() }

    /** The four list-choice keys render through [GenericSettingsEditorSheet]; the rest are numeric/margin pages. */
    private val listChoiceTextSettingKeys = setOf(
        TextSettingType.FONTFAMILY.name,
        TextSettingType.STRONGS.name,
        TextSettingType.PAGE_SCROLL_AMOUNT.name,
        TextSettingType.SCROLL_HELPER_LINE_STYLE.name,
    )

    fun show(settingsScope: SettingsScope, page: SettingsEditorPage, onReady: () -> Unit) {
        // Total mutual exclusion: this editor is a modal overlay, opening it closes the other ones.
        ReadingOverlayExclusion.closedBy(ReadingOverlay.TextSettingsEditor).forEach(hostState::closeOverlay)
        // Close first: `open` assigns a MutableStateFlow, which conflates an equal value, so opening
        // the same page for a different scope would emit nothing and keep the old scope's controller.
        editor.close()
        editedScope = settingsScope
        this.onReady = onReady
        // The cached controller only reloads after its own writes; an edit made through another
        // controller instance would leave this one stale and it could write the stale value back.
        controllerFor(settingsScope).refresh()
        editor.open(page)
    }

    fun controllerFor(settingsScope: SettingsScope): TextDisplaySettingsController =
        controllers.getOrPut(settingsScope) {
            TextDisplaySettingsController(
                service = service(),
                settingsScope = settingsScope,
                labels = controllerLabels,
                // Every navigating key is filtered out by textSettingEditorPageFor before this
                // controller is reached, so the in-place editor never navigates.
                onNavigateCallback = { },
            )
        }

    /** Not cached: its state is loaded once in the constructor and a whole-scope reset can change colours behind it. */
    fun colorControllerFor(settingsScope: SettingsScope): ColorSettingsController =
        ColorSettingsController(
            service = service(),
            scope = settingsScope,
            coroutineScope = scope,
            imagePicker = { imagePicker.pickImage() },
        )

    /** Self-hides when the stack is empty, so a host can render it unconditionally. */
    @Composable
    fun Content() {
        val pages by editor.pages.collectAsState()
        val page = pages.lastOrNull() ?: return
        val editedScope = editedScope ?: return
        val controller = remember(editedScope) { controllerFor(editedScope) }
        val state by controller.state.collectAsState()

        when (page) {
            is SettingsEditorPage.Row -> {
                if (page.key in listChoiceTextSettingKeys) {
                    GenericSettingsEditorSheet(
                        state = SettingsScreenState(title = state.title, items = state.items),
                        editor = editor,
                        page = page,
                        depth = pages.size,
                        onListChoice = { key, v -> controller.onListChoice(key, v); onReady() },
                        // Text inputs and multi-selects never occur among text-display settings.
                        onTextInput = { _, _ -> },
                        onMultiSelectChange = { _, _ -> },
                    )
                } else {
                    TextSettingRowEditorSheet(
                        pages = pages,
                        rows = state.rows,
                        dialogLabels = screenLabels,
                        onNumericChange = { key, v -> controller.onNumericChange(key, v); onReady() },
                        onMarginsChange = { key, l, r, m -> controller.onMarginsChange(key, l, r, m); onReady() },
                        onRevert = { key -> controller.onRevert(key); onReady() },
                        onPop = { editor.pop() },
                        onClose = { editor.close() },
                    )
                }
            }
            is SettingsEditorPage.Colors,
            is SettingsEditorPage.ColorPick,
            is SettingsEditorPage.BackgroundImage -> {
                val colorController = remember(editedScope) { colorControllerFor(editedScope) }
                val colorState by colorController.state.collectAsState()
                ColorSettingsEditorSheet(
                    pages = pages,
                    state = colorState,
                    labels = colorLabels,
                    chooserLabels = chooserLabels,
                    thumbnailFor = thumbnailFor,
                    importVisible = true,
                    onColorChange = { f, c -> colorController.onColorChange(f, c); onReady() },
                    onWorkspaceColorChange = { c -> colorController.onWorkspaceColorChange(c); onReady() },
                    onWorkspaceColorReset = { colorController.onWorkspaceColorReset(); onReady() },
                    onNoiseChange = { n, v -> colorController.onNoiseChange(n, v); onReady() },
                    onOpacityChange = { n, v -> colorController.onOpacityChange(n, v); onReady() },
                    onSelectBackgroundImage = { n, i -> colorController.onSelectBackgroundImage(n, i); onReady() },
                    onImportBackgroundImage = colorController::onImportBackgroundImage,
                    onRequestDeleteBackgroundImage = colorController::onRequestDeleteBackgroundImage,
                    onConfirmDeleteBackgroundImage = colorController::onConfirmDeleteBackgroundImage,
                    onDismissDeleteConfirm = colorController::onDismissDeleteConfirm,
                    onReset = { colorController.onReset(); onReady() },
                    resetConfirmMessage = screenLabels.resetConfirmMessage,
                    confirmLabel = screenLabels.okLabel,
                    cancelLabel = screenLabels.cancelLabel,
                    onPush = { editor.push(it) },
                    onPop = { editor.pop() },
                    onClose = { editor.close() },
                )
            }
        }
    }
}
