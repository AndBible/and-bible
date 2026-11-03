/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.sharedui.webview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** BibleView-JS opens links with `window.location.assign`; the navigation delegate must turn those into link opens. */
class IosLinkNavigationTest {
    private val page = "$BIBLE_ORIGIN/index.html?lang=en&night=false"

    @Test
    fun mainFrameNavigationsAwayFromTheBundlePageAreLinks() {
        for (link in listOf(
            "osis://?osis=John.3.16&v11n=KJV", "ab-w://?strong=G2316", "multi://?osis=Gen.1.1",
            "sword://MHC/Matt.5.3", "https://example.org/", "journal://?id=x",
        )) {
            assertEquals(link, iosLinkNavigation(link, isMainFrame = true, documentUrl = page), link)
        }
    }

    @Test
    fun bundleLoadsSubframesAndForeignDocumentsAreNoLinks() {
        assertNull(iosLinkNavigation("$BIBLE_ORIGIN/index.html?lang=fi", isMainFrame = true, documentUrl = page))
        assertNull(iosLinkNavigation("$BIBLE_ORIGIN/index.html", isMainFrame = true, documentUrl = null), "the first load")
        assertNull(iosLinkNavigation("osis://?osis=Gen.1.1", isMainFrame = false, documentUrl = page))
        assertNull(iosLinkNavigation("osis://?osis=Gen.1.1", isMainFrame = true, documentUrl = "https://evil.org/"))
        assertNull(iosLinkNavigation("osis://?osis=Gen.1.1", isMainFrame = true, documentUrl = null))
        assertNull(iosLinkNavigation(null, isMainFrame = true, documentUrl = page))
    }

    @Test
    fun onlyTheExactBundleOriginIsTheBundle() {
        assertTrue(isAtBibleOrigin(BIBLE_ORIGIN))
        assertTrue(isAtBibleOrigin("$BIBLE_ORIGIN/x?y#z"))
        assertFalse(isAtBibleOrigin("${BIBLE_ORIGIN}.evil/index.html"))
        assertFalse(isAtBibleOrigin("${BIBLE_ORIGIN}x/"))
        assertFalse(isAtBibleOrigin("https://app/index.html"))
    }
}
