package net.bible.sharedui.desktop.webengine

/**
 * Serializes a native pane's composition mounts on its native thread. Compose may attach an incoming
 * movable subtree before disposing the outgoing subtree of the same retained pane. All calls except
 * [close] originate on the composition thread; [post] must enqueue work in order on the native thread.
 */
class NativePaneAttachment(private val post: (() -> Unit) -> Unit) {
    private var current: Any? = null // native-thread state
    @Volatile private var closed = false

    fun attach(owner: Any, action: () -> Unit) = post {
        if (!closed) {
            current = owner
            action()
        }
    }

    fun update(owner: Any, action: () -> Unit) = post {
        if (!closed && current === owner) action()
    }

    fun detach(owner: Any, action: () -> Unit) = post {
        if (!closed && current === owner) {
            current = null
            action()
        }
    }

    /** Rejects queued mounts immediately, then releases native resources after the already queued work. */
    @Synchronized
    fun close(action: () -> Unit) {
        if (closed) return
        closed = true
        post {
            current = null
            action()
        }
    }
}
