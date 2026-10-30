package net.bible.sharedui.host.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log
import net.bible.sharedui.app.ports.DocumentRef

/**
 * Routes files handed to the app from outside: a database backup goes to the restore flow (the one the Welcome
 * screen's "Restore Database File" uses), everything else (module archives and the rest) to InstallZip.
 *
 * One batch is handled once: its backups are restored one after another, then the remaining files go to [install] in
 * a single call, and [route]'s `complete` runs exactly once, after the last restore result or, when files remain,
 * when InstallZip reports its result. A failed classification counts as "not a backup", so the file still reaches
 * InstallZip, which reports what is wrong with it.
 */
internal class IncomingFileRouter(
    private val scope: CoroutineScope,
    private val isBackup: suspend (DocumentRef) -> Boolean,
    private val restore: suspend (DocumentRef) -> Unit,
    private val install: (List<DocumentRef>, () -> Unit) -> Unit,
) {
    fun route(refs: List<DocumentRef>, complete: () -> Unit) {
        scope.launch {
            val backups = ArrayList<DocumentRef>()
            val others = ArrayList<DocumentRef>()
            for (ref in refs) {
                val backup = try { isBackup(ref) } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                catch (e: Exception) { Log.w(TAG, "Could not classify ${ref.uri}: ${e.message}"); false }
                (if (backup) backups else others).add(ref)
            }
            for (ref in backups) {
                try { restore(ref) } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
                catch (e: Exception) { Log.e(TAG, "Restore of ${ref.uri} failed", e) }
            }
            if (others.isEmpty()) complete() else install(others, complete)
        }
    }

    private companion object { const val TAG = "IncomingFileRouter" }
}

/**
 * The absolute path of a `file:` document reference (the `PickedSource` token of the file pickers), percent-decoded;
 * a reference that is already a plain absolute path is returned as is; null for any other scheme.
 */
internal fun incomingFilePath(ref: DocumentRef): String? {
    val uri = ref.uri
    val raw = when {
        uri.startsWith("file://") -> uri.removePrefix("file://").substringBefore('?').substringBefore('#')
        uri.startsWith("file:/") -> uri.removePrefix("file:").substringBefore('?').substringBefore('#')
        uri.startsWith("/") -> return uri.takeIf { '\u0000' !in it }
        else -> return null
    }
    val path = raw.let { if (it.startsWith("localhost/")) it.removePrefix("localhost") else it }
    if (!path.startsWith("/")) return null
    val bytes = ArrayList<Byte>(path.length)
    var i = 0
    while (i < path.length) {
        val c = path[i]
        if (c == '%' && i + 2 < path.length) {
            val v = path.substring(i + 1, i + 3).toIntOrNull(16)
            if (v != null) { bytes.add(v.toByte()); i += 3; continue }
        }
        bytes.addAll(c.toString().encodeToByteArray().toList()); i++
    }
    return bytes.toByteArray().decodeToString().takeIf { '\u0000' !in it }
}
