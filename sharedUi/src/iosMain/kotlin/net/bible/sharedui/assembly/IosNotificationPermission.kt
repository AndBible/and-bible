@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.assembly

import kotlinx.coroutines.suspendCancellableCoroutine
import net.bible.android.control.activity.SystemNotificationsPreference
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.NotificationPermission
import net.bible.sharedui.notifications.NotificationPermissionLogic
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun requestIosNotificationAuthorization(request: suspend (ULong) -> Boolean): Boolean =
    request(UNAuthorizationOptionAlert or UNAuthorizationOptionSound)

@kotlin.native.HiddenFromObjC
fun iosNotificationPermission(settings: AppSettings, preference: SystemNotificationsPreference): NotificationPermission =
    NotificationPermissionLogic(
        asked = { settings.getBoolean("notification_permission_asked", false) },
        markAsked = { settings.setBoolean("notification_permission_asked", true) },
        enabled = { preference.enabled.value },
        request = {
            requestIosNotificationAuthorization { options ->
                suspendCancellableCoroutine { continuation ->
                    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(options) { granted, error ->
                        if (error != null) continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                        else continuation.resume(granted)
                    }
                }
            }
        },
    )

@kotlin.native.HiddenFromObjC
suspend fun iosNotificationsDenied(): Boolean = suspendCancellableCoroutine { continuation ->
    UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
        continuation.resume(settings?.authorizationStatus == UNAuthorizationStatusDenied)
    }
}
