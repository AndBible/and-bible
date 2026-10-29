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
        assertTrue(copyFileReplacing(src, dst, freeBytes = ::iosFreeSpaceBytes))
        assertEquals("hello", fs.read(dst) { readUtf8() })
        assertTrue(fs.exists(src), "source is copied, not moved")
    }

    @Test
    fun insufficientSpacePreservesExistingTarget() {
        val src = write("src.tmp", "large module")
        val dst = write("dst.zip", "previous module")
        assertFalse(copyFileReplacing(src, dst, freeBytes = { 1L }))
        assertEquals("previous module", fs.read(dst) { readUtf8() })
        assertFalse(fs.exists(root / "dst.zip.tmp"))
    }

    @Test
    fun overwritesAnExistingTarget() {
        val src = write("src.tmp", "new")
        val dst = write("dst.zip", "old content that is longer")
        assertTrue(copyFileReplacing(src, dst, freeBytes = ::iosFreeSpaceBytes))
        assertEquals("new", fs.read(dst) { readUtf8() })
    }

    @Test
    fun missingSourceReturnsFalseWithoutCreatingTarget() {
        val dst = root / "dst.zip"
        assertFalse(copyFileReplacing(root / "nope", dst, freeBytes = ::iosFreeSpaceBytes))
        assertFalse(fs.exists(dst))
    }

    @Test
    fun leavesNoTempFileBehind() {
        val src = write("src.tmp", "x")
        val dst = root / "out" / "dst.zip"
        assertTrue(copyFileReplacing(src, dst, freeBytes = ::iosFreeSpaceBytes))
        assertFalse(fs.exists(root / "out" / "dst.zip.tmp"))
    }

    @Test
    fun failedCopyKeepsTheExistingTarget() {
        val dst = write("dst.zip", "existing module")
        val src = root / "missing-source"
        assertFalse(copyFileReplacing(src, dst, freeBytes = ::iosFreeSpaceBytes))
        assertEquals("existing module", fs.read(dst) { readUtf8() })
    }
}
