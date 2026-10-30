package net.bible.desktop

import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.Element

class WindowsManifestTest {
    @Test
    fun msiAssociationsAndSigningPolicyAreGeneratedForPackaging() {
        val policy = java.util.Properties().apply {
            File("build/windows-msix/packaging-policy.properties").inputStream().use { load(it) }
        }
        val extensions = policy.getProperty("msi.extensions").split(',').toSet()
        assertEquals(setOf("zip", "epub", "ttf", "csv", "jpg", "jpeg", "png", "webp", "sqlite3"), extensions)
        assertEquals(System.getProperty("expectedTimestamp"), policy.getProperty("timestamp.url"))
        assertEquals(System.getProperty("expectedTestSigning"), policy.getProperty("test.signing"))
        val expectedArgs = if (System.getProperty("expectedTestSigning") == "true") "" else
            "/tr|${System.getProperty("expectedTimestamp")}|/td|SHA256"
        assertEquals(expectedArgs, policy.getProperty("timestamp.args"))
    }

    @Test
    fun generatedManifestHasIdentityVersionAndFileAssociations() {
        val stage = File("build/windows-msix/staging")
        val manifest = File(stage, "AppxManifest.xml")
        assertTrue(manifest.isFile, "Generated Windows manifest is missing")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val xml = factory.newDocumentBuilder().parse(manifest)
        fun elements(name: String) = xml.getElementsByTagNameNS("*", name).let { nodes ->
            (0 until nodes.length).map { nodes.item(it) as Element }
        }
        val identity = elements("Identity").single()
        assertEquals("AndBible.Desktop", identity.getAttribute("Name"))
        assertEquals("CN=AndBible-Unsigned", identity.getAttribute("Publisher"))
        assertEquals("${System.getProperty("windowsPackageVersion")}.0", identity.getAttribute("Version"))
        val capabilities = elements("Capability").map { it.getAttribute("Name") }.toSet()
        assertTrue(capabilities.containsAll(setOf("runFullTrust", "unvirtualizedResources")))
        assertEquals("disabled", elements("FileSystemWriteVirtualization").single().textContent)
        assertEquals("\"%1\"", elements("Verb").single().getAttribute("Parameters"))
        assertTrue(elements("Verb").single().textContent.isNotBlank(), "File activation verb needs a display name")

        val mapping = mapOf(
            "application/zip" to setOf(".zip"), "application/epub+zip" to setOf(".epub"),
            "font/ttf" to emptySet(), // Windows reserves .ttf; import fonts through the app picker.
            "text/csv" to setOf(".csv"),
            "image/jpeg" to setOf(".jpg", ".jpeg"), "image/png" to setOf(".png"),
            "image/webp" to setOf(".webp"), "application/vnd.sqlite3" to setOf(".sqlite3"),
        )
        val mimeLine = File("packaging/linux/org.andbible.AndBible.desktop").readLines().single { it.startsWith("MimeType=") }
        val mimeTypes = mimeLine.substringAfter('=').split(';').filter { it.isNotEmpty() }.toSet()
        assertEquals(emptySet(), mimeTypes - mapping.keys, "Unmapped Linux MIME types")
        val extensions = elements("FileType").map { it.textContent }.toSet()
        assertTrue(extensions.containsAll(mimeTypes.flatMap { mapping.getValue(it) }), "Missing Linux file associations")
        assertTrue(".ttf" !in extensions, "Windows reserves .ttf")
        for ((name, size) in listOf("StoreLogo" to 50, "Square150x150Logo" to 150, "Square44x44Logo" to 44)) {
            val image = assertNotNull(ImageIO.read(File(stage, "Assets/$name.png")))
            assertEquals(size, image.width)
            assertEquals(size, image.height)
        }
    }
}
