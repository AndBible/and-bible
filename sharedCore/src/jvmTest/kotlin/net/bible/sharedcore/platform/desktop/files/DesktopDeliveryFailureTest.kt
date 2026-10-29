package net.bible.sharedcore.platform.desktop.files

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.SaveOrShare
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopDeliveryFailureTest {
    private fun chooser(target: Path) = object : FileChooserBackend {
        override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? = null
        override suspend fun save(suggestedName: String, filters: List<FileFilter>) = target
    }

    @Test
    fun midStreamFailureDeletesPartAndPreservesTarget() = runTest {
        val directory = Files.createTempDirectory("delivery-failure")
        val source = Files.writeString(directory.resolve("source"), "new data")
        val target = Files.writeString(directory.resolve("target"), "original")
        try {
            val delivery = DesktopFileDelivery(chooser(target), openInput = { path ->
                object : FilterInputStream(Files.newInputStream(path)) {
                    var calls = 0
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (calls++ > 0) throw IOException("injected failure")
                        return super.read(buffer, offset, length)
                    }
                }
            })
            assertEquals(DeliveryResult.FAILED, delivery.deliver(source.toString(), "target", SaveOrShare.SAVE, "application/zip", "application/zip", null, null, ""))
            assertEquals("original", Files.readString(target))
            assertFalse(Files.exists(directory.resolve("target.part")))
        } finally { Files.deleteIfExists(source); Files.deleteIfExists(target); Files.deleteIfExists(directory) }
    }

    @Test
    fun portalInputFailureDoesNotTouchExistingTarget() = runTest {
        val directory = Files.createTempDirectory("portal-failure")
        val source = Files.writeString(directory.resolve("source"), "new")
        val target = Files.writeString(directory.resolve("target"), "original")
        try {
            val delivery = DesktopFileDelivery(chooser(target), isPortalPath = { true }, openInput = { path ->
                if (path != source) throw IOException("staged input unavailable")
                Files.newInputStream(path)
            })
            assertEquals(DeliveryResult.FAILED, delivery.deliver(source.toString(), "target", SaveOrShare.SAVE, "application/zip", "application/zip", null, null, ""))
            assertEquals("original", Files.readString(target))
        } finally { Files.deleteIfExists(source); Files.deleteIfExists(target); Files.deleteIfExists(directory) }
    }

    @Test
    fun portalMidStreamFailureTruncatesPartialTarget() = runTest {
        val directory = Files.createTempDirectory("portal-failure")
        val source = Files.writeString(directory.resolve("source"), "new data")
        val target = Files.writeString(directory.resolve("target"), "original")
        try {
            val delivery = DesktopFileDelivery(chooser(target), isPortalPath = { true }, openInput = { path ->
                val input: InputStream = Files.newInputStream(path)
                if (path == source) input else object : FilterInputStream(input) {
                    var calls = 0
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        if (calls++ > 0) throw IOException("injected failure")
                        return super.read(buffer, offset, length)
                    }
                }
            })
            assertEquals(DeliveryResult.FAILED, delivery.deliver(source.toString(), "target", SaveOrShare.SAVE, "application/zip", "application/zip", null, null, ""))
            assertEquals("", Files.readString(target))
        } finally { Files.deleteIfExists(source); Files.deleteIfExists(target); Files.deleteIfExists(directory) }
    }
}
