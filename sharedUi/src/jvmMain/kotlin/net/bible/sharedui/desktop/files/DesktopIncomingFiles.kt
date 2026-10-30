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
                setOf("zip", "epub", "ttf", "csv", "jpg", "jpeg", "png", "webp", "sqlite3", "mybible", "bblx", "bbli")) {
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

internal class DesktopIncomingFilesImpl(
    dataDir: Path,
    args: List<String>,
    private val window: DesktopWindowHandle,
    exitTasks: DesktopExitTasks,
    listen: (Path, (List<Path>) -> Unit) -> AutoCloseable = InstanceHandOff::listen,
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
