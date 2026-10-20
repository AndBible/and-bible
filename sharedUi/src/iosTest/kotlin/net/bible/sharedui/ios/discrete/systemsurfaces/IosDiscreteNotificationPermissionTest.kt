package net.bible.sharedui.ios.discrete

import kotlinx.coroutines.runBlocking
import net.bible.sharedui.app.ports.NotificationPermission
import kotlin.test.Test
import kotlin.test.assertEquals

class IosDiscreteNotificationPermissionTest {
    @Test fun noPermissionPromptInDiscrete() = runBlocking {
        var prompts = 0
        val permission = IosDiscreteNotificationPermission(object : NotificationPermission {
            override suspend fun request() { prompts++ }
        }, isDiscrete = { true })
        permission.request()
        assertEquals(0, prompts)
    }

    @Test fun permissionTracksModeChangesInBothDirections() = runBlocking {
        var discrete = false
        var prompts = 0
        val permission = IosDiscreteNotificationPermission(object : NotificationPermission {
            override suspend fun request() { prompts++ }
        }, isDiscrete = { discrete })
        permission.request()
        assertEquals(1, prompts)
        discrete = true
        permission.request()
        assertEquals(1, prompts)
        discrete = false
        permission.request()
        assertEquals(2, prompts)
    }
}
