package net.bible.sharedcore.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelpLinksTest {
    @Test fun everyDocsLinkRoundTripsThroughItsHelpUrl() {
        for (link in DocsLink.entries) {
            val url = HelpLinks.url(link)
            assertTrue(HelpLinks.isHelpUrl(url), url)
            assertEquals(link, HelpLinks.parse(url))
        }
    }

    @Test fun ordinaryAndUnknownUrlsAreNotHelpTopics() {
        assertNull(HelpLinks.parse(DocsLink.SPEAK.url))
        assertNull(HelpLinks.parse("https://example.org/"))
        assertNull(HelpLinks.parse("andbible-help:NO_SUCH_TOPIC"))
        assertNull(HelpLinks.parse("andbible-help:"))
    }

    /** Release notes are external updater targets; every other topic belongs to the in-app User guide tab. */
    @Test fun everyHelpTopicResolvesToAUserGuidePage() {
        val externalReleaseNotes = setOf(DocsLink.RELEASE_4_0, DocsLink.RELEASE_4_1, DocsLink.RELEASE_5_0, DocsLink.RELEASE_6_0)
        val missing = DocsLink.entries.filter { it !in externalReleaseNotes && UserGuideToc.pageFor(it) == null }
        assertEquals(emptyList(), missing, "Help topics without a User guide tab page")
        externalReleaseNotes.forEach { assertNull(UserGuideToc.pageFor(it), it.name) }
    }

    @Test fun anchoredTopicsResolveToTheirPage() {
        val page = assertNotNull(UserGuideToc.pageFor(DocsLink.AI_SETTING_PERMISSIONS))
        assertEquals("ai", page.stem)
        assertEquals("getting_started", UserGuideToc.pageFor(DocsLink.GETTING_STARTED_WINDOWS)?.stem)
    }
}
