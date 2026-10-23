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
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.cinterop.useContents
import kotlinx.coroutines.*
import net.bible.sharedcore.platform.files.SheetAnchor
import net.bible.sharedcore.platform.files.sheetAnchorRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.*
import platform.UIKit.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The unified share presenter through its real [IosSharePresenter] / `IosSheetPresentation` lifecycle, with a recording
 * host and a plain popover controller standing in for UIActivityViewController (which needs an app-hosted scene).
 */
class IosShareSheetTest {
    private class Host : UIViewController(null, null) {
        var attached: UIViewController? = null
        override fun presentedViewController(): UIViewController? = attached
        override fun presentViewController(viewControllerToPresent: UIViewController, animated: Boolean, completion: (() -> Unit)?) {
            attached = viewControllerToPresent
            completion?.invoke()
        }
    }

    private class Rig(width: Double = 400.0, height: Double = 800.0) {
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, width, height))
        val host = Host().also { it.view.setFrame(CGRectMake(0.0, 0.0, width, height)); window.addSubview(it.view) }
        val sheets = mutableListOf<UIViewController>()
        var releases = 0
        val presentations = mutableListOf<net.bible.sharedcore.platform.files.IosSheetPresentation<Unit>>()
        val presenter = IosSharePresenter(
            presenter = { host },
            makeSheet = { _ -> UIViewController(null, null).also { it.modalPresentationStyle = UIModalPresentationPopover; sheets.add(it) } },
            onReleased = { releases++ },
            onPresentation = { presentations += it },
        )
        /** The terminal event UIKit's dismissal callbacks funnel into; delegates are weak, so go through the owner. */
        fun dismissPopover(@Suppress("UNUSED_PARAMETER") sheet: UIViewController) = presentations.last().finish(Unit)
        fun close() = host.view.removeFromSuperview()
    }

    @Test fun fileShareCompletesOnceAndAnchorsPopover() = onMain {
        val rig = Rig()
        try {
            val file = NSURL.fileURLWithPath(NSTemporaryDirectory() + "/share-test.txt")
            val job = rig.presenter.share(listOf(file))
            awaitCondition { rig.sheets.isNotEmpty() }
            val sheet = rig.sheets.single()
            val popover = assertNotNull(sheet.popoverPresentationController)
            assertEquals(rig.host.view, popover.sourceView)
            popover.sourceRect.useContents {
                assertEquals(200.0, origin.x)
                assertEquals(799.0, origin.y) // bottom centre, not the view centre
                assertEquals(1.0, size.width)
            }
            assertFalse(rig.presenter.canPresent(), "a second share must not start while one is up")
            // UIKit may report the same dismissal through several callbacks: only the first is terminal.
            assertEquals(1, rig.presentations.size)
            rig.dismissPopover(sheet)
            rig.dismissPopover(sheet)
            job.join()
            assertEquals(1, rig.releases)
            assertTrue(rig.presenter.canPresent())
        } finally { rig.close() }
    }

    @Test fun secondShareWhileOneIsUpIsIgnored() = onMain {
        val rig = Rig()
        try {
            val first = rig.presenter.share(listOf("a"))
            awaitCondition { rig.sheets.size == 1 }
            rig.presenter.share(listOf("b")).join()
            assertEquals(1, rig.sheets.size)
            rig.dismissPopover(rig.sheets.single())
            first.join()
            assertEquals(1, rig.releases)
            rig.presenter.share(listOf("c"))
            awaitCondition { rig.sheets.size == 2 }
            rig.dismissPopover(rig.sheets.last())
        } finally { rig.close() }
    }

    @Test fun cancellingTheShareReleasesOnce() = onMain {
        val rig = Rig()
        try {
            val job = rig.presenter.share(listOf("a"))
            awaitCondition { rig.sheets.isNotEmpty() }
            job.cancelAndJoin()
            assertEquals(1, rig.releases)
            assertTrue(rig.presenter.canPresent())
        } finally { rig.close() }
    }

    @Test fun missingPresenterDoesNothing() = onMain {
        var made = 0
        var released = 0
        val presenter = IosSharePresenter(presenter = { null }, makeSheet = { made++; UIViewController(null, null) }, onReleased = { released++ })
        assertFalse(presenter.canPresent())
        presenter.share(listOf("a")).join()
        assertEquals(0, made)
        assertEquals(0, released)
    }

    @Test fun textShareUsesTheSamePresenter() = onMain {
        val rig = Rig()
        try {
            IosTextShare(rig.presenter).shareText("hello", "ignored title")
            awaitCondition { rig.sheets.isNotEmpty() }
            assertEquals(200.0, rig.sheets.single().popoverPresentationController!!.sourceRect.useContents { origin.x })
            rig.dismissPopover(rig.sheets.single())
            awaitCondition { rig.releases == 1 }
        } finally { rig.close() }
    }

    @Test fun anchorRectsDifferByAnchor() {
        assertEquals(Triple(200.0, 400.0, 0.0), sheetAnchorRect(SheetAnchor.Center, 0.0, 0.0, 400.0, 800.0))
        assertEquals(Triple(210.0, 859.0, 1.0), sheetAnchorRect(SheetAnchor.BottomCenter, 10.0, 60.0, 400.0, 800.0))
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        val started = TimeSource.Monotonic.markNow()
        while (!condition()) {
            check(started.elapsedNow() < 2.seconds) { "Native callback was not reached" }
            delay(1)
        }
    }

    private fun onMain(block: suspend CoroutineScope.() -> Unit) {
        check(NSThread.isMainThread)
        val result = CoroutineScope(Dispatchers.Main).async(block = block)
        val started = TimeSource.Monotonic.markNow()
        while (!result.isCompleted) {
            if (started.elapsedNow() > 20.seconds) { result.cancel(); fail("Share sheet test timed out") }
            NSRunLoop.mainRunLoop.runMode(NSDefaultRunLoopMode, beforeDate = NSDate.dateWithTimeIntervalSinceNow(0.01))
        }
        result.getCompleted()
    }
}
