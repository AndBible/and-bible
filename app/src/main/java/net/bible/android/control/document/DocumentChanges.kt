package net.bible.android.control.document

import androidx.annotation.VisibleForTesting
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.page.UpdateMainBibleActivityDocuments
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events

/** The set of installed documents changed (download, delete, ZIP install, restore). Replaces UpdateMainBibleActivityDocuments. */
object DocumentChanges {
    private var source = EventSource<Unit>()
    val installedChanged: Events<Unit> get() = source
    fun notifyInstalledChanged() {
        ABEventBus.post(UpdateMainBibleActivityDocuments())     // removed in Task 5
        source.emit(Unit)
    }
    @VisibleForTesting fun resetSubscribersForTest() { source = EventSource() }
}
