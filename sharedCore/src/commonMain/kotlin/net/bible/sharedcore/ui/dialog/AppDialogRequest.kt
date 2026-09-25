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

package net.bible.sharedcore.ui.dialog

import net.bible.sharedcore.settings.SettingsItem

/**
 * A dialog raised through [AppDialogController] — the owner-less generic dialogs (`Dialogs.kt`,
 * `CommonUtils`' helpers, `Hourglass`, service callers). A dialog that belongs to a feature lives in
 * that feature's own state instead (spec D4). Every text is already resolved (spec D5).
 *
 * HTML: [Message.message] and [Confirm.message] are HTML, rendered by `AbHtmlText` — the classic
 * dialogs ran every such body through `Html.fromHtml` (spec §3.2 finding 2).
 */
sealed interface AppDialogRequest {
    val title: String?

    /** Class A. [neutralText] is the third button ("Report error", "Show unlock info"). */
    data class Message(
        override val title: String?,
        val message: String,
        val confirmText: String,
        val dismissText: String? = null,
        val neutralText: String? = null,
        val cancellable: Boolean = false,
    ) : AppDialogRequest

    /** Class B. [message] may be null (a title-only question, e.g. `simpleQuestion(message = null)`). */
    data class Confirm(
        override val title: String?,
        val message: String?,
        val confirmText: String,
        val dismissText: String,
        val cancellable: Boolean = true,
    ) : AppDialogRequest

    /** Class C, a sheet. */
    data class SingleChoice(
        override val title: String?,
        val choices: List<SettingsItem.Choice>,
        val selectedValue: String?,
    ) : AppDialogRequest

    /** Class D, a sheet. Ids are the [SettingsItem.Choice.value]s. */
    data class MultiChoice(
        override val title: String?,
        val options: List<SettingsItem.Choice>,
        val selectedIds: List<String>,
        val confirmText: String,
        val dismissText: String,
        val selectAllText: String? = null,
        val selectNoneText: String? = null,
        /**
         * A pure function of the currently checked ids, rendered under the option list (e.g.
         * `GetCommentariesTool`'s live token total) -- recomputed by the host every time the user
         * toggles a row. `null`: no footer (existing callers unchanged).
         */
        val footerFor: ((List<String>) -> String)? = null,
    ) : AppDialogRequest

    /** Class E input. [neutralText] is an optional third button (answered [AppDialogResult.Neutral]). */
    data class TextInput(
        override val title: String?,
        val message: String?,
        val initial: String,
        val confirmText: String,
        val dismissText: String,
        val neutralText: String? = null,
        val numeric: Boolean = false,
        val masked: Boolean = false,
        val cancellable: Boolean = true,
    ) : AppDialogRequest

    /**
     * Class F. [asActionSheet] is spec §7's split: true = a list of actions (`AbActionSheet`),
     * false = answers to a question (`AbOptionsDialog`). Answered [AppDialogResult.Selected] with the
     * chosen [SettingsItem.Choice.value], or [AppDialogResult.Cancel]. [message] is HTML, rendered by
     * `AbHtmlText`, like [Message.message] and [Confirm.message].
     */
    data class Options(
        override val title: String?,
        val message: String?,
        val options: List<SettingsItem.Choice>,
        val dismissText: String?,
        val asActionSheet: Boolean,
        val cancellable: Boolean = true,
    ) : AppDialogRequest

    /**
     * `Hourglass`. Never answered — removed by [AppDialogController.dismiss]. Never cancellable.
     * Drawn underneath; never blocks answerable requests (see [AppDialogController.progress]).
     */
    data class Progress(override val title: String?, val message: String) : AppDialogRequest
}

sealed interface AppDialogResult {
    data object Ok : AppDialogResult
    /** Cancel button, back, scrim, or a request dropped by [AppDialogController.cancelAll]. */
    data object Cancel : AppDialogResult
    data object Neutral : AppDialogResult
    data class Text(val value: String) : AppDialogResult
    data class Selected(val value: String) : AppDialogResult
    data class SelectedMany(val ids: List<String>) : AppDialogResult
}

/** The queue head as the host sees it: [id] is what the host passes back to `respond`. */
data class ShownDialog(val id: Long, val request: AppDialogRequest)
