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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.ai.GlobalToolPermissionsController
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedcore.ai.ToolPermissionService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.GlobalToolPermissionsScreen
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

/**
 * Compose host for the global default tool-permissions screen — the new-path twin of classic
 * [GlobalToolPermissionsActivity]. Wires the shared [GlobalToolPermissionsController] over
 * [ToolPermissionService] and renders [GlobalToolPermissionsScreen].
 *
 * **Save-on-apply.** The Save check-icon (`onSave`) persists the controller's working map via
 * [GlobalToolPermissionsController.save] and then `finish()`es — mirroring classic's
 * `save_permissions` → `saveAndFinish()`. "Reset all" (`onResetAll`) and per-tool edits stay staged
 * in the controller until Save (classic parity — `reset_all` only flips the UI to neutral defaults).
 *
 * **Dirty-back.** [GlobalToolPermissionsScreen] gates its up-navigation icon behind a discard-confirm
 * dialog whenever dirty (see that screen's kdoc), calling `onUp` (= `finish()`) only once confirmed.
 * The SAME gate is duplicated here via [BackHandler] for the system back gesture/button (which the
 * plain composable cannot intercept), mirroring classic's `onBackPressed`→`cancelOrConfirmDiscard()`.
 */
class GlobalToolPermissionsComposeActivity : ActivityBase() {
    private val service: ToolPermissionService by inject()

    private val controller by lazy {
        GlobalToolPermissionsController(service = service, scope = lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val strings = LocalStrings.current
                    val groups by controller.state.collectAsState()
                    val permissions by controller.permissions.collectAsState()
                    val isDirty by controller.isDirty.collectAsState()

                    var showDiscardConfirm by remember { mutableStateOf(false) }
                    BackHandler {
                        if (isDirty) showDiscardConfirm = true else finish()
                    }

                    GlobalToolPermissionsScreen(
                        groups = groups,
                        permissionFor = { permissions[it] ?: ToolPermission.ASK },
                        isDirty = isDirty,
                        onUp = { finish() },
                        onSetPermission = controller::setPermission,
                        onResetAll = controller::resetAll,
                        onSave = { controller.save(); finish() },
                        helpBody = getString(R.string.help_global_tool_permissions_text),
                        helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#setting-permissions",
                    )

                    if (showDiscardConfirm) {
                        AbConfirmDialog(
                            title = null,
                            message = strings.discardChangesConfirmation,
                            confirmText = strings.yes,
                            dismissText = strings.no,
                            onConfirm = { showDiscardConfirm = false; finish() },
                            onDismiss = { showDiscardConfirm = false },
                        )
                    }
                }
            }
        }
    }
}
