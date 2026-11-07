package net.bible.sharedui.assembly

import net.bible.sharedcore.platform.NativeNotifier
import org.koin.dsl.module

/** Native test executables have no application bundle, so UserNotifications cannot resolve their process. */
internal fun iosTestNotificationModule() = module {
    single<NativeNotifier> {
        IosNativeNotifier(object : UserNotificationCenterApi {
            override fun add(id: String, title: String, body: String, sound: Boolean) = Unit
            override fun removeDelivered(id: String) = Unit
            override fun removePending(id: String) = Unit
        })
    }
}
