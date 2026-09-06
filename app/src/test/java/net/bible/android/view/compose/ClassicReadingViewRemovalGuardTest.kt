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

package net.bible.android.view.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks Batch Z-late's epilogue (spec 10.2-10.4): the reading view's classic branches, the
 * classic bottom chrome and everything the collapse orphans are gone, and stay gone.
 *
 * Deliberately source-walking rather than behavioural, following the house pattern
 * (SettingsEditorSheetGuardTest, QuickSheetMountGuardTest, SpeakEntryPointGuardTest): the
 * decision being made permanent here is structural, and no runtime assertion can observe
 * "this branch no longer exists".
 */
class ClassicReadingViewRemovalGuardTest {
    private val mainBibleActivity =
        "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt"

    @Test
    fun theReadingViewReadsTheFlagNowhere() {
        val code = ClassicRemovalScan.codeLinesOf(mainBibleActivity)
        assertEquals(
            "MainBibleActivity must not read use_compose_ui at all -- the epilogue deleted the " +
                "setting, its row and every production reader. Note that such a read would still " +
                "COMPILE (settings is an untyped key-value store), so this count is the only thing " +
                "that catches one coming back.",
            0,
            Regex("""getBoolean\("use_compose_ui"""").findAll(code).count(),
        )
    }

    /**
     * Fix round 1, and the reason it exists: [theReadingViewReadsTheFlagNowhere] counts flag reads
     * and nothing else, so it is satisfied IDENTICALLY by a collapse to the Compose branch and a
     * collapse to the classic one. Delete the flag read but keep the toolbar tinting and
     * `statusBarColor = toolbarColor`, and the count is still 0. Nothing else in the tree closes
     * that gap: the Roborazzi goldens exercise Compose composables, not this activity's
     * system-bar code, and the compile break from the classic views' own removal does not arrive
     * until the slice that deletes them.
     *
     * These three names are the tell, because each appears only on the classic side of the branches
     * this epilogue collapsed. `toolbarColor` was the classic toolbar's colour source (deleted with
     * its last reader); `toolbarLayout` is the view every classic branch mutated -- background,
     * height, padding, visibility, slide animation; `toolbarDivider` is the monochrome-only rule
     * from inside the deleted tint block. All three are exactly zero in the file's CODE lines as of
     * this commit and each would be non-zero under a wrong-direction collapse.
     *
     * Deliberately scoped to code lines: `toolbarLayout` still appears seven times in this file's
     * PROSE ("the now-GONE `toolbarLayout`"), which [ClassicRemovalScan.codeLinesOf] strips. Those
     * comments are accurate and must stay.
     */
    @Test
    fun theReadingViewNamesNoClassicToolbarInCode() {
        val code = ClassicRemovalScan.codeLinesOf(mainBibleActivity)
        assertTrue(
            "the scan read no MainBibleActivity code at all -- this assertion would pass vacuously",
            code.contains("class MainBibleActivity"),
        )
        assertEquals(
            "MainBibleActivity must not name the classic toolbar in code. A use_compose_ui read " +
                "count of 0 on its own cannot tell a collapse to the Compose branch from a " +
                "collapse to the CLASSIC one -- these names can only reappear on the classic side.",
            emptyList<String>(),
            listOf("toolbarLayout", "toolbarColor", "toolbarDivider").filter { code.contains(it) },
        )
    }

    /**
     * The five files spec 10.3's collapse orphans. `SplitBibleArea.kt` had exactly two top-level
     * declarations left when it was deleted -- `LockableHorizontalScrollView`, whose only referrer
     * was `split_bible_area.xml`, and `SplitBibleArea` itself, whose only code referrer was
     * `BibleFrame.kt`. `AddNewWindowButtonWidget` lives inside `WindowButtonWidget.kt` and went
     * with it. `PageTiltScroller.kt` sits in the same directory and deliberately SURVIVES -- its
     * consumer is `BibleView`.
     */
    private val doomedReadingViewPaths = listOf(
        "src/main/java/net/bible/android/view/activity/page/screen/SplitBibleArea.kt",
        "src/main/java/net/bible/android/view/activity/page/screen/BibleFrame.kt",
        "src/main/java/net/bible/android/view/activity/page/screen/Separator.kt",
        "src/main/java/net/bible/android/view/util/widget/WindowButtonWidget.kt",
        "src/main/res/layout/split_bible_area.xml",
    )

    @Test
    fun theClassicSplitReadingAreaIsGone() {
        ClassicRemovalScan.assertPathsGone(
            doomedReadingViewPaths,
            "spec 10.3: the collapse orphans these five, and D2 deletes them here rather than " +
                "deferring",
        )
    }

    /**
     * `net.bible.android.view.util.widget.TwoLineListItem` is deliberately NOT in this list even
     * though it sits in the very directory `WindowButtonWidget.kt` was deleted from (trap 12).
     * While the split reading area was collapsed it still SURVIVED, inflated by name from
     * `list_item_2_highlighted.xml`, so listing it here would have failed this guard against live
     * code. The Z-late epilogue then deleted that layout and the widget with it -- but as its own
     * orphan sweep, not as split-reading-area residue, so it stays out of this list. `sharedUi`
     * separately owns an unrelated composable of the same bare name, which is why every entry
     * below is FULLY QUALIFIED.
     *
     * The names are FULLY QUALIFIED, as [ClassicRemovalScan.assertNoSourceNames] requires -- a bare
     * `Separator` would match ~100 unrelated lines, `sharedUi`'s own `Separator` composable among
     * them. The sweep keeps imports and scans resource XML too, so the layout tags that inflated
     * these classes by name are covered as well as Kotlin references.
     */
    @Test
    fun nothingStillNamesTheClassicSplitReadingArea() {
        ClassicRemovalScan.assertNoSourceNames(
            listOf(
                "net.bible.android.view.activity.page.screen.SplitBibleArea",
                "net.bible.android.view.activity.page.screen.BibleFrame",
                "net.bible.android.view.activity.page.screen.Separator",
                "net.bible.android.view.util.widget.WindowButtonWidget",
                "net.bible.android.view.util.widget.AddNewWindowButtonWidget",
            ),
            "a KDoc bracket link counts, and not because it breaks the build: kotlinc ignores an " +
                "unresolved [Foo] entirely (at most Dokka warns). What it means is that a survivor " +
                "still points a reader at a class that is gone -- and the IMPORT such a link " +
                "usually drags in behind it IS a hard compile error.",
        )
    }

    @Test
    fun theRestoreButtonsEventOutlivesItsClassicHome() {
        // RestoreButtonsVisibilityChanged is posted by WindowRepository.notifyRestoreButtonsChanged
        // and consumed by BibleView -- both on the Compose path. It must NOT die with SplitBibleArea.
        ClassicRemovalScan.assertPathsPresent(
            listOf("src/main/java/net/bible/android/view/activity/page/screen/RestoreButtonsEvents.kt"),
            "the live Compose-path event must have been split out before SplitBibleArea is deleted",
        )
        ClassicRemovalScan.assertPathsGone(
            doomedReadingViewPaths,
            "the classic host must be GONE, so it cannot hold a second declaration of the event",
        )
    }

    /**
     * The second live tenant of `SplitBibleArea.kt`, found while proving the claim above rather
     * than assuming it. `var clipboardKey` is a TOP-LEVEL property in that file, and three
     * survivors import it by its fully-qualified name -- `MainBibleActivity`, `BibleView` and
     * `WindowPaneMenuStateBuilder` (the Compose pane menu's "go to copied reference" row reads the
     * very same shared state classic wrote). It has to be split out for exactly the same reason the
     * event does, so it is guarded the same way and for the same reason: nothing else in the tree
     * would notice its loss until the compile break in the slice that deletes its host.
     *
     * Both halves are load-bearing. The presence check alone would pass with the declaration
     * duplicated in two files (a redeclaration error, but only once someone compiles); the second
     * half alone would pass with the property simply deleted. That second half used to count
     * declarations inside `SplitBibleArea.kt`; once Task 4 deleted that file the count would have
     * THROWN rather than passed vacuously, so it became the [ClassicRemovalScan.assertPathsGone]
     * below -- which makes the same argument, since a host that does not exist cannot redeclare
     * anything.
     */
    @Test
    fun theClipboardKeyOutlivesItsClassicHome() {
        ClassicRemovalScan.assertPathsPresent(
            listOf("src/main/java/net/bible/android/view/activity/page/screen/ClipboardKey.kt"),
            "the shared clipboardKey must have been split out before SplitBibleArea is deleted",
        )
        ClassicRemovalScan.assertPathsGone(
            doomedReadingViewPaths,
            "the classic host must be GONE, so it cannot hold a second declaration of the slot",
        )
    }

    /**
     * Spec 10.4 / decision D1. The two widgets `main_bible_view.xml` still embedded were not merely
     * hidden, because a `GONE` view is still ATTACHED: `SpeakTransportWidget.onAttachedToWindow`
     * registers three `ABEventBus` subscriptions and its `SpeakProgressEvent` handler ran
     * `speakControl.getStatusText(FLAG_SHOW_ALL)` on every progress tick, beside the Compose
     * `SpeakTransportController` that replaced it; its constructor ran `SpeakSettings.load()`
     * regardless of visibility. `AgentLogWidget` likewise kept live bus handlers and a
     * `RecyclerView` adapter. So the tags go and the classes go with them.
     *
     * The presence half is not decoration: both files held a LIVE declaration that the Compose path
     * still posts and consumes (`HideTransportEvent`, `AgentLogVisibilityChanged`), so the deletion
     * is only correct if those were split out first -- exactly the shape of
     * [theRestoreButtonsEventOutlivesItsClassicHome] above. Deleting the widgets and dropping the
     * events would break the Compose Speak bar's hide path and the agent-log offset bookkeeping,
     * and the two halves together forbid the other failure mode too: a split-out file that merely
     * shadows a still-present host.
     *
     * The layout half is the anti-vacuity precondition for the count: `mainBibleView` is the Compose
     * mount point and must still be there, or "zero classic chrome tags" is what an empty or
     * moved-away file says too.
     */
    @Test
    fun theClassicBottomChromeIsGone() {
        ClassicRemovalScan.assertPathsGone(
            listOf(
                "src/main/java/net/bible/android/view/util/widget/SpeakTransportWidget.kt",
                "src/main/java/net/bible/android/view/util/widget/AgentLogWidget.kt",
                "src/main/res/layout/speak_transport_widget.xml",
                "src/main/res/layout/agent_log_widget.xml",
            ),
            "spec 10.4 / D1: a GONE view is still attached, so leaving these hidden kept three " +
                "ABEventBus subscriptions and a per-tick getStatusText running beside the Compose " +
                "controller. The tags go and the classes go with them.",
        )
        ClassicRemovalScan.assertPathsPresent(
            listOf(
                "src/main/java/net/bible/android/view/util/widget/SpeakTransportEvents.kt",
                "src/main/java/net/bible/android/view/util/widget/AgentLogEvents.kt",
            ),
            "their live Compose-path events had to be split out first",
        )
        val layout = ClassicRemovalScan.codeLinesOf("src/main/res/layout/main_bible_view.xml")
        assertTrue(
            "main_bible_view.xml no longer holds the Compose mount point -- the count below " +
                "would pass vacuously against an empty or moved file",
            layout.contains("android:id=\"@+id/mainBibleView\""),
        )
        assertEquals("main_bible_view.xml must not embed the classic chrome", 0,
            Regex("""SpeakTransportWidget|AgentLogWidget""").findAll(layout).count())
    }
}
