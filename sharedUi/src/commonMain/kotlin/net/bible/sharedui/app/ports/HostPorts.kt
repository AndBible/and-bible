package net.bible.sharedui.app.ports

import net.bible.sharedui.app.chooser.ChooserHostPlatform
import net.bible.sharedui.app.welcome.WelcomeHostPlatform

/**
 * Ports whose Android implementation needs the Activity (launchers registered before STARTED).
 * Feature-specific ports are added by the owning task.
 */
class HostPorts(
    val documentPicker: DocumentPicker,
    val imagePicker: ImagePicker,
    val textShare: TextShare,
    val notificationPermission: NotificationPermission,
    val systemScreens: SystemScreens,
    val documentDialogs: DocumentDialogs,
    val helpDialogs: HelpDialogs,
    /** The platform half of the welcome flow; a provider because the host builds it on first use. */
    val welcomePlatform: () -> WelcomeHostPlatform,
    /** The platform half of the chooser cluster; a provider because the host builds it on first use. */
    val chooserPlatform: () -> ChooserHostPlatform,
)
