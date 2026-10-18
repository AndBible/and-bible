package net.bible.sharedui.app.ports

import net.bible.sharedui.app.bookmark.BookmarkHostPlatform
import net.bible.sharedui.app.bookmark.LabelIcons
import net.bible.sharedui.app.bookmark.StudyPadExportPlatform
import net.bible.sharedui.app.chooser.ChooserHostPlatform
import net.bible.sharedui.app.readingplan.ReadingPlanHostPlatform
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
    /** The label glyphs and top-bar icons of the bookmark cluster. */
    val labelIcons: LabelIcons,
    /** StudyPad export, which needs the Activity on Android. */
    val studyPadExport: StudyPadExportPlatform,
    /** The platform half of the bookmark cluster; a provider because the host builds it on first use. */
    val bookmarkPlatform: () -> BookmarkHostPlatform,
    /** The platform half of the reading plan cluster; a provider because the host builds it on first use. */
    val readingPlanPlatform: () -> ReadingPlanHostPlatform,
    /** The file I/O of the My Documents screens, which needs the Activity on Android. */
    val myDocumentFiles: MyDocumentFiles,
    /** The Backup screen's file pickers, share sheet and dialogs, which need the Activity on Android. */
    val backupHost: net.bible.android.control.backup.BackupHost,
)
