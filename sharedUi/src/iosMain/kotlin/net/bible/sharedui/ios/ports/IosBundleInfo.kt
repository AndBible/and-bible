@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/** The app's `CFBundleShortVersionString`, or an empty string when the bundle has none. */
@kotlin.native.HiddenFromObjC
fun iosBundleVersionName(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

/** The bundled `<name>.html` resource as text; throws when it is missing or unreadable. */
@kotlin.native.HiddenFromObjC
fun iosBundledHtml(name: String): String {
    val path = checkNotNull(NSBundle.mainBundle.pathForResource(name, "html")) { "Missing bundled HTML: $name" }
    return checkNotNull(NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)) { "Unreadable bundled HTML: $name" }
}
