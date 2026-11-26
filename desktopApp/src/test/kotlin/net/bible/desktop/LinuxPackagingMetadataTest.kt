package net.bible.desktop

import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxPackagingMetadataTest {
    private val linux = File("packaging/linux")
    private fun metadata() = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(File(linux, "org.andbible.AndBible.metainfo.xml"))
    private fun value(tag: String) = metadata().getElementsByTagName(tag).item(0).textContent.trim()

    @Test fun metainfoIdMatchesDesktopFileAndAppId() {
        val id = value("id")
        assertEquals("org.andbible.AndBible", id)
        assertEquals("$id.desktop", File(linux, "$id.desktop").name)
        check(File(linux, "$id.desktop").isFile)
    }

    @Test fun metainfoContactIsHelpAddress() {
        assertEquals("help.andbible@gmail.com", value("update_contact"))
        val developer = metadata().getElementsByTagName("developer").item(0)
        assertEquals("org.andbible", developer.attributes.getNamedItem("id").nodeValue)
        assertEquals("AndBible", developer.textContent.trim())
    }

    @Test fun desktopDoesNotClaimGenericMimeTypes() {
        val mimeTypes = File(linux, "org.andbible.AndBible.desktop").readLines()
            .filter { it.startsWith("MimeType=") }
            .flatMap { it.substringAfter('=').split(';') }
            .filter { it.isNotBlank() }.toSet()
        // A generic handler can become the default even without an explicit default-app request.
        // No AndBible-specific MIME type is currently installed by the Linux packages.
        assertEquals(emptySet<String>(), mimeTypes, "Linux must not advertise generic file handlers")
    }

    @Test fun desktopKeepsExplicitFileOpeningAvailable() {
        val command = File(linux, "org.andbible.AndBible.desktop").readLines()
            .single { it.startsWith("Exec=") }.substringAfter('=')
        assertTrue("%F" in command.split(' '), "Open with must pass selected file paths to AndBible")
    }

    @Test fun metainfoReleaseVersionEqualsDesktopVersion() {
        val props = Properties().apply { File("../version.properties").inputStream().use { load(it) } }
        val expected = "${props.getProperty("versionMajorMinor")}.${props.getProperty("versionCode")}"
        val release = metadata().getElementsByTagName("release").item(0).attributes.getNamedItem("version").nodeValue
        assertEquals(expected, release)
    }
}
