package net.bible.desktop

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsManifestTest {
    @Test
    fun generatedManifestHasIdentityVersionAndFileAssociations() {
        val manifest = File("build/windows-msix/staging/AppxManifest.xml")
        assertTrue(manifest.isFile, "Generated Windows manifest is missing")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val xml = factory.newDocumentBuilder().parse(manifest)
        val identity = xml.getElementsByTagNameNS("*", "Identity").item(0) as org.w3c.dom.Element
        assertEquals("AndBible.Desktop", identity.getAttribute("Name"))
        assertEquals("CN=AndBible-Unsigned", identity.getAttribute("Publisher"))
        val properties = java.util.Properties().apply { File("../version.properties").inputStream().use { load(it) } }
        assertEquals("${properties.getProperty("versionMajorMinor")}.${properties.getProperty("versionCode")}.0", identity.getAttribute("Version"))
        val capabilities = xml.getElementsByTagNameNS("*", "Capability")
        assertTrue((0 until capabilities.length).any { (capabilities.item(it) as org.w3c.dom.Element).getAttribute("Name") == "runFullTrust" })
        val fileTypes = xml.getElementsByTagNameNS("*", "FileType")
        val extensions = (0 until fileTypes.length).map { fileTypes.item(it).textContent }.toSet()
        assertTrue(extensions.containsAll(setOf(".zip", ".epub", ".ttf", ".csv", ".jpg", ".jpeg", ".png", ".webp", ".sqlite3")))
    }
}
