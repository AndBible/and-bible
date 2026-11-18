@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.diagnostics

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import net.bible.sharedui.host.diagnostics.DiagnosticRecord
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticEvent
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticStore
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import net.bible.sharedcore.log.IosLogSink
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

internal fun diagnosticStore(directory: Path, clock: () -> Long): DiagnosticWriterStore {
    FileSystem.SYSTEM.createDirectories(directory)
    excludeFromBackup(directory)
    val store = RuntimeDiagnosticStore(directory, FileSystem.SYSTEM, ::excludeFromBackup, clock)
    return object : DiagnosticWriterStore {
        override fun append(record: DiagnosticRecord) = store.append(record)
        override fun appendBatch(records: List<DiagnosticRecord>) = store.appendBatch(records)
        override fun snapshot() = store.snapshot()
        override fun takeOmittedCount() = store.takeOmittedCount()
    }
}

private fun excludeFromBackup(path: Path) {
    try { NSURL.fileURLWithPath(path.toString()).setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null) }
    catch (_: Exception) { }
}

internal object IosRuntimeDiagnostics {
    private val clock: () -> Long = { (NSDate().timeIntervalSince1970 * 1000).toLong() }
    private val runId = NSUUID().UUIDString
    internal val testRunId: String get() = runId
    private val writer by lazy {
        IosDiagnosticWriter(runId, clock, Dispatchers.Default, {
            val root = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
            diagnosticStore(root.toPath() / "AndBible" / "Diagnostics", clock)
        }) { IosLogSink.current()?.invoke(it) }
    }

    private val installed = atomic(false)

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        val bundle = NSBundle.mainBundle
        fun value(key: String) = (bundle.objectForInfoDictionaryKey(key) as? String ?: "").take(100)
        emit(RuntimeDiagnosticEvent.RunStarted(value("CFBundleShortVersionString"), value("CFBundleVersion"), value("AndBibleCommit")))
    }

    fun emit(event: RuntimeDiagnosticEvent) = writer.emit(event)
    suspend fun snapshot(): String = writer.snapshot()
}
