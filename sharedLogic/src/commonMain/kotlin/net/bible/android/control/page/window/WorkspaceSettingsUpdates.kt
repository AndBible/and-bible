package net.bible.android.control.page.window

import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities.WorkspaceSettings
import net.bible.sharedlogic.sync.withMonitor

/** Replace the cursor snapshot under the same owner monitor for UI and bookmark-service writers. */
fun WorkspaceSettings.setStudyPadCursor(labelId: IdType, orderNumber: Int) = withMonitor(this) {
    studyPadCursors = studyPadCursors + (labelId to orderNumber)
}
