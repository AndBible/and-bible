@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.assembly

import kotlinx.coroutines.test.runTest
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosNotificationPermissionTest {
    @Test
    fun requestUsesAlertAndSound() = runTest {
        var options = 0uL
        val granted = requestIosNotificationAuthorization { requested ->
            options = requested
            true
        }
        assertEquals(UNAuthorizationOptionAlert or UNAuthorizationOptionSound, options)
        assertTrue(granted)
    }
}
