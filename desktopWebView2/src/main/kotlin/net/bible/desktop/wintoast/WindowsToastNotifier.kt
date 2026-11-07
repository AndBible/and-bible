package net.bible.desktop.wintoast

import net.bible.desktop.webview2.MessagePump
import net.bible.desktop.webview2.ComException
import net.bible.desktop.webview2.StaThread
import net.bible.desktop.webview2.Win32MessagePump
import net.bible.desktop.webview2.hresultString
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.NativeNotification
import net.bible.sharedcore.platform.NativeNotifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Serializes notification state on the COM owner; callers (including AWT) only enqueue finite work. */
class WindowsToastNotifier(
    private val api: () -> ToastApi,
    private val thread: (Runnable) -> Unit,
    private val closeThread: () -> Unit = {},
    private val log: (String) -> Unit = { Log.w("WindowsToast", it) },
    private val cleanupThread: (Runnable) -> Unit = thread,
) : NativeNotifier, AutoCloseable {
    private class Owner(
        private val pumpFactory: () -> MessagePump = { Win32MessagePump() },
        private val log: (String) -> Unit = { Log.w("WindowsToast", it) },
    ) {
        private var sta: StaThread? = null
        @Synchronized fun post(task: Runnable) {
            val owner = sta ?: StaThread("AndBible-WindowsToast", pumpFactory(), onFailure = { error ->
                val detail = if (error is ComException) hresultString(error.hresult) else "${error.javaClass.name}: ${error.message}"
                runCatching { log("Windows toast owner failed: $detail") }
            }).also { sta = it }
            owner.post { task.run() }
        }
        @Synchronized fun cleanup(task: Runnable) { sta?.post { task.run() } }
        fun close() { sta?.closeAndJoin(500) }
    }

    private constructor(owner: Owner, aumid: String?) : this(
        { WinRtToastApi(aumid) }, owner::post, owner::close, cleanupThread = owner::cleanup,
    )

    /** Null selects an MSIX package identity; the default selects the installer's registered AUMID. */
    constructor(aumid: String? = ANDBIBLE_AUMID) : this(Owner(), aumid)

    internal constructor(aumid: String?, pumpFactory: () -> MessagePump, log: (String) -> Unit) :
        this(Owner(pumpFactory, log), aumid)

    override val supportsProgress = true
    private val closed = AtomicBoolean()
    private val dispatchLock = Any()
    private val reported = ConcurrentHashMap.newKeySet<String>()
    private var native: ToastApi? = null
    private val shown = linkedMapOf<String, NativeNotification>()
    private val dismissed = linkedSetOf<String>()
    private val removed = linkedSetOf<String>()
    @Volatile private var activated: (String) -> Unit = {}

    private fun report(operation: String, error: String) {
        if (reported.add("$operation:$error")) runCatching { log("Windows toast $operation failed: $error") }
    }

    private fun guarded(operation: String, action: () -> Int): Int? = try {
        action().also { if (it < 0) report(operation, hresultString(it)) }
    } catch (e: Exception) {
        report(operation, if (e is ComException) hresultString(e.hresult) else "${e.javaClass.name}: ${e.message}")
        null
    } catch (e: LinkageError) {
        report(operation, "${e.javaClass.name}: ${e.message}")
        null
    }

    private fun getApi(): ToastApi? {
        native?.let { return it }
        var result: ToastApi? = null
        guarded("initialize") {
            val created = api()
            try {
                created.onActivated { args ->
                    if (!closed.get()) parseToastActivationArguments(args)?.let { id ->
                        if (toastTag(id) !in removed) guarded("activation") { activated(id); 0 }
                    }
                }
                native = created
                result = created
            } catch (e: Throwable) {
                runCatching { created.close() }
                throw e
            }
            0
        }
        return result
    }

    private fun enqueue(action: () -> Unit) = synchronized(dispatchLock) {
        if (!closed.get()) guarded("schedule") { thread(Runnable { if (!closed.get()) action() }); 0 }
        Unit
    }

    override fun show(n: NativeNotification) = enqueue {
        val service = getApi() ?: return@enqueue
        val tag = toastTag(n.id)
        val old = shown[tag]
        val update = old != null && old.isProgress && n.isProgress && old.title == n.title && old.urgent == n.urgent
        if (update && tag in dismissed) return@enqueue
        val hr = guarded(if (update) "update" else "show") {
            if (update) service.update(tag, TOAST_GROUP, toastData(n))
            else service.show(toastXml(n), tag, TOAST_GROUP, toastData(n))
        }
        if (hr != null && hr >= 0) {
            // Final history activation is self-describing; no completed activity or COM object needs retaining.
            if (n.isProgress) shown[tag] = n else shown.remove(tag)
            removed -= tag
            if (update && hr == 1) dismissed += tag else dismissed -= tag
            while (shown.size > 256) {
                val oldest = shown.keys.first()
                shown.remove(oldest)
                dismissed.remove(oldest)
                guarded("remove evicted progress") { service.remove(oldest, TOAST_GROUP) }
            }
            while (dismissed.size > 256) dismissed.remove(dismissed.first())
        }
    }

    override fun remove(id: String) = enqueue {
        val tag = toastTag(id)
        getApi()?.let { service -> guarded("remove") { service.remove(tag, TOAST_GROUP) } }
        shown.remove(tag)
        removed += tag
        while (removed.size > 256) removed.remove(removed.first())
        dismissed.remove(tag)
    }

    override fun setOnActivated(handler: (id: String) -> Unit) {
        activated = handler
        enqueue { getApi() }
    }

    override fun close() {
        synchronized(dispatchLock) {
            if (!closed.compareAndSet(false, true)) return
        }
        removed.clear()
        activated = {}
        try {
            guarded("schedule cleanup") {
                cleanupThread(Runnable {
                    try {
                        native?.let { service ->
                            shown.filterValues { it.isProgress }.keys.forEach { tag ->
                                guarded("remove") { service.remove(tag, TOAST_GROUP) }
                            }
                        }
                    } finally {
                        guarded("close") { native?.close(); 0 }
                        native = null
                        shown.clear()
                        dismissed.clear()
                    }
                })
                0
            }
        } finally {
            guarded("stop thread") { closeThread(); 0 }
        }
    }

    private val NativeNotification.isProgress: Boolean get() = progress != null || indeterminate
}
