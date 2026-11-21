package net.bible.sharedcore.settings

import kotlin.test.Test
import kotlin.test.assertTrue

class WindowButtonsPreferencesJvmTest {
    /** #4184: the desktop app lets text run under the window buttons unless the user turns it off. */
    @Test fun desktopDefaultIsTextUnderTheButtons() {
        assertTrue(textUnderWindowButtonsDefault)
    }
}
