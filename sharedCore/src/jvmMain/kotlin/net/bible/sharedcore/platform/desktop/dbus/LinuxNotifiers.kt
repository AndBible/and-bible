package net.bible.sharedcore.platform.desktop.dbus

import java.io.File
import kotlinx.coroutines.CoroutineScope
import net.bible.sharedcore.platform.NativeNotification
import net.bible.sharedcore.platform.NativeNotifier

/** Notifier for platforms without a notification service: every call is ignored. */
object NoOpNativeNotifier : NativeNotifier {
    override val supportsProgress: Boolean get() = false
    override fun show(n: NativeNotification) {}
    override fun remove(id: String) {}
    override fun setOnActivated(handler: (id: String) -> Unit) {}
}

/**
 * The notifier for this desktop: the Notification portal inside Flatpak ([FLATPAK_ID] set or `/.flatpak-info` present),
 * the freedesktop service otherwise, [NoOpNativeNotifier] where D-Bus cannot exist. The D-Bus ones are [AutoCloseable].
 */
fun linuxNativeNotifier(
    dbus: DesktopDbus = DesktopDbus(),
    env: (String) -> String? = System::getenv,
    scope: CoroutineScope,
    flatpakInfoExists: () -> Boolean = { File("/.flatpak-info").exists() },
): NativeNotifier = when {
    !dbus.supportedOs -> NoOpNativeNotifier
    !env("FLATPAK_ID").isNullOrEmpty() || flatpakInfoExists() -> PortalNotifier(dbus, scope)
    else -> FreedesktopNotifier(dbus, scope)
}

private const val FLATPAK_ID = "FLATPAK_ID"
