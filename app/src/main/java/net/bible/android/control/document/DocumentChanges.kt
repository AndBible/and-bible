package net.bible.android.control.document

import androidx.annotation.VisibleForTesting
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events

/** The set of installed documents changed (download, delete, ZIP install, restore). */
object DocumentChanges {
    private var source = EventSource<Unit>()
    val installedChanged: Events<Unit> get() = source
    fun notifyInstalledChanged() {
        source.emit(Unit)
    }
    @VisibleForTesting fun resetSubscribersForTest() { source = EventSource() }
}
