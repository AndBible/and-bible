package net.bible.sharedcore.docs

import kotlin.test.Test
import kotlin.test.assertEquals

class DocsLinksTest {
    @Test
    fun releaseNotesUseOnlyPublishedVersionPages() {
        val cases = listOf(
            "6.0.1285-beta" to "https://andbible.org/docs/releases/release_6_0/",
            "6.0.1286-test" to "https://andbible.org/docs/releases/release_6_0/",
            "5.0.1000" to "https://andbible.org/docs/releases/release_5_0/",
            "4.1.900" to "https://andbible.org/docs/releases/release_4_1/",
            "4.0.800" to "https://andbible.org/docs/releases/release_4_0/",
            "6.1.1300" to "https://andbible.org/docs/desktop/#update-checks",
            "7.0.1400" to "https://andbible.org/docs/desktop/#update-checks",
            "production-1285" to "https://andbible.org/docs/desktop/#update-checks",
            "6.0.invalid" to "https://andbible.org/docs/desktop/#update-checks",
            null to "https://andbible.org/docs/desktop/#update-checks",
        )
        for ((version, url) in cases) assertEquals(url, DocsLinks.releaseNotes(version), version)
    }

    @Test
    fun pageWithoutAnchor() =
        assertEquals("https://andbible.org/docs/study_pads/", DocsLink.STUDY_PADS.url)

    @Test
    fun pageWithAnchor() =
        assertEquals("https://andbible.org/docs/ai/#setting-permissions", DocsLink.AI_SETTING_PERMISSIONS.url)
}
