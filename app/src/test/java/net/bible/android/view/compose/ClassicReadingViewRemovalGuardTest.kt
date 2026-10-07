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
    /**
     * The reading view's host. Slice 8 F3 repointed both scans below from `MainBibleActivity.kt`
     * (deleted in F4) to the nav host, which is the only host left.
     */
    private val readingHost =
        "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt"

    @Test
    fun theReadingViewReadsTheFlagNowhere() {
        val code = ClassicRemovalScan.codeLinesOf(readingHost)
        assertEquals(
            "the reading host must not read use_compose_ui at all -- the epilogue deleted the " +
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
     * from inside the deleted tint block.
     *
     * **The R6d exemption is gone with its subject.** Reading-host re-typing R6d had narrowed the
     * rule to "named only in order to be HIDDEN", because `MainBibleActivity.hideClassicToolbarRow`
     * had to write `binding.toolbarLayout`/`toolbarDivider` `GONE` while that Activity still
     * inflated the classic row. Slice 8 F3 repointed this scan at the nav host, which never had a
     * classic toolbar row and so needs no exemption: any of the three names in its code is the
     * classic side coming back.
     *
     * Deliberately scoped to code lines: `toolbarLayout` still appears in the host's PROSE
     * ("`toolbarLayout` is GONE"), which [ClassicRemovalScan.codeLinesOf] strips. That comment is
     * accurate and must stay.
     */
    @Test
    fun theReadingViewNamesNoClassicToolbarInCode() {
        val code = ClassicRemovalScan.codeLinesOf(readingHost)
        assertTrue(
            "the scan read no NavHostComposeActivity code at all -- this assertion would pass vacuously",
            code.contains("class NavHostComposeActivity"),
        )
        assertEquals(
            "the reading host must not name the classic toolbar in code. A use_compose_ui read " +
                "count of 0 on its own cannot tell a collapse to the Compose branch from a collapse " +
                "to the CLASSIC one -- these names can only reappear on the classic side.",
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
    fun theDoomedClassicHostCannotHoldAWindowEvent() {
        // RestoreButtonsVisibilityChanged became WindowChange.RestoreButtonsChanged (ABEventBus phase 2);
        // WindowCommandsImplTest pins that notifyRestoreButtonsChanged emits it.
        ClassicRemovalScan.assertPathsGone(
            doomedReadingViewPaths,
            "the classic host must be GONE, so it cannot hold a second declaration of a window event",
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
     * still posts and consumes (`HideTransportEvent`), so the deletion
     * is only correct if those were split out first -- exactly the shape of
     * [theRestoreButtonsEventOutlivesItsClassicHome] above. Deleting the widgets and dropping the
     * events would break the Compose Speak bar's hide path and the agent-log offset bookkeeping,
     * and the two halves together forbid the other failure mode too: a split-out file that merely
     * shadows a still-present host. `AgentLogEvents.kt` was deleted in ABEventBus removal phase 0: its
     * event had no sender.
     *
     * The layout half used to read `main_bible_view.xml` and count the classic chrome tags in it.
     * Slice 8 F4 deleted that layout with `MainBibleActivity`, so the half is now "the layout is
     * gone": a layout that no longer exists cannot embed the classic chrome again.
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
            ),
            "their live Compose-path events had to be split out first",
        )
        ClassicRemovalScan.assertPathsGone(
            listOf("src/main/res/layout/main_bible_view.xml"),
            "the classic reading layout went with MainBibleActivity",
        )
    }
}
