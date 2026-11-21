package net.bible.service.cloudsync

import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult

/**
 * Answers a collected join plan the way a user who picks [choice] ("merge", "fetch", "create" or "disable") for
 * every category and confirms would: the same choice for every row.
 */
fun answerJoinPlan(request: AppDialogRequest.SyncJoinPlan, choice: String): AppDialogResult =
    AppDialogResult.SyncJoinChoices(request.rows.associate { it.key to choice })
