/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.bible.sharedui.host.reading

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Exercises offset iteration during pane replacement, on the same UI thread as production. */
@RunWith(Parameterized::class)
class LinksConversionOffsetsStressTest(@Suppress("UNUSED_PARAMETER") run: Int) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "offset stress run {0}")
        fun runs() = (1..20).map { arrayOf(it) }
    }

    @Test fun convertsWhileOffsetsAreRefreshed() {
        val fixture = WorkspaceSwitchContentTest()
        fixture.setUp()
        try {
            fixture.convertSearchLinksWindow(stressOffsets = true)
        } finally {
            fixture.tearDown()
        }
    }
}
