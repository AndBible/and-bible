package net.bible.sharedlogic.addons

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import org.crosswire.common.util.KUri

class BackgroundImageInventoryTest {
    @Test fun windowsDriveUriResolvesToTheNativeDriveRatherThanASlashPrefixedPath() {
        val file = org.crosswire.common.util.NetUtil.filePathOf("/C:/Users/Test User/100% #/night.png", "\\")
        assertEquals("C:/Users/Test User/100% #/night.png".toPath(), file)
    }

    @Test fun importedImageLocationRoundTripsThroughAFileUri() {
        val root = Files.createTempDirectory("background space # ")
        val file = root.resolve("night # 100%.png")
        try {
            Files.write(file, byteArrayOf(1, 2, 3))
            val metadata = FakeAddonMetaData("BGIMG_night", null,
                mapOf("AndBibleProvidesBackgroundImage" to listOf("Night;night # 100%.png")), emptyMap())
            metadata.location = KUri(root.toUri().toString())
            val scanner = SharedAddonScanner({ listOf(FakeAddonBook(metadata)) })
            val image = scanner.backgroundImages().getValue("BGIMG_night")
            assertEquals(file.toString().toPath(), image.path)
            Files.delete(file)
            assertTrue(scanner.backgroundImages().isEmpty(), "A missing image must not keep the colours editor from opening")
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(root)
        }
    }
}
