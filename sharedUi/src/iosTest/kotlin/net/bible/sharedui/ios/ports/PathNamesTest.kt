@file:Suppress("CAST_NEVER_SUCCEEDS")

package net.bible.sharedui.ios.ports

import platform.Foundation.NSString
import platform.Foundation.lastPathComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedui.host.ports.lastPathComponent

/** Pins [lastPathComponent] to Foundation's `NSString.lastPathComponent` over the inputs that can reach it. */
class PathNamesTest {
    @Test
    fun matchesFoundation() {
        for (path in listOf("/a/b.zip", "/a/b/", "/a/b///", "a//b", "name", "/", "///", "", "/tmp/Raamattu 1938 (fi).zip",
            "/Users/Ääkkönen/päivä x\\y.epub", "/a/..", "/a/.hidden", "/a/b:c")) {
            assertEquals((path as NSString).lastPathComponent, lastPathComponent(path), "path '$path'")
        }
    }
}
