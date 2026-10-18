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

package net.bible.sharedui.reading.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface

class BibleWindowSessionTest {
    private class FakeHost : JsHost {
        val evaluated = mutableListOf<String>()
        override val isMainThread = true
        override val isAttached = true
        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        override fun post(block: () -> Unit) = block()
        override fun evaluate(js: String, callback: ((String) -> Unit)?) {
            evaluated += js
            callback?.invoke("null")
        }
    }

    private val surface = object : ReadingWindowSurface {
        override fun copyText(label: String?, text: String) {}
        override fun shareText(text: String, subject: String?) {}
        override fun shareHtml(html: String, title: String) {}
        override fun stopSelection(removeRanges: Boolean) {}
        override fun toast(text: String) {}
        override fun openWebSearch(query: String) {}
    }

    private fun session(host: FakeHost): BibleWindowSession {
        val channel = BibleViewJsChannel(host) {}
        channel.markClientReady()
        return BibleWindowSession(
            window = null,
            jsChannel = channel,
            hostScope = { TestScope() },
            surface = surface,
            navigator = unusedNavigator(),
        )
    }

    private fun unusedNavigator(): ReadingNavigator =
        java.lang.reflect.Proxy.newProxyInstance(
            ReadingNavigator::class.java.classLoader, arrayOf(ReadingNavigator::class.java),
        ) { _, m, _ -> error("unexpected navigator call ${m.name}") } as ReadingNavigator

    @Test
    fun chapterBookkeepingTracksLoadedRange() {
        val s = session(FakeHost())
        s.minChapter = 3
        s.maxChapter = 5
        assertTrue(s.hasChapterLoaded(4))
        assertTrue(s.hasChapterLoaded(3))
        assertTrue(s.hasChapterLoaded(5))
        assertFalse(s.hasChapterLoaded(6))
        assertFalse(s.hasChapterLoaded(2))
    }

    @Test
    fun emitAfterDisposeIsDropped() {
        val host = FakeHost()
        val s = session(host)
        assertTrue(s.emit(BibleViewEvents.closeModals()))
        val before = host.evaluated.size
        assertEquals(1, before)
        s.dispose()
        assertTrue(s.isDisposed)
        assertFalse(s.emit(BibleViewEvents.closeModals()))
        assertEquals(before, host.evaluated.size)
    }
}
