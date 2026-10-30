package net.bible.desktop

import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test fun desktopMimeTypesMatchFileAssociations() {
        val mime = File(linux, "org.andbible.AndBible.desktop").readLines().single { it.startsWith("MimeType=") }
            .substringAfter('=').split(';').filter { it.isNotEmpty() }.toSet()
        val mapping = mapOf(
            ".zip" to "application/zip", ".epub" to "application/epub+zip", ".csv" to "text/csv",
            ".jpg" to "image/jpeg", ".jpeg" to "image/jpeg", ".png" to "image/png",
            ".webp" to "image/webp", ".sqlite3" to "application/vnd.sqlite3",
        )
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val manifest = factory.newDocumentBuilder().parse(File("build/windows-msix/staging/AppxManifest.xml"))
        val nodes = manifest.getElementsByTagNameNS("*", "FileType")
        val windowsMime = (0 until nodes.length).map { mapping.getValue(nodes.item(it).textContent) }.toSet()
        // Windows reserves .ttf; Linux registers it in addition to the Windows associations.
        assertEquals(windowsMime + "font/ttf", mime)
    }

    @Test fun metainfoReleaseVersionEqualsDesktopVersion() {
        val props = Properties().apply { File("../version.properties").inputStream().use { load(it) } }
        val expected = "${props.getProperty("versionMajorMinor")}.${props.getProperty("versionCode")}"
        val release = metadata().getElementsByTagName("release").item(0).attributes.getNamedItem("version").nodeValue
        assertEquals(expected, release)
    }
}
