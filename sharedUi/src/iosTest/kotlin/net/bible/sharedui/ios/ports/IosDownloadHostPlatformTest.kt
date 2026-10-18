package net.bible.sharedui.ios.ports

import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosDownloadHostPlatformTest {
    private val fs = FileSystem.SYSTEM
    private val root = (NSTemporaryDirectory().trimEnd('/') + "/copyfile-" + NSUUID().UUIDString).toPath()

    @AfterTest
    fun cleanup() { fs.deleteRecursively(root) }

    private fun write(name: String, text: String) = (root / name).also {
        fs.createDirectories(root)
        fs.write(it) { writeUtf8(text) }
    }

    @Test
    fun copiesAndCreatesParentDirectories() {
        val src = write("src.tmp", "hello")
        val dst = root / "a" / "b" / "dst.zip"
        assertTrue(IosDownloadHostPlatform().copyFile(src, dst))
        assertEquals("hello", fs.read(dst) { readUtf8() })
        assertTrue(fs.exists(src), "source is copied, not moved")
    }

    @Test
    fun overwritesAnExistingTarget() {
        val src = write("src.tmp", "new")
        val dst = write("dst.zip", "old content that is longer")
        assertTrue(copyFileReplacing(src, dst))
        assertEquals("new", fs.read(dst) { readUtf8() })
    }

    @Test
    fun missingSourceReturnsFalseWithoutCreatingTarget() {
        val dst = root / "dst.zip"
        assertFalse(copyFileReplacing(root / "nope", dst))
        assertFalse(fs.exists(dst))
    }
}
