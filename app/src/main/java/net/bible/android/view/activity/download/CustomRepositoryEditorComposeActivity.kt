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
package net.bible.android.view.activity.download

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.download.CustomRepositoryEditorController
import net.bible.sharedcore.download.CustomRepositoryService
import net.bible.sharedcore.download.RepositoryResult
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.download.CustomRepositoryEditorScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

/**
 * Compose host for the custom-repository editor -- the new-path twin of classic
 * [CustomRepositoryEditor]. Reads the SAME `"data"`=[RepositoryData] JSON intent extra, maps it to
 * a portable [RepositoryResult] via [toRepositoryResult], drives the shared
 * [CustomRepositoryEditorController], and renders [CustomRepositoryEditorScreen].
 *
 * **Result parity.** Every exit path (save/delete/cancel) maps the controller's [RepositoryResult]
 * back through [toRepositoryData] to the classic JSON shape and finishes with `RESULT_OK` --
 * INCLUDING cancel (classic `cancelAndExit()` also uses `RESULT_OK`, carrying `cancel = true` in
 * the payload rather than `RESULT_CANCELED`), so whichever list activity launched this
 * ([CustomRepositoriesComposeActivity] or classic [CustomRepositories]) reads the result identically
 * regardless of which editor -- Compose or classic -- ran.
 *
 * **Dirty-back.** [CustomRepositoryEditorScreen] itself gates its up-navigation icon behind a
 * discard-changes confirmation (its own internal `AbConfirmDialog`) whenever the controller state
 * is dirty. The SAME gate is duplicated here via [BackHandler] for the system back gesture/button,
 * which a plain composable cannot intercept (mirrors `PromptEditComposeActivity`'s pattern).
 */
class CustomRepositoryEditorComposeActivity : ActivityBase() {
    private val service: CustomRepositoryService by inject()

    private val initial: RepositoryResult by lazy {
        RepositoryData.fromJSON(intent.getStringExtra("data")!!).toRepositoryResult()
    }

    private val controller by lazy {
        CustomRepositoryEditorController(service, lifecycleScope, initial)
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
                    val state by controller.state.collectAsState()
                    var showDiscardConfirm by remember { mutableStateOf(false) }

                    BackHandler {
                        if (state.isDirty) showDiscardConfirm = true
                        else finishWithResult(controller.buildCancelResult())
                    }

                    CustomRepositoryEditorScreen(
                        state = state,
                        onUrlChange = controller::setUrl,
                        onPaste = { pasteFromClipboard() },
                        onPackageDirChange = controller::setPackageDir,
                        onSave = { finishWithResult(controller.buildSaveResult()) },
                        onDelete = { finishWithResult(controller.buildDeleteResult()) },
                        onUp = { finishWithResult(controller.buildCancelResult()) },
                    )

                    if (showDiscardConfirm) {
                        AbConfirmDialog(
                            title = null,
                            message = strings.discardChangesConfirmation,
                            confirmText = strings.yes,
                            dismissText = strings.no,
                            onConfirm = { showDiscardConfirm = false; finishWithResult(controller.buildCancelResult()) },
                            onDismiss = { showDiscardConfirm = false },
                        )
                    }
                }
            }
        }
    }

    /** Classic `paste()`: read the clipboard's primary text clip straight into the URL field, which
     *  schedules validation the same as typing (via [CustomRepositoryEditorController.setUrl]). */
    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val paste = clipboard.primaryClip?.getItemAt(0)?.text
        if (paste != null) controller.setUrl(paste.toString())
    }

    private fun finishWithResult(result: RepositoryResult) {
        val json = result.toRepositoryData().toJSON()
        setResult(RESULT_OK, Intent().putExtra("data", json))
        finish()
    }
}
