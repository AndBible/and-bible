package net.bible.sharedui.desktop.webengine

import kotlin.test.Test
import kotlin.test.assertEquals

class NativePaneAttachmentTest {
    private class NativeQueue {
        val queued = ArrayDeque<() -> Unit>()
        val events = mutableListOf<String>()
        val attachment = NativePaneAttachment { queued.addLast(it) }
        fun drain() { while (queued.isNotEmpty()) queued.removeFirst().invoke() }
    }

    @Test fun outgoingDetachCannotHideNewMount() {
        val native = NativeQueue()
        val old = Any()
        val current = Any()
        native.attachment.attach(old) { native.events += "old visible" }
        native.attachment.attach(current) { native.events += "current visible" }
        native.attachment.detach(old) { native.events += "hidden" }
        native.drain()
        assertEquals(listOf("old visible", "current visible"), native.events)
    }

    @Test fun staleLayoutCannotReplaceCurrentBounds() {
        val native = NativeQueue()
        val old = Any()
        val current = Any()
        var bounds = 0
        native.attachment.attach(old) {}
        native.attachment.attach(current) {}
        native.attachment.update(current) { bounds = 590 }
        native.attachment.update(old) { bounds = 1184 }
        native.drain()
        assertEquals(590, bounds)
    }

    @Test fun currentDetachHidesAndRejectsLaterLayout() {
        val native = NativeQueue()
        val owner = Any()
        native.attachment.attach(owner) { native.events += "visible" }
        native.attachment.detach(owner) { native.events += "hidden" }
        native.attachment.update(owner) { native.events += "placed after detach" }
        native.drain()
        assertEquals(listOf("visible", "hidden"), native.events)
    }

    @Test fun closeRejectsAlreadyQueuedAndFutureMountWork() {
        val native = NativeQueue()
        val owner = Any()
        native.attachment.attach(owner) { native.events += "visible" }
        native.attachment.update(owner) { native.events += "placed" }
        native.attachment.close { native.events += "closed" }
        native.attachment.attach(Any()) { native.events += "resurrected" }
        native.attachment.detach(owner) { native.events += "hidden after close" }
        native.attachment.close { native.events += "closed twice" }
        native.drain()
        assertEquals(listOf("closed"), native.events)
    }
}
