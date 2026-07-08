package net.bible.sharedcore

import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformTest {
    @Test fun name_is_sharedCore() {
        assertEquals("sharedCore", SharedCore.NAME)
    }
}
