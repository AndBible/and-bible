package net.bible.sharedui.desktop.files

import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedcore.platform.desktop.files.InstanceHandOff
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.desktop.assembly.DesktopIncomingFiles
import net.bible.sharedui.desktop.assembly.DesktopWindowHandle

internal fun resolveIncomingArgs(args: List<String>, cwd: Path = Paths.get("").toAbsolutePath()): List<Path> =
    args.mapNotNull { runCatching { cwd.resolve(it).toAbsolutePath().normalize() }.getOrNull() }

/** Acceptance is thread-safe; binding, delivery and acknowledgement run on Main. */
internal class DesktopIncomingFileQueue(private val log: (String) -> Unit) {
    private class Binding(val open: (List<DocumentRef>, () -> Unit) -> Unit)
    private class Entry(val ref: DocumentRef)
    private val waiting = ArrayDeque<Entry>()
    private var owner: Binding? = null
    private var active: Entry? = null
    private var pumping = false

    fun accept(files: List<Path>) {
        val entries = files.mapNotNull { file ->
            if (file.fileName?.toString()?.substringAfterLast('.', "")?.lowercase() !in
                setOf("zip", "epub", "ttf", "csv", "jpg", "jpeg", "png", "webp", "sqlite3", "mybible", "bblx", "bbli", "abdb")) {
                log("Unsupported incoming file: $file")
                null
            } else Entry(DocumentRef(file.toUri().toString()))
        }
        synchronized(waiting) { waiting.addAll(entries) }
    }

    fun offer(files: List<Path>) { accept(files); pump() }
    fun deliverPending() = pump()

    fun bind(open: (List<DocumentRef>, () -> Unit) -> Unit, onFailure: () -> Unit): Any {
        val binding = Binding(open)
        owner = binding
        active = null
        pump()
        return binding
    }

    fun detach(binding: Any) {
        if (owner === binding) { owner = null; active = null }
    }

    private fun pump() {
        if (pumping) return
        pumping = true
        try {
            while (active == null) {
                val binding = owner ?: return
                val entry = synchronized(waiting) { waiting.firstOrNull() } ?: return
                active = entry
                binding.open(listOf(entry.ref)) {
                    if (owner === binding && active === entry) {
                        synchronized(waiting) { waiting.removeFirst() }
                        active = null
                        pump()
                    }
                }
            }
        } finally { pumping = false }
    }
}

/**
 * The second-launch hand-off listener that starts before the crash gate and the app graph exist. Files handed over
 * while nobody is attached are queued (the second instance gets its "delivered" answer and may exit), and the app graph's
 * [DesktopIncomingFilesImpl] attaches later through [listen] and receives them all. [DesktopIncomingFilesImpl] then
 * hands them to the shared receive path (`AppHostPlatform.receiveIncomingFiles`).
 */
class DesktopEarlyIncomingFiles private constructor() : AutoCloseable {
    private var listener: AutoCloseable = AutoCloseable {}
    private val lock = Any()
    private var pending = ArrayList<Path>()
    private var sink: ((List<Path>) -> Unit)? = null

    // Delivery happens under [lock] (the sinks only enqueue), so files queued before attaching are always handed over
    // before any file accepted afterwards: FIFO across the hand-over.
    private fun accept(files: List<Path>) {
        synchronized(lock) {
            val target = sink
            if (target == null) pending.addAll(files) else target(files)
        }
    }

    /** The `listen` seam of [DesktopIncomingFilesImpl]: attaches [onFiles], first handing it everything queued so far. */
    fun listen(@Suppress("UNUSED_PARAMETER") dataDir: Path, onFiles: (List<Path>) -> Unit): AutoCloseable {
        synchronized(lock) {
            sink = onFiles
            val queued = pending
            pending = ArrayList()
            if (queued.isNotEmpty()) onFiles(queued)
        }
        return AutoCloseable { synchronized(lock) { if (sink === onFiles) sink = null } }
    }

    override fun close() {
        listener.close()
        val dropped = synchronized(lock) { pending.size.also { pending.clear() } }
        if (dropped > 0) Log.w("DesktopIncomingFiles", "Dropped $dropped incoming file(s) queued before the app graph came up (quit at the crash gate)")
    }

    companion object {
        @Volatile private var installed: DesktopEarlyIncomingFiles? = null

        /**
         * Starts listening in [dataDir] and makes the listener the one [DesktopIncomingFilesImpl] attaches to. A socket
         * that cannot be bound costs only the second-launch hand-off, never the app start.
         */
        fun install(
            dataDir: Path,
            listen: (Path, (List<Path>) -> Unit) -> AutoCloseable = InstanceHandOff::listen,
        ): DesktopEarlyIncomingFiles? {
            // Created before listening: a connection that arrives as listen() returns always has a queue to land in.
            val early = DesktopEarlyIncomingFiles()
            early.listener = try {
                listen(dataDir) { files -> early.accept(files) }
            } catch (e: Exception) {
                Log.w("DesktopIncomingFiles", "Second-launch file hand-off unavailable: ${e.message}")
                return null
            }
            installed = early
            return early
        }

        /** The `listen` function for [DesktopIncomingFilesImpl]: the installed early listener, else a fresh one. */
        internal fun listenFunction(): (Path, (List<Path>) -> Unit) -> AutoCloseable =
            installed?.let { it::listen } ?: InstanceHandOff::listen

        fun closeInstalled() { installed?.close(); installed = null }
    }
}

internal class DesktopIncomingFilesImpl(
    dataDir: Path,
    args: List<String>,
    private val window: DesktopWindowHandle,
    exitTasks: DesktopExitTasks,
    listen: (Path, (List<Path>) -> Unit) -> AutoCloseable = DesktopEarlyIncomingFiles.listenFunction(),
) : DesktopIncomingFiles, AutoCloseable {
    private val queue = DesktopIncomingFileQueue { Log.w("DesktopIncomingFiles", it) }.apply {
        offer(resolveIncomingArgs(args))
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // A socket that cannot be bound (a path too long for AF_UNIX, a stale socket Windows keeps locked) costs only the
    // second-launch hand-off, never the app start (W1 review).
    private val listener: AutoCloseable = try {
        listen(dataDir) { files ->
            queue.accept(files)
            scope.launch {
                queue.deliverPending()
                window.toFront()
            }
        }
    } catch (e: Exception) {
        Log.w("DesktopIncomingFiles", "Second-launch file hand-off unavailable: ${e.message}")
        AutoCloseable {}
    }
    private val unregister = exitTasks.register("incoming files") { close() }

    override suspend fun receive(open: (List<DocumentRef>, () -> Unit) -> Unit, onFailure: () -> Unit) =
        withContext(Dispatchers.Main) {
            val binding = queue.bind(open, onFailure)
            try { awaitCancellation() } finally { queue.detach(binding) }
        }

    override fun close() { unregister(); listener.close(); scope.cancel() }
}
