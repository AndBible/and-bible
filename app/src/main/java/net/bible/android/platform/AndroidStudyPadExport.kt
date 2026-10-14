package net.bible.android.platform

import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.android.control.backup.SaveOrShare
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.exportStudyPads
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import org.koin.java.KoinJavaComponent

/** Android entry point of the StudyPad export: resolves the seams from Koin and delivers through [activity]. */
suspend fun exportStudyPads(
    activity: ActivityBase,
    vararg labels: BookmarkEntities.Label,
    chooseDestination: (suspend () -> SaveOrShare?)? = null,
    host: BackupHost = AndroidBackupHost { activity },
) = exportStudyPads(
    labels.toList(), host,
    KoinJavaComponent.get(CoreStrings::class.java),
    KoinJavaComponent.get(AppEnvironment::class.java),
    KoinJavaComponent.get(BackupControl::class.java),
    chooseDestination,
)
