package net.bible.android.view.activity.nav.ports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.android.control.event.UserMessages
import net.bible.android.view.activity.base.Dialogs
import net.bible.sharedui.app.ports.DebugActions

class AndroidDebugActions : DebugActions {
    /**
     * Runs on a fresh lifecycle-INDEPENDENT scope: the delayed crash must still fire if the user leaves
     * the settings screen within the ten seconds.
     */
    override fun crashAppDelayed() {
        CoroutineScope(Dispatchers.Main).launch {
            UserMessages.errorNotification("Crashing app in 10 seconds!")
            delay(10000)
            throw RuntimeException("Crash app!")
        }
    }

    override fun showTestError(message: String) {
        Dialogs.showErrorMsg(message, RuntimeException("E4 test exception"))
    }
}
