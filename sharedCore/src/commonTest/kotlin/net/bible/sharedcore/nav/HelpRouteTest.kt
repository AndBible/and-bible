package net.bible.sharedcore.nav

import net.bible.sharedcore.docs.DocsLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HelpRouteTest {
    @Test fun aTopiclessHelpRouteIsThePlainHelpRoute() {
        assertEquals(NavRoutes.HELP, NavRoutes.help(null))
        assertEquals(NavRoutes.HELP, NavRoutes.HELP_PATTERN.substringBefore('?'))
    }

    @Test fun everyTopicRoundTripsThroughTheRouteArgument() {
        for (link in DocsLink.entries) {
            val route = NavRoutes.help(link)
            assertEquals("help?topic=${link.name}", route)
            assertEquals(link, NavRoutes.helpTopic(route.substringAfter('=')))
        }
    }

    @Test fun anUnknownTopicIsIgnored() {
        assertNull(NavRoutes.helpTopic("NOT_A_TOPIC"))
        assertNull(NavRoutes.helpTopic(null))
    }
}
