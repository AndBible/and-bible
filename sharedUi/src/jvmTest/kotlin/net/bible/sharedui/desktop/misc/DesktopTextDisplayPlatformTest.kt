package net.bible.sharedui.desktop.misc

import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath

object DesktopMetadataProbe {
    @JvmStatic fun main(args: Array<String>) {
        val port = DesktopTextDisplayPlatform(inventory = {
            mapOf("metadata" to args[0].toPath(), "plain" to args[1].toPath())
        }, windowPixels = { 50 to 40 }, onModulesChanged = {})
        check(port.thumbnail("plain") != null) { "plain PNG failed" }
        check(port.thumbnail("metadata") != null) { "compressed metadata PNG failed" }
    }
}

class DesktopTextDisplayPlatformTest {
    @Test fun compressedMetadataDoesNotExhaustDecodeHeap() {
        val path = Files.createTempFile("metadata", ".png")
        val png = java.io.ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        val compressed = java.io.ByteArrayOutputStream().also { bytes ->
            java.util.zip.DeflaterOutputStream(bytes).use { it.write(ByteArray(256 * 1024) { 65 }) }
        }.toByteArray()
        val chunk = byteArrayOf('k'.code.toByte(), 0, 0) + compressed
        java.io.DataOutputStream(Files.newOutputStream(path)).use { out ->
            out.write(png, 0, 33)
            repeat(1000) {
                val type = "zTXt".toByteArray(Charsets.US_ASCII)
                val crc = java.util.zip.CRC32().apply { update(type); update(chunk) }
                out.writeInt(chunk.size); out.write(type); out.write(chunk); out.writeInt(crc.value.toInt())
            }
            out.write(png, 33, png.size - 33)
        }
        val plain = Files.createTempFile("plain", ".png")
        Files.write(plain, png)
        val process = ProcessBuilder(
            java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
            "-Xmx64m", "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
            "net.bible.sharedui.desktop.misc.DesktopMetadataProbe", path.toString(), plain.toString(),
        ).redirectErrorStream(true).start()
        val finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly().waitFor()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(finished && process.exitValue() == 0, "metadata decode failed: $output")
    }

    @Test fun corruptImageYieldsNoBackground() {
        val path = Files.createTempFile("image", ".png")
        Files.writeString(path, "not an image")
        val port = DesktopTextDisplayPlatform(inventory = { mapOf("module" to path.toString().toPath()) }, windowPixels = { 50 to 40 }, onModulesChanged = {})
        assertNull(port.thumbnail("module"))
    }

    @Test fun moduleChangeInvalidatesMissesAndImages() {
        val path = Files.createTempFile("image", ".png")
        var inventory = emptyMap<String, okio.Path>()
        var invalidate: () -> Unit = {}
        val port = DesktopTextDisplayPlatform(inventory = { inventory }, windowPixels = { 50 to 40 }, onModulesChanged = { invalidate = it })
        assertNull(port.thumbnail("module"))
        ImageIO.write(BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", path.toFile())
        inventory = mapOf("module" to path.toString().toPath())
        invalidate()
        assertNotNull(port.thumbnail("module"))
        inventory = emptyMap()
        invalidate()
        assertNull(port.thumbnail("module"))
    }

    @Test fun invalidationDuringLoadDoesNotCacheStaleImage() {
        val path = Files.createTempFile("image", ".png")
        ImageIO.write(BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", path.toFile())
        val entered = java.util.concurrent.CountDownLatch(1)
        val resume = java.util.concurrent.CountDownLatch(1)
        var invalidate: () -> Unit = {}
        var installed = true
        val port = DesktopTextDisplayPlatform(inventory = {
            val result = if (installed) mapOf("module" to path.toString().toPath()) else emptyMap()
            entered.countDown()
            check(resume.await(5, java.util.concurrent.TimeUnit.SECONDS))
            result
        }, windowPixels = { 50 to 40 }, onModulesChanged = { invalidate = it })
        val worker = Thread { port.thumbnail("module") }
        worker.start()
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
        installed = false
        invalidate()
        resume.countDown()
        worker.join(5000)
        assertTrue(!worker.isAlive)
        assertNull(port.thumbnail("module"))
    }

    @Test fun cancellationFromInventoryPropagates() {
        val port = DesktopTextDisplayPlatform(inventory = { throw kotlinx.coroutines.CancellationException("cancel") }, onModulesChanged = {})
        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> { port.thumbnail("module") }
    }

    @Test fun oversizedImageIsDownscaled() {
        val path = Files.createTempFile("image", ".png")
        ImageIO.write(BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB), "png", path.toFile())
        val port = DesktopTextDisplayPlatform(inventory = { mapOf("module" to path.toString().toPath()) }, windowPixels = { 50 to 40 }, onModulesChanged = {})
        val bitmap = assertNotNull(port.thumbnail("module"))
        assertTrue(bitmap.width <= 100 && bitmap.height <= 80)
    }
}
