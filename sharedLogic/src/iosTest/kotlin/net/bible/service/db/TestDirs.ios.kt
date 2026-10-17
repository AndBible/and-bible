@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.service.db

import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.Foundation.NSTemporaryDirectory
import platform.posix.mkdtemp

actual fun newTestDir(): String = memScoped {
    val template = (NSTemporaryDirectory() + "sharedlogic-test-XXXXXX").cstr.getPointer(this)
    checkNotNull(mkdtemp(template)) { "Could not create test directory" }.toKString()
}
