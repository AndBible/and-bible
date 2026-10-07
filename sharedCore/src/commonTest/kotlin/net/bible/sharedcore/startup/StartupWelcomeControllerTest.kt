package net.bible.sharedcore.startup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupWelcomeControllerTest {
    private fun info(prev: Boolean, easy: Boolean) = StartupWelcomeInfo(
        versionText = "v1",
        supportedFormatsText = "fmts",
        previousInstallDetected = prev,
        easyStartAvailable = easy,
    )

    @Test fun english_shows_tabs_and_starts_on_easy() {
        val s = StartupWelcomeController { info(prev = false, easy = true) }.state.value
        assertTrue(s.showTabs)
        assertEquals(StartupWelcomeTab.EASY, s.selectedTab)
    }

    @Test fun nonEnglish_has_no_tabs_and_is_advanced() {
        val s = StartupWelcomeController { info(prev = true, easy = false) }.state.value
        assertFalse(s.showTabs)
        assertEquals(StartupWelcomeTab.ADVANCED, s.selectedTab)
        assertFalse(s.showRedownloadHint)
        assertTrue(s.showRedownload)
    }

    @Test fun redownload_visibility_over_all_four_combinations() {
        for (prev in listOf(false, true)) for (easy in listOf(false, true)) {
            val s = StartupWelcomeController { info(prev = prev, easy = easy) }.state.value
            assertEquals(prev, s.showRedownload, "row prev=$prev easy=$easy")
            assertEquals(prev && easy, s.showRedownloadHint, "hint prev=$prev easy=$easy")
        }
    }

    @Test fun selectTab_switches_when_tabs_shown() {
        val c = StartupWelcomeController { info(prev = false, easy = true) }
        c.selectTab(StartupWelcomeTab.ADVANCED)
        assertEquals(StartupWelcomeTab.ADVANCED, c.state.value.selectedTab)
        c.selectTab(StartupWelcomeTab.EASY)
        assertEquals(StartupWelcomeTab.EASY, c.state.value.selectedTab)
    }

    @Test fun selectTab_ignored_without_tabs() {
        val c = StartupWelcomeController { info(prev = false, easy = false) }
        c.selectTab(StartupWelcomeTab.EASY)
        assertEquals(StartupWelcomeTab.ADVANCED, c.state.value.selectedTab)
    }

    @Test fun refresh_preserves_selected_tab() {
        var prev = false
        val c = StartupWelcomeController { info(prev = prev, easy = true) }
        c.selectTab(StartupWelcomeTab.ADVANCED)
        prev = true
        c.refresh()
        assertEquals(StartupWelcomeTab.ADVANCED, c.state.value.selectedTab)
        assertTrue(c.state.value.showRedownload)
    }

    @Test fun setProgress_updates_progressText() {
        val c = StartupWelcomeController { info(prev = false, easy = false) }
        assertEquals(null, c.state.value.progressText)
        c.setProgress("Installing…")
        assertEquals("Installing…", c.state.value.progressText)
    }

    @Test fun refresh_preserves_progressText() {
        val c = StartupWelcomeController { info(prev = false, easy = false) }
        c.setProgress("busy")
        c.refresh()
        assertEquals("busy", c.state.value.progressText)
    }
}
