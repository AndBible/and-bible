@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.ios.ports

import net.bible.sharedui.app.ports.DocumentRef
import okio.FileSystem
import okio.ForwardingSink
import okio.Path.Companion.toPath
import okio.Sink
import platform.Foundation.NSURL

/** Holds the picker URL's security scope for the entire write, including flush/close. */
@kotlin.native.HiddenFromObjC
class IosBookmarkCsvFiles(private val files: FileSystem = FileSystem.SYSTEM) {
    fun openSink(ref: DocumentRef): Sink {
        val uri = ref.uri
        require(uri.startsWith("/") || uri.startsWith("file:")) { "Not a local document: $uri" }
        val url = if (uri.startsWith("file:")) {
            NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri.removePrefix("file://"))
        } else NSURL.fileURLWithPath(uri)
        val path = requireNotNull(url.path) { "Invalid document URL: $uri" }.toPath()
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            return object : ForwardingSink(files.sink(path)) {
                private var closed = false
                override fun close() {
                    if (closed) return
                    closed = true
                    try { super.close() }
                    finally { if (scoped) url.stopAccessingSecurityScopedResource() }
                }
            }
        } catch (e: Throwable) {
            if (scoped) url.stopAccessingSecurityScopedResource()
            throw e
        }
    }
}
