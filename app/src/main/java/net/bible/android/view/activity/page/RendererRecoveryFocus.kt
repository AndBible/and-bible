package net.bible.android.view.activity.page

import net.bible.android.database.IdType

/** Host-local recovery scope: automatic focus is not pane selection until replacements attach. */
internal class RendererRecoveryFocus {
    private val pending = mutableSetOf<IdType>()
    fun isRecovering(visibleWindowIds: Set<IdType>): Boolean {
        // Hidden/closed panes have no remount; do not wait forever for their attachment.
        pending.retainAll(visibleWindowIds)
        return pending.isNotEmpty()
    }

    fun rendererGone(windowId: IdType) { pending += windowId }
    fun replacementAttached(windowId: IdType) { pending -= windowId }
    fun clear() { pending.clear() }
}
