package net.bible.sharedui.host.reading

import net.bible.service.common.THEME_PALETTE_STYLE_KEY
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [HostDocumentLoaderPlatform.CONFIG_SETTING_KEYS] decides which app-settings writes re-send the BibleView config
 * (#4047). It is a hand-kept list, so a key newly read by the config builder would silently stay stale until a
 * reload. This scans the builder for every literal app-settings key it reads and requires the list to match.
 */
class ConfigSettingKeysGuardTest {
    @Test fun everyAppSettingReadByTheConfigBuilderReSendsTheConfig() {
        val source = File("src/hostMain/kotlin/net/bible/sharedui/host/reading/HostDocumentLoaderPlatform.kt").readText()
        val builder = source.substringBefore("companion object")
        val read = Regex("""\b(?:s|settings\(\))\.get\w+\(\s*"([a-z0-9_]+)"""").findAll(builder).map { it.groupValues[1] }.toSet()
        assertTrue(read.isNotEmpty(), "the scan must find the config builder's settings reads")
        val viaConstant = if ("THEME_PALETTE_STYLE_KEY" in builder) setOf(THEME_PALETTE_STYLE_KEY) else emptySet()
        assertEquals(read + viaConstant, HostDocumentLoaderPlatform.CONFIG_SETTING_KEYS)
    }
}
