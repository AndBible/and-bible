/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package net.bible.sharedui.desktop.webview

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.test.TestScope
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.UnimplementedBibleBridge
import net.bible.sharedui.desktop.webengine.FakeWebEngine
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.reading.session.BibleWindowSession
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopPaneAttachmentCompositionTest {
    @Test fun attachesOnlyAfterThePaneCompositionIsCommitted() = runComposeUiTest {
        val engine = DesktopReadingPaneEngine(FakeWebEngine(), {})
        val surface = java.lang.reflect.Proxy.newProxyInstance(ReadingWindowSurface::class.java.classLoader,
            arrayOf(ReadingWindowSurface::class.java)) { _, _, _ -> null } as ReadingWindowSurface
        val navigator = java.lang.reflect.Proxy.newProxyInstance(ReadingNavigator::class.java.classLoader,
            arrayOf(ReadingNavigator::class.java)) { _, _, _ -> null } as ReadingNavigator
        var committed = false
        var attached = false
        var attachedBeforeCommit = false
        val session = BibleWindowSession(null, BibleViewJsChannel(engine.jsHostFor("window")) {}, { TestScope() }, surface, navigator)
        val factory = desktopReadingPaneFactory(engine, {
            attachedBeforeCommit = attachedBeforeCommit || !committed
            attached = true
            session
        }, { UnimplementedBibleBridge({}, {}) })
        setContent {
            DisposableEffect(Unit) {
                committed = true
                onDispose { committed = false }
            }
            factory("window", Modifier)
        }
        waitForIdle()
        assertTrue(attached)
        kotlin.test.assertNotNull(engine.paneFor("window"))
        kotlin.test.assertFalse(engine.hasFailed("window"))
        kotlin.test.assertFalse(attachedBeforeCommit, "session creation must not mutate state during composition")
        runOnIdle { engine.release("window") }
    }
}
