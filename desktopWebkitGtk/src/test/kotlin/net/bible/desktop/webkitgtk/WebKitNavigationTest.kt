package net.bible.desktop.webkitgtk

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WebKitNavigationTest {
    private val nav = POLICY_NAVIGATION_ACTION
    private val newWindow = POLICY_NEW_WINDOW_ACTION

    @Test
    fun bundleNavigationIsAllowed() {
        assertEquals(NavigationVerdict.Allow, decideNavigation(nav, "$BUNDLE_BASE/index.html?lang=en&night=false"))
        assertEquals(NavigationVerdict.Allow, decideNavigation(nav, BUNDLE_BASE))
        assertEquals(NavigationVerdict.Allow, decideNavigation(nav, "$BUNDLE_BASE/#frag"))
    }

    @Test
    fun offBundleNavigationIsBlockedAndReported() {
        for (uri in listOf("https://example.com/x", "osis://John.3.16", "ab-w://?x=1", "about:blank", "file:///etc/passwd")) {
            assertEquals(NavigationVerdict.BlockAndReport(uri), decideNavigation(nav, uri), uri)
        }
    }

    @Test
    fun lookalikeHostsPortsAndUserinfoAreNotBundle() {
        for (uri in listOf(
            "andbible://bibleview.evil/", "andbible://bibleview:8080/", "andbible://bibleview@evil/",
            "andbible://user@bibleview/", "andbible2://bibleview/", "https://bibleview/", "ANDBIBLE://bibleview/",
            "andbible://bibleviewx/index.html",
        )) {
            assertEquals(NavigationVerdict.BlockAndReport(uri), decideNavigation(nav, uri), uri)
        }
    }

    @Test
    fun emptyOrMissingUriIsBlockedWithoutReport() {
        for (type in listOf(nav, newWindow)) {
            assertEquals(NavigationVerdict.Block, decideNavigation(type, null))
            assertEquals(NavigationVerdict.Block, decideNavigation(type, ""))
        }
    }

    @Test
    fun newWindowIsNeverAllowedEvenToTheBundle() {
        assertEquals(NavigationVerdict.BlockAndReport("https://example.com/p"), decideNavigation(newWindow, "https://example.com/p"))
        assertEquals(NavigationVerdict.BlockAndReport("$BUNDLE_BASE/index.html"), decideNavigation(newWindow, "$BUNDLE_BASE/index.html"))
    }

    @Test
    fun responsesAndUnknownTypesAreLeftToWebKit() {
        assertEquals(NavigationVerdict.Default, decideNavigation(2, "https://example.com/"))
        assertEquals(NavigationVerdict.Default, decideNavigation(2, null))
        assertEquals(NavigationVerdict.Default, decideNavigation(99, "$BUNDLE_BASE/"))
    }

    @Test
    fun frameLockdownPolicyForbidsFramesAndObjects() {
        assertEquals("frame-src 'none'; object-src 'none'", FRAME_LOCKDOWN_CSP)
    }

    @Test
    fun argbConvertsToRgbaFloats() {
        assertContentEquals(floatArrayOf(1f, 0f, 0f, 1f), argbToRgba(0xFFFF0000.toInt()))
        assertContentEquals(floatArrayOf(0f, 0f, 0f, 0f), argbToRgba(0))
        val mixed = argbToRgba(0x80336699.toInt())
        assertEquals(0x33 / 255f, mixed[0])
        assertEquals(0x66 / 255f, mixed[1])
        assertEquals(0x99 / 255f, mixed[2])
        assertEquals(0x80 / 255f, mixed[3])
    }
}
