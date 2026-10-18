package net.bible.sharedui.ios

import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.ios.ports.IosAppSettingsService
import net.bible.sharedui.ios.ports.IosDebugActions
import net.bible.sharedui.ios.ports.IosDocumentInstallStarter
import net.bible.sharedui.ios.ports.IosExternalLinks
import net.bible.sharedui.ios.ports.IosPlatformText
import net.bible.sharedui.ios.ports.IosTextDisplayPlatform
import net.bible.sharedui.app.ports.PluralKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosPendingTest {
    @BeforeTest fun reset() = IosPending.resetForTest()
    @AfterTest fun clean() = IosPending.resetForTest()

    @Test
    fun logsEachMemberOnce() {
        IosPending.log("X.y")
        IosPending.log("X.y")
        assertEquals(setOf("X.y"), IosPending.reported)
    }

    @Test
    fun pendingPortReturnsNoOpValues() {
        IosExternalLinks().open("https://example.org")
        assertFalse(IosExternalLinks().askBeforeOpening)
        IosDebugActions().showTestError("x")
        IosDocumentInstallStarter().start(emptyList<DocumentRef>(), null)
        assertEquals("", IosPlatformText().plural(PluralKey.CLOUD_DOC_SYNC_NOW_COUNT, 1))
        assertNull(IosTextDisplayPlatform().thumbnail("t"))
        val settings = IosAppSettingsService()
        settings.setBool("k", true)
        assertFalse(settings.snapshot.value.navigateToVerse)
        assertTrue(settings.snapshot.value.openLinksInSpecialWindow)
        assertTrue("ExternalLinks.open" in IosPending.reported)
        assertTrue("AppSettingsService.setBool" in IosPending.reported)
    }
}
