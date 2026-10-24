package net.bible.android.view.activity.nav.ports

import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.platform.exportStudyPads
import net.bible.android.view.activity.base.ActivityBase
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedui.app.bookmark.StudyPadExportPlatform

/** [StudyPadExportPlatform] over the existing Activity-bound `exportStudyPads`. */
class AndroidStudyPadExportPlatform(private val activity: ActivityBase) : StudyPadExportPlatform {
    override suspend fun export(labels: List<BookmarkEntities.Label>, chooseDestination: suspend () -> SaveOrShare?) {
        exportStudyPads(activity, *labels.toTypedArray(), chooseDestination = chooseDestination)
    }
}
