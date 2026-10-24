package net.bible.android.view.activity.nav.ports

import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.sharedui.app.ports.NotificationPermission

class AndroidNotificationPermission(private val activity: ActivityBase) : NotificationPermission {
    override suspend fun request() = CommonUtils.requestNotificationPermission(activity)
}
