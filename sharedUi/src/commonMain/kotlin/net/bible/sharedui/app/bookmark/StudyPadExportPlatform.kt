package net.bible.sharedui.app.bookmark

import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.sharedcore.platform.files.SaveOrShare

/**
 * Exports StudyPads to a file the user saves or shares. Android needs the Activity for it
 * (`exportStudyPads(activity, ...)`), so it is a host-scoped feature port held in `HostPorts`.
 */
interface StudyPadExportPlatform {
    /**
     * Exports [labels]. [chooseDestination] asks the user "save or share"; null (dismissed) means they chose
     * nothing.
     */
    suspend fun export(labels: List<BookmarkEntities.Label>, chooseDestination: suspend () -> SaveOrShare?)
}
