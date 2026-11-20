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

    /** The topic catalog's guarantee: every help topic has a section on the User guide tab to scroll to and highlight. */
    @Test fun everyHelpTopicResolvesToAUserGuidePage() {
        val missing = DocsLink.entries.filter { UserGuideToc.pageFor(it) == null }
        assertEquals(emptyList(), missing, "DocsLink entries without a User guide tab page")
    }

    @Test fun anchoredTopicsResolveToTheirPage() {
        val page = assertNotNull(UserGuideToc.pageFor(DocsLink.AI_SETTING_PERMISSIONS))
        assertEquals("ai", page.stem)
        assertEquals("getting_started", UserGuideToc.pageFor(DocsLink.GETTING_STARTED_WINDOWS)?.stem)
    }
}
