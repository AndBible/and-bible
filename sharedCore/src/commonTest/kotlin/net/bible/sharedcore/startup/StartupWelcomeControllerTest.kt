package net.bible.sharedcore.startup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupWelcomeControllerTest {
    private fun info(prev: Boolean, easy: Boolean) = StartupWelcomeInfo(
        welcomeText = "welcome",
        versionText = "v1",
        supportedFormatsText = "fmts",
        redownloadMessage = "re",
        easyStartMessage = "es",
        previousInstallDetected = prev,
        easyStartAvailable = easy,
    )

    @Test fun previousInstall_shows_redownload_and_hides_restore() {
        val c = StartupWelcomeController { info(prev = true, easy = false) }
        val s = c.state.value
        assertTrue(s.showRedownload)
        assertFalse(s.showRestore)
        assertFalse(s.showEasyStart)
    }

    @Test fun noPreviousInstall_shows_restore_and_hides_redownload() {
        val c = StartupWelcomeController { info(prev = false, easy = true) }
        val s = c.state.value
        assertFalse(s.showRedownload)
        assertTrue(s.showRestore)
        assertTrue(s.showEasyStart)
    }

    @Test fun setProgress_updates_progressText() {
        val c = StartupWelcomeController { info(prev = false, easy = false) }
        assertEquals(null, c.state.value.progressText)
        c.setProgress("Installing…")
        assertEquals("Installing…", c.state.value.progressText)
    }

    @Test fun refresh_recomputes_visibility_from_latest_info() {
        var prev = false
        val c = StartupWelcomeController { info(prev = prev, easy = false) }
        assertTrue(c.state.value.showRestore)
        prev = true
        c.refresh()
        assertTrue(c.state.value.showRedownload)
        assertFalse(c.state.value.showRestore)
    }

    @Test fun refresh_preserves_progressText() {
        val c = StartupWelcomeController { info(prev = false, easy = false) }
        c.setProgress("busy")
        c.refresh()
        assertEquals("busy", c.state.value.progressText)
    }
}
