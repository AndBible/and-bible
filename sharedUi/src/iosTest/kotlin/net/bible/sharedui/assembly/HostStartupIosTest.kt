package net.bible.sharedui.assembly

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Pins that [HostStartup] answers exactly what [IosAppStartup] answers, before and after the start. */
class HostStartupIosTest {
    @AfterTest fun reset() { awaitStartupOnMain { IosAppStartup.resetForTest() } }

    @Test fun mirrorsIosAppStartupAcrossStartAndReset() {
        awaitStartupOnMain { IosAppStartup.resetForTest() }
        assertFalse(HostStartup.isStarted)
        assertEquals(IosAppStartup.databasesOpen, HostStartup.databasesOpen)
        assertFailsWith<IllegalStateException> { HostStartup.stringTable }
        awaitStartupOnMain { IosAppStartup.ensureStarted(IosAppConfig(
            root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
            repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
        )) }
        assertTrue(HostStartup.isStarted)
        assertSame(IosAppStartup.stringTable, HostStartup.stringTable)
    }
}
