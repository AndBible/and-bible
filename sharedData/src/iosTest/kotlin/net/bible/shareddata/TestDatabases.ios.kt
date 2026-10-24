@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.shareddata

import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.Foundation.NSTemporaryDirectory
import platform.posix.mkdtemp

actual fun testDatabaseDir(): String = memScoped {
    val template = (NSTemporaryDirectory() + "shareddata-test-XXXXXX").cstr.getPointer(this)
    checkNotNull(mkdtemp(template)) { "Could not create test database directory" }.toKString()
}
