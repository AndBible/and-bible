package net.bible.service.cloudsync.nextcloud

import android.net.Uri
import com.owncloud.android.lib.common.OwnCloudClientFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Document
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class NextCloudSearchMethodTest {
    private fun request(user: String, folder: String, since: Long): Document {
        val client = OwnCloudClientFactory.createOwnCloudClient(
            Uri.parse("https://cloud.example.test"), RuntimeEnvironment.getApplication(), false,
        ).apply { userId = user }
        val method = NextCloudSearchMethod(client, folder, since)
        assertEquals("SEARCH", method.name)
        assertEquals("text/xml", method.getRequestHeader("Content-Type").value)
        val bytes = ByteArrayOutputStream().also { method.requestEntity.writeRequest(it) }.toByteArray()
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(bytes.inputStream())
    }

    private fun Document.davText(name: String): String =
        getElementsByTagNameNS("DAV:", name).item(0).textContent

    @Test
    fun incrementalSearchUsesUtcRegardlessOfDeviceLocaleAndTimezone() {
        val locale = Locale.getDefault()
        val zone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("th-TH"))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            val query = request("reader", "AndBible/bookmarks", 1_704_164_645_999L)
            assertEquals("2024-01-02T03:04:05Z", query.davText("literal"))
            assertEquals("/files/reader/AndBible/bookmarks", query.davText("href"))
            assertEquals("infinity", query.davText("depth"))
            assertEquals(1, query.getElementsByTagNameNS("DAV:", "gt").length)
            assertEquals(1, query.getElementsByTagNameNS("DAV:", "ascending").length)
        } finally {
            Locale.setDefault(locale)
            TimeZone.setDefault(zone)
        }
    }

    @Test
    fun accountAndFolderAreXmlTextNotMarkup() {
        val query = request("reader&writer", "AndBible/A&B/<notes>", 0L)
        assertEquals("/files/reader&writer/AndBible/A&B/<notes>", query.davText("href"))
        assertEquals(0, query.getElementsByTagName("notes").length)
    }
}
