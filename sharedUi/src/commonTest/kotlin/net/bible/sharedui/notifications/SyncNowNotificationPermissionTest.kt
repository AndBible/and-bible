package net.bible.sharedui.notifications

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.CloudSyncPort
import net.bible.service.cloudsync.SyncNow
import net.bible.sharedcore.event.EventSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SyncNowNotificationPermissionTest {
    @Test fun syncNowAsksOnceOnlyWhenNotificationsEnabled() = runTest {
        for (enabled in listOf(true, false)) {
            var asked = false
            var requests = 0
            var starts = 0
            val permission = NotificationPermissionLogic({ asked }, { asked = true }, { enabled }) {
                requests++
                true
            }
            val cloud = object : CloudSyncPort {
                override val signedIn = true
                override val runningChanged = EventSource<Boolean>()
                override suspend fun signIn() = true
                override fun start() = CompletableDeferred(true).also { starts++ }
                override suspend fun waitUntilFinished() = Unit
            }
            val sync = SyncNow(cloud, EventSource(), { true }, {}, { true }, { false }, {}, this,
                notificationPermission = permission)
            assertEquals(0, requests, "Construction must not ask permission")
            repeat(2) {
                sync.start()
                runCurrent()
                assertFalse(sync.running.value)
            }
            assertEquals(2, starts)
            assertEquals(if (enabled) 1 else 0, requests)
            assertEquals(enabled, asked)
            sync.close()
        }
    }
}
