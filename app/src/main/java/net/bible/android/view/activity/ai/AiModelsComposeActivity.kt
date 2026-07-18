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
package net.bible.android.view.activity.ai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.ai.AiModelsController
import net.bible.sharedcore.ai.LlmModelService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.AiModelsScreen
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

/**
 * Compose host for the AI models list/add/edit screen — the new-path twin of classic
 * [AiModelsActivity]/[AiModelsFragment]. Wires the shared [AiModelsController] over
 * [LlmModelService] and renders [AiModelsScreen]. No disclaimer gate here (parity with classic —
 * the disclaimer is enforced at the provider level; a model can only be added once a provider
 * exists). The **Help** overflow (parity with classic `ai_models_options_menu`) is a Compose
 * top-bar action.
 *
 * `onDismiss` carries a small guard: the add flow's provider picker is an `AbListChoiceDialog`,
 * which fires `onSelect(value)` **and** `onDismiss()` synchronously on a tap (its own
 * `onClick = { onSelect(...); onDismiss() }`). Wiring `onDismiss` straight to
 * [AiModelsController.dismissDialog] would therefore clobber the [AiModelsController.pickProvider]
 * step transition (PICK_PROVIDER → PICK_MODEL) that `onSelect` just performed, closing the whole
 * dialog. A transient [swallowNextDismiss] flag (set in `onPickProvider`, cleared by the paired
 * synchronous `onDismiss`) makes that one spurious dismiss a no-op while every genuine dismiss
 * (PICK_PROVIDER cancel, PICK_MODEL cancel/delete-confirm) still closes — the same class of fix
 * as the easy-setup wizard's `onDismiss` guard in [AiProvidersComposeActivity].
 */
class AiModelsComposeActivity : ActivityBase() {
    private val service: LlmModelService by inject()

    private val controller by lazy { AiModelsController(service, lifecycleScope) }

    /** Swallows the single synchronous `onDismiss` that `AbListChoiceDialog` fires right after
     *  `onSelect` when a provider is picked (see class KDoc). */
    private var swallowNextDismiss = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val models by controller.models.collectAsState()
                    val dialog by controller.dialog.collectAsState()
                    // Configured providers supply the read-only "Provider" name in the edit flow
                    // (where ModelEditState.providerChoices is empty). Recompute on model-list
                    // changes as a cheap proxy for provider-list changes.
                    val providers = remember(models) { service.providersForPicker() }
                    AiModelsScreen(
                        models = models,
                        providers = providers,
                        editState = dialog,
                        onUp = { finish() },
                        onAdd = controller::startAdd,
                        onPickProvider = { providerId ->
                            swallowNextDismiss = true
                            controller.pickProvider(providerId)
                        },
                        onPickModel = controller::pickModel,
                        onStartEdit = controller::startEdit,
                        onField = controller::updateField,
                        onSave = controller::save,
                        onDelete = controller::delete,
                        onSetDefault = controller::setDefault,
                        onSetAsDefault = controller::setAsDefault,
                        onSetShowUnsupported = controller::setShowUnsupported,
                        onDismiss = {
                            if (swallowNextDismiss) {
                                swallowNextDismiss = false
                            } else {
                                controller.dismissDialog()
                            }
                        },
                        actions = { HelpAction() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Parity with classic's onResume refresh (models/keys may have changed elsewhere).
        service.refresh()
    }

    // --- Help overflow (parity with classic ai_models_options_menu) -------------------------------

    @Composable
    private fun RowScope.HelpAction() {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Text("⋮", fontSize = 24.sp) // vertical ellipsis; Material icons aren't on the app-module classpath
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(getString(R.string.help)) }, onClick = {
                expanded = false
                CommonUtils.showHelpDialog(
                    activity = this@AiModelsComposeActivity,
                    titleResId = R.string.help,
                    messageResId = R.string.help_ai_models_text,
                    helpPath = "ai.html#available-models",
                )
            })
        }
    }
}
