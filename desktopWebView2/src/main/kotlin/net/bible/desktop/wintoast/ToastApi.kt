package net.bible.desktop.wintoast

/** Owner-thread WinRT boundary. Negative results are HRESULT failures; update returns S_FALSE for a missing toast. */
interface ToastApi : AutoCloseable {
    fun show(xml: String, tag: String, group: String, data: Map<String, String>): Int
    fun update(tag: String, group: String, data: Map<String, String>): Int
    fun remove(tag: String, group: String): Int
    fun onActivated(handler: (tag: String) -> Unit)
    /** Cleanup errors are diagnostics, not a reversal of an already successful Show/update result. */
    fun onCleanupFailure(handler: (operation: String, hresult: Int) -> Unit) = Unit
    override fun close() = Unit
}
