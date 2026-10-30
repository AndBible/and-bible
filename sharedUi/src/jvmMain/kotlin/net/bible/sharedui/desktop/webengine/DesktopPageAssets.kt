package net.bible.sharedui.desktop.webengine

import java.nio.file.Path

/** One file an engine serves at the bundle origin. */
data class BundleResource(val file: Path, val mimeType: String, val fromBundle: Boolean = true) {
    /**
     * The `Content-Type` header. Text is always UTF-8 and says so: the injected scripts can push the page's
     * `<meta charset>` past the 1024-byte prescan window, and scripts without a charset inherit the document's.
     */
    val contentType: String
        get() = if (mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "image/svg+xml") {
            "$mimeType; charset=utf-8"
        } else {
            mimeType
        }
}

/**
 * Page assets served at the bundle origin next to the bundle's own files (module files, background images, ...;
 * Android's `WebViewAssetLoader` path handlers). Engine-neutral: an engine asks it for any path no bundle file serves.
 */
fun interface DesktopPageAssets {
    /** The file for the origin-relative [path] (decoded, with a leading `/`, no query), or null for a 404. */
    fun resolve(path: String): BundleResource?

    companion object {
        /** Serves nothing. */
        val None = DesktopPageAssets { null }
    }
}
