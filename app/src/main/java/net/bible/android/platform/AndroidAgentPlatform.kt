package net.bible.android.platform

import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import net.bible.android.BibleApplication
import net.bible.android.view.activity.base.AppPosition
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.llm.agent.AgentPlatform
import net.bible.service.llm.agent.ToolPermissionAnswer
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.event.Subscription

/** Android activity lookup and dialogs; foreground polling retains the existing 500 ms cadence. */
class AndroidAgentPlatform : AgentPlatform {
    override val hasForegroundUi: Boolean get() = CurrentActivityHolder.currentActivity != null
    override val foregroundChanges: Events<Boolean> = object : Events<Boolean> {
        override fun subscribe(handler: (Boolean) -> Unit): Subscription =
            CurrentActivityHolder.appPositionChanges.subscribe { handler(it == AppPosition.FOREGROUND) }
        override fun subscribeOnMain(handler: (Boolean) -> Unit): Subscription =
            CurrentActivityHolder.appPositionChanges.subscribeOnMain { handler(it == AppPosition.FOREGROUND) }
        override fun asFlow() = callbackFlow {
            val subscription = subscribe { trySend(it) }
            awaitClose { subscription.cancel() }
        }.buffer(Channel.UNLIMITED)
    }

    private suspend fun awaitActivity(): net.bible.android.view.activity.base.ActivityBase {
        CurrentActivityHolder.currentActivity?.let { return it }
        do {
            delay(500)
            val activity = CurrentActivityHolder.currentActivity
            if (activity != null) return activity
        } while (true)
    }

    override suspend fun awaitForegroundUi(): Boolean { awaitActivity(); return true }

    // The executor owns the foreground wait. Dialog controllers can remain pending in the
    // background, including the second "always allow" confirmation; never re-wait here.
    override suspend fun askToolPermission(toolDisplayName: String, toolDescription: String, actionDescription: String?): ToolPermissionAnswer =
        when (Dialogs.agentPermissionDialog(BibleApplication.application, toolDisplayName, toolDescription, actionDescription)) {
            Dialogs.AgentPermissionResult.ALLOW -> ToolPermissionAnswer.ALLOW
            Dialogs.AgentPermissionResult.ALLOW_FOR_SESSION -> ToolPermissionAnswer.ALLOW_FOR_SESSION
            Dialogs.AgentPermissionResult.ALLOW_ALL_SESSION -> ToolPermissionAnswer.ALLOW_ALL_SESSION
            Dialogs.AgentPermissionResult.ALLOW_ALWAYS -> ToolPermissionAnswer.ALLOW_ALWAYS
            Dialogs.AgentPermissionResult.DENY -> ToolPermissionAnswer.DENY
        }

    override suspend fun askQuestion(title: String?, message: String): Boolean =
        Dialogs.simpleQuestion(BibleApplication.application, title = title, message = message)

    override fun showShortMessage(message: String) {
        Toast.makeText(BibleApplication.application, message, Toast.LENGTH_SHORT).show()
    }
}
