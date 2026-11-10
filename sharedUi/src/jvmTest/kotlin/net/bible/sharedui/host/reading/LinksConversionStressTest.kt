/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.bible.sharedui.host.reading

import kotlin.test.Test

/** osTicket 3520: repeated real host conversions expose the movable-pane invalidation race. */
class LinksConversionStressTest {
    @Test fun repeatedSearchLinksWindowConversions() {
        repeat(40) {
            val fixture = WorkspaceSwitchContentTest()
            fixture.setUp()
            try {
                fixture.searchLinksWindowCanBeConvertedWhileTheSplitIsComposed()
            } finally {
                fixture.tearDown()
            }
        }
    }
}
