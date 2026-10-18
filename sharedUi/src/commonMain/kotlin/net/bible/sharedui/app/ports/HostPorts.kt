package net.bible.sharedui.app.ports

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
)
