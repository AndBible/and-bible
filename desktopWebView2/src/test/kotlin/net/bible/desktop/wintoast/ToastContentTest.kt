package net.bible.desktop.wintoast

import net.bible.sharedcore.platform.NativeNotification
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.xml.sax.InputSource

class ToastContentTest {
    @Test
    fun xmlEscapesTitleAndBody() {
        val title = "Q&A <draft>"
        val body = "Body & details <here>"
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(toastXml(notification(title = title, body = body)))))

        assertEquals(title, document.getElementsByTagName("text").item(0).textContent)
        assertEquals(body, document.getElementsByTagName("text").item(1).textContent)
    }

    @Test
    fun progressToastBindsDataNames() {
        val xml = toastXml(notification(progress = 42))

        assertTrue(xml.contains("<progress value=\"{progressValue}\" valueStringOverride=\"{progressText}\" status=\"{status}\""))
        assertTrue(xml.contains("<audio silent=\"true\""))
        assertEquals(
            mapOf("progressValue" to "0.42", "progressText" to "42 %", "status" to "Working"),
            toastData(notification(body = "Working", progress = 42)),
        )
    }

    @Test
    fun finishedToastIsSilentFailureIsNot() {
        val finished = toastXml(notification(progress = null, indeterminate = false, urgent = false))
        val failure = toastXml(notification(progress = null, indeterminate = false, urgent = true))

        assertTrue(finished.contains("<audio silent=\"true\""))
        assertFalse(failure.contains("<audio"))
    }

    @Test
    fun indeterminateProgressValue() {
        val notification = notification(progress = null, indeterminate = true)

        assertTrue(toastXml(notification).contains("<progress"))
        assertEquals(
            mapOf("progressValue" to "indeterminate", "progressText" to "", "status" to notification.body),
            toastData(notification),
        )
    }

    @Test
    fun xmlRoundTripsTextAfterRemovingForbiddenControlsAndUnpairedSurrogates() {
        val title = "before\u0001after\uD800tail"
        val body = "left\u0000middle\uDC00right😀"
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(InputSource(StringReader(toastXml(notification(title = title, body = body)))))

        assertEquals("beforeaftertail", document.getElementsByTagName("text").item(0).textContent)
        assertEquals("leftmiddleright😀", document.getElementsByTagName("text").item(1).textContent)
    }

    @Test
    fun longIdTagUsesKnownSha256PrefixAndRespectsLengthBoundary() {
        assertEquals("a".repeat(64), toastTag("a".repeat(64)))
        assertEquals("635361c48bb9eab14198e76ea8ab7f1a41685d6ad62aa9146d301d4f17eb0ae0", toastTag("a".repeat(65)))
        assertEquals("a".repeat(64), toastTag("a".repeat(64)))
        assertEquals(64, toastTag("a".repeat(65)).length)
    }

    @Test
    fun shortIdTagIsTheId() {
        assertEquals("job-1", toastTag("job-1"))
    }

    private fun notification(
        title: String = "Title",
        body: String = "Body",
        progress: Int? = null,
        indeterminate: Boolean = false,
        urgent: Boolean = false,
    ) = NativeNotification("id", title, body, progress, indeterminate, urgent)
}
