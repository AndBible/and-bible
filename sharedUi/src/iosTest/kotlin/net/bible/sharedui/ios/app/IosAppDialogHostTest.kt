package net.bible.sharedui.ios.app

import net.bible.sharedui.ios.ports.IosExternalLinks
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** The iOS [AppDialogHost] hands the overlay what it built itself before the seam: a fresh [IosExternalLinks]. */
class IosAppDialogHostTest {
    @Test
    fun externalLinksAreAFreshIosExternalLinksPerOverlay() {
        val first = IosAppDialogHost.newExternalLinks()
        assertTrue(first is IosExternalLinks)
        assertNotSame(first, IosAppDialogHost.newExternalLinks())
    }
}
