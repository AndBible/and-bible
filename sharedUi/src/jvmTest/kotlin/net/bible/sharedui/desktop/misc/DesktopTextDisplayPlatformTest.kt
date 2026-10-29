package net.bible.sharedui.desktop.misc

import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath

class DesktopTextDisplayPlatformTest {
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
