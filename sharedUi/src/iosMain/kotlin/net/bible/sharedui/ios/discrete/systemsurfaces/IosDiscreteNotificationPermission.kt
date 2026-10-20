package net.bible.sharedui.ios.discrete

import net.bible.sharedui.app.ports.NotificationPermission

internal class IosDiscreteNotificationPermission(
    private val delegate: NotificationPermission,
    private val isDiscrete: () -> Boolean,
) : NotificationPermission {
    /** U7 avoids a system prompt naming the app before the PIN; ADR 0007 does not fake its name. */
    override suspend fun request() {
        if (!isDiscrete()) delegate.request()
    }
}
