package net.bible.android.view.activity.page

import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout

/** Observes Chromium virtual-node clicks without replacing its accessibility provider. */
internal class BiblePaneFrame(view: BibleView) : FrameLayout(view.context) {
    init { addView(view) }

    override fun requestSendAccessibilityEvent(child: View, event: AccessibilityEvent): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) (child as? BibleView)?.userSelectedPane()
        return super.requestSendAccessibilityEvent(child, event)
    }
}
