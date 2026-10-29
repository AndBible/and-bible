package net.bible.sharedui.desktop.misc

import net.bible.sharedui.app.ports.DebugActions
import net.bible.sharedui.app.ports.NotificationPermission
import net.bible.sharedui.app.ports.SystemScreens
import net.bible.sharedcore.platform.UserNotifier
import java.awt.EventQueue
import java.util.Timer
import java.util.TimerTask

class DesktopNotificationPermission : NotificationPermission {
    override suspend fun request() = Unit
}

class DesktopSystemScreens : SystemScreens {
    override fun openAppLinkSettings() = Unit
}

class DesktopDebugActions(private val notifier: UserNotifier? = null, private val enabled: () -> Boolean = { System.getProperty("andbible.debug.actions") == "true" }) : DebugActions {
    override fun crashAppDelayed() {
        if (!enabled()) return
        Timer("desktop-debug-crash", true).schedule(object : TimerTask() {
            override fun run() { EventQueue.invokeLater { throw IllegalStateException("Deliberate desktop debug crash") } }
        }, 2000)
    }

    override fun showTestError(message: String) {
        if (enabled()) notifier?.showError(message, IllegalStateException("Debug test error"))
    }
}
