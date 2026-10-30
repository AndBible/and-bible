package net.bible.desktop.webkitgtk

import java.nio.file.Path
import net.bible.sharedui.desktop.webengine.kcef.BUNDLE_ORIGIN
import net.bible.sharedui.desktop.webengine.kcef.BundleRequestHandler

/** WebKitGTK cannot intercept `https`, so the bundle is served from this custom scheme (registered secure + CORS-enabled). */
const val BUNDLE_SCHEME = "andbible"

/** The origin of the bundle. */
const val BUNDLE_BASE = "andbible://bibleview"

/** Whether [uri] is at [BUNDLE_BASE] exactly (look-alike hosts, other schemes and ports are refused). */
fun isAtBundleBase(uri: String?): Boolean {
    if (uri == null || !uri.startsWith(BUNDLE_BASE)) return false
    return uri.length == BUNDLE_BASE.length || uri[BUNDLE_BASE.length] in "/?#"
}

/** Maps `andbible://bibleview/...` requests to bundle files by reusing D1's [BundleRequestHandler] rules. */
class BundleSchemeHandler(bundleDir: Path) {
    private val delegate = BundleRequestHandler(bundleDir)

    /** The file and MIME type for [uri], or null when it is not at [BUNDLE_BASE], escapes the bundle or is missing. */
    fun resolve(uri: String): Pair<Path, String>? {
        if (!isAtBundleBase(uri)) return null
        var rest = uri.substring(BUNDLE_BASE.length)
        if (!rest.startsWith("/")) rest = "/$rest" // bare base, or "?q"/"#f" directly after the host
        val resource = delegate.resolve(BUNDLE_ORIGIN + rest) ?: return null
        return resource.file to resource.mimeType
    }
}
