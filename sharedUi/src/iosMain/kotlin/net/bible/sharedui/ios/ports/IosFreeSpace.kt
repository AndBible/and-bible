@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber

/** Free bytes on the volume holding [path] (`NSFileSystemFreeSize`), or null when it cannot be read. */
@kotlin.native.HiddenFromObjC
fun iosFreeSpaceBytes(path: String): Long? =
    (NSFileManager.defaultManager.attributesOfFileSystemForPath(path, null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue
