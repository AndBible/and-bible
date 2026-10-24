package net.bible.sharedui.app.download

import net.bible.sharedui.strings.iosStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadErrorsMessageTest {
    private val strings = iosStrings("en")

    @Test fun insecureRepositoryGetsOneReasonLineEach() {
        val message = downloadErrorsMessage(strings, listOf("a", "b"), listOf("a", "b"), emptyList())
        assertTrue(message.startsWith(strings.failedRepositoriesMessage("a,\nb")), message)
        assertTrue(message.contains("a uses an http:// address. Only https:// repositories can be used.\n"), message)
        assertTrue(message.contains("b uses an http:// address. Only https:// repositories can be used.\n"), message)
    }

    @Test fun noInsecureRepositoryMeansNoReasonLine() {
        assertEquals(
            strings.failedRepositoriesMessage("a"),
            downloadErrorsMessage(strings, listOf("a"), emptyList(), emptyList()),
        )
    }
}
