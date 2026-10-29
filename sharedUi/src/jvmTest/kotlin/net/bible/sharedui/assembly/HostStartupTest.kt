package net.bible.sharedui.assembly

import net.bible.sharedui.strings.keyed.StringTable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostStartupTest {
    @AfterTest fun unregister() = HostStartup.register(null)

    @Test fun unregisteredEqualsNotStarted() {
        HostStartup.register(null)
        assertFalse(HostStartup.isStarted)
        assertFalse(HostStartup.databasesOpen)
        assertFailsWith<IllegalStateException> { HostStartup.stringTable }
    }

    @Test fun registeredInfoIsReadLiveOnEveryAccess() {
        var started = false
        var open = false
        HostStartup.register(object : HostStartupInfo {
            override val isStarted get() = started
            override val databasesOpen get() = open
            override val stringTable: StringTable get() = error("no table in this fake")
        })
        assertFalse(HostStartup.isStarted)
        started = true; open = true
        assertTrue(HostStartup.isStarted)
        assertTrue(HostStartup.databasesOpen)
        assertEquals("no table in this fake", assertFailsWith<IllegalStateException> { HostStartup.stringTable }.message)
    }
}
