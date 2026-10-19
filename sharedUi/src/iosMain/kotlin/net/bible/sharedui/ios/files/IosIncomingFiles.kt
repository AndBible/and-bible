@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.files

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.sharedui.app.ports.DocumentRef
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

private fun incomingUrl(value: String): NSURL? = NSURL.URLWithString(value)
    ?.takeIf { it.isFileURL() && it.path?.startsWith("/") == true }

/** Main-thread queue. A host owns one delivery until its result acknowledges it, not just until navigation. */
internal class IosIncomingFileQueue(
    private val scope: CoroutineScope,
    private val prepare: (String) -> (suspend () -> DocumentRef),
) {
    private class Entry(val copy: (suspend () -> DocumentRef)?) {
        var ref: DocumentRef? = null
        var failed = copy == null
    }
    private class Binding(val open: (List<DocumentRef>, () -> Unit) -> Unit, val failure: () -> Unit)
    private val waiting = ArrayDeque<Entry>()
    private var binding: Binding? = null
    private var active: Entry? = null
    private var copying = false
    private var pumping = false

    fun offer(url: String) {
        if (incomingUrl(url) == null) return
        // Acquire provider access now, even if an earlier file is still copying or awaiting confirmation.
        val entry = try { Entry(prepare(url)) } catch (_: Exception) { Entry(null) }
        waiting.addLast(entry)
        copyNext()
        pump()
    }

    fun bind(open: (List<DocumentRef>, () -> Unit) -> Unit, onFailure: () -> Unit): Any {
        val owner = Binding(open, onFailure)
        binding = owner
        active = null
        pump()
        return owner
    }

    fun detach(owner: Any) {
        if (binding !== owner) return
        binding = null
        active = null
    }

    private fun copyNext() {
        if (copying) return
        val entry = waiting.firstOrNull { it.ref == null && !it.failed } ?: return
        copying = true
        scope.launch {
            try {
                entry.ref = entry.copy!!.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                entry.failed = true
            } finally {
                copying = false
            }
            pump()
            copyNext()
        }
    }

    private fun pump() {
        if (pumping) return
        pumping = true
        try {
            while (active == null) {
                val owner = binding ?: return
                val entry = waiting.firstOrNull() ?: return
                if (entry.failed) {
                    waiting.removeFirst()
                    owner.failure()
                    continue
                }
                val ref = entry.ref ?: return
                active = entry
                owner.open(listOf(ref)) {
                    if (binding === owner && active === entry) {
                        waiting.removeFirst()
                        active = null
                        pump()
                    }
                }
                // A synchronous answer/rebind may have made more work ready. Do not recurse into the pump.
            }
        } finally { pumping = false }
    }
}

/**
 * Copies exact Foundation URLs, never external paths through Okio. Each arrival gets its own safe filename.
 * Inputs survive navigation cancellation because the process installer may still be acquiring them. Disk use is
 * the total received bytes this process; the next process removes only this private namespace's old directories.
 */
internal class IosIncomingFileStaging(
    private val namespace: NSURL = NSURL.fileURLWithPath(NSTemporaryDirectory() + "andbible-incoming-files"),
    private val processId: String = NSUUID().UUIDString,
    private val startAccess: (NSURL) -> Boolean = { it.startAccessingSecurityScopedResource() },
    private val stopAccess: (NSURL) -> Unit = { it.stopAccessingSecurityScopedResource() },
) {
    private var initialized = false

    fun prepare(value: String): suspend () -> DocumentRef {
        val source = requireNotNull(incomingUrl(value))
        val scoped = startAccess(source)
        return {
            try {
                withContext(Dispatchers.Default) {
                    val manager = NSFileManager.defaultManager
                    val process = namespace.URLByAppendingPathComponent(processId)!!
                    if (!initialized) {
                        check(manager.createDirectoryAtURL(namespace, true, null, null))
                        // Names are generated internally; never enumerate or clean another cache root.
                        val children = manager.contentsOfDirectoryAtURL(namespace, null, 0u, null).orEmpty()
                        for (child in children) {
                            val url = child as NSURL
                            if (url.lastPathComponent != processId) check(manager.removeItemAtURL(url, null))
                        }
                        check(manager.createDirectoryAtURL(process, true, null, null))
                        initialized = true
                    }
                    val arrival = process.URLByAppendingPathComponent(NSUUID().UUIDString)!!
                    check(manager.createDirectoryAtURL(arrival, true, null, null))
                    val name = source.lastPathComponent.orEmpty()
                        .replace(Regex("[/\\\\\\x00-\\x1f\\x7f]"), "_")
                        .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "incoming"
                    val destination = arrival.URLByAppendingPathComponent(name)!!
                    try {
                        check(manager.copyItemAtURL(source, destination, null)) { "Cannot copy incoming file" }
                        DocumentRef(requireNotNull(destination.absoluteString))
                    } catch (e: Throwable) {
                        manager.removeItemAtURL(arrival, null)
                        throw e
                    }
                }
            } finally { if (scoped) stopAccess(source) }
        }
    }
}

/** Process-wide ingress, safe before Koin or the first composition. All queue mutation is main-thread only. */
@kotlin.native.HiddenFromObjC
object IosIncomingFiles {
    private val staging by lazy { IosIncomingFileStaging() }
    private val queue by lazy { IosIncomingFileQueue(MainScope(), staging::prepare) }

    fun offer(url: String) {
        checkMain()
        queue.offer(url)
    }

    /** Facade for non-session consumers; production binds with a result acknowledgment instead. */
    fun drainInto(open: (List<DocumentRef>) -> Unit) {
        bind({ refs, done -> open(refs); done() }, {})
    }

    internal fun bind(open: (List<DocumentRef>, () -> Unit) -> Unit, onFailure: () -> Unit): Any {
        checkMain()
        return queue.bind(open, onFailure)
    }

    internal fun detach(owner: Any) {
        checkMain()
        queue.detach(owner)
    }

    private fun checkMain() = check(NSThread.isMainThread) { "IosIncomingFiles is main-thread only" }
}
