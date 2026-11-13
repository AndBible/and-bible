package net.bible.service.cloudsync.drive.oauth

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoogleOAuthClientsTest {
    private val projectPrefix = "533479479097-"

    @Test fun iosClientIsConfiguredWithoutSecret() {
        val ios = assertNotNull(GoogleOAuthClients.ios)
        assertTrue(ios.clientId.startsWith(projectPrefix), ios.clientId)
        assertTrue(ios.clientId.endsWith(".apps.googleusercontent.com"), ios.clientId)
        assertNull(ios.clientSecret)
    }

    @Test fun desktopClientIsConfiguredWithSecret() {
        val desktop = assertNotNull(GoogleOAuthClients.desktop)
        assertTrue(desktop.clientId.startsWith(projectPrefix), desktop.clientId)
        assertTrue(desktop.clientId.endsWith(".apps.googleusercontent.com"), desktop.clientId)
        assertTrue(!desktop.clientSecret.isNullOrBlank())
    }

    @Test fun platformsUseDistinctClients() {
        assertTrue(GoogleOAuthClients.ios!!.clientId != GoogleOAuthClients.desktop!!.clientId)
    }
}
