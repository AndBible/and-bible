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
package net.bible.android.view.activity.page

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.BookCategory
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Nav-graph slice 7 Task 2. Two jobs:
 *
 * 1. **Parity**: `res/menu/main_bible_options_menu.xml` is still the contract for the reading
 *    view's overflow ("3-dot") menu until Task 13 deletes it, but nothing reads it at runtime any
 *    more — [OptionsMenuStateBuilder] hard-codes the same item set for the Compose
 *    `ReadingOverflowMenu`. Nothing but this test notices if the two drift apart, so this test
 *    parses the XML and compares.
 * 2. **No native `PopupMenu` is left on the reading view's options path**: the last two live
 *    entry points to the classic popups (the `"AltKeyO"` JS shortcut and the two `swap-menu`
 *    long-press document menus) now go through the Compose menus, and
 *    `MainBibleActivity.showOptionsMenu`/`handlePrefItem` are gone.
 *
 * Deliberate, documented differences between the XML and the Compose item list (asserted
 * explicitly below rather than papered over by a looser comparison):
 * - `allTextOptions` is emitted **last** by [OptionsMenuStateBuilder.build] — classic renders it
 *   there too, via `android:orderInCategory="1000"` inside `textOptionsGroup`, so the XML's
 *   *declaration* order and the *rendered* order differ. [OptionsMenuStateBuilder.staticItemIds]
 *   keeps declaration order; `build()` moves the row.
 * - the dynamic `textOptionItem` rows (one per `CommonUtils.lastDisplaySettingsSorted` entry) have
 *   no static `<item>` of their own — classic `menu.add`s them at runtime too.
 * - `<group android:id="@+id/textOptionsGroup">` is a group, not an item; it carries the section
 *   divider, which the Compose model expresses as `OptionsMenuItem.startsNewSection`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingOptionsMenuTest {

    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: NavHostComposeActivity
    /** The Koin singleton `MainBibleActivity` injected as `documentControl` (the nav host has no such member). */
    private val documentControl: DocumentControl get() = GlobalContext.get().get()

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        // Built WITHOUT `.create()`, for the reasons OptionsMenuStateBuilderTest's kdoc gives.
        activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        activity.readingAppBootstrap.windowRepository = windowRepository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
    }

    @After
    fun tearDown() {
        CommonUtils.settings.setString("toolbar_button_actions", null)
        CommonUtils.settings.setString("lastDisplaySettings", null)
        // `setCurrentDocument` persists this; the commentary long-press test really does switch.
        CommonUtils.settings.setString("default-COMMENTARY", null)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    // ---------------------------------------------------------------- XML parity

    private val menuXml = File("src/main/res/menu/main_bible_options_menu.xml")

    /**
     * `<item ... android:id="@+id/NAME" ...>` ids, in declaration order. `[^>]*` cannot cross the
     * element's own closing `>`, and `<group>`/`<menu>` elements do not start with `<item`, so
     * group ids are excluded by construction.
     */
    private fun parseMenuItemIds(file: File): List<String> =
        Regex("""<item\b[^>]*android:id="@\+id/([A-Za-z0-9_]+)"""")
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toList()

    /**
     * A parser that silently found nothing would make every parity assertion below vacuous, so
     * prove it actually reads the file.
     */
    @Test
    fun theMenuXmlParserActuallyFindsTheItemIds() {
        assertTrue(menuXml.isFile, "working dir must be the :app module dir; got ${menuXml.absolutePath}")
        val ids = parseMenuItemIds(menuXml)
        assertTrue(ids.size >= 9, "expected at least the 9 known <item>s, parsed ${ids.size}: $ids")
        assertTrue("nightMode" in ids, "a known item id must be parsed out; got $ids")
        assertTrue("allTextOptions" in ids, "the item nested inside <group> must be parsed too; got $ids")
        assertFalse(
            "textOptionsGroup" in ids,
            "a <group> id is not an item and must not be parsed as one; got $ids",
        )
        assertEquals(ids.size, ids.distinct().size, "no duplicate ids expected: $ids")
    }

    @Test
    fun everyClassicMenuItemHasAComposeCounterpart() {
        val classicIds = parseMenuItemIds(menuXml)
        val composeIds = OptionsMenuStateBuilder.staticItemIds

        val missing = classicIds - composeIds.toSet()
        assertTrue(missing.isEmpty(), "XML items with no Compose counterpart: $missing")
        val extra = composeIds - classicIds.toSet()
        assertTrue(extra.isEmpty(), "Compose items with no XML counterpart: $extra")
        assertEquals(classicIds, composeIds, "declaration order must match too")
    }

    /**
     * Name parity alone would pass if the builder's [OptionsMenuStateBuilder.idFor] mapping pointed
     * a name at the wrong `R.id`. Resolve each XML id against the real resource table and check the
     * builder's own id<->resId mapping agrees.
     */
    @Test
    fun eachClassicIdMapsToTheSameResourceIdTheComposeBuilderUses() {
        val res = activity.resources
        for (name in parseMenuItemIds(menuXml)) {
            val resId = res.getIdentifier(name, "id", activity.packageName)
            assertTrue(resId != 0, "R.id.$name must exist")
            assertEquals(name, OptionsMenuStateBuilder.idFor(resId, 0), "idFor(R.id.$name)")
            assertEquals(
                OptionsMenuStateBuilder.ParsedId(resId, 0),
                OptionsMenuStateBuilder.parseId(name),
                "parseId(\"$name\")",
            )
        }
    }

    /** Documented difference 1: rendered order moves `allTextOptions` to the end. */
    @Test
    fun allTextOptionsIsTheLastRowTheBuilderEmits() {
        val ids = activity.readingCommands.buildOptionsMenuItems().map { it.id }
        assertTrue(ids.isNotEmpty(), "sanity: the builder must emit something")
        assertEquals("allTextOptions", ids.last(), "orderInCategory=1000 puts it last; got $ids")
        assertEquals(
            "allTextOptions",
            OptionsMenuStateBuilder.staticItemIds.last(),
            "…and it is also last in the XML's declaration order, so the two happen to agree here",
        )
    }

    /** Documented difference 2: the dynamic rows have no static `<item>`. */
    @Test
    fun dynamicTextOptionRowsHaveNoStaticXmlCounterpart() {
        assertFalse(
            "textOptionItem" in parseMenuItemIds(menuXml),
            "classic `menu.add`s these at runtime; there is no <item> for them",
        )
        assertFalse("textOptionItem" in OptionsMenuStateBuilder.staticItemIds)
        assertEquals(
            "textOptionItem:2",
            OptionsMenuStateBuilder.idFor(net.bible.android.activity.R.id.textOptionItem, 2),
            "a dynamic row's id carries its order, so it needs no static entry",
        )
    }

    // ---------------------------------------------------------------- AltKeyO

    @Test
    fun openOverflowMenuBuildsTheItemsAndExpandsTheComposeMenu() {
        val host = ComposeReadingViewHost(activity)
        assertFalse(host.overflowExpandedForTest, "sanity: starts closed")

        host.openOverflowMenu()

        assertTrue(host.overflowExpandedForTest, "the Compose overflow menu must be expanded")
        // Without this the comparison below is vacuous: an empty list equals an empty list.
        assertTrue(host.overflowItemsForTest.isNotEmpty(), "sanity: the menu must have rows")
        assertEquals(
            activity.readingCommands.buildOptionsMenuItems().map { it.id },
            host.overflowItemsForTest.map { it.id },
            "it must show exactly what the bridge builds",
        )
    }

    /**
     * Fix round 1, Major 1. `ReadingViewScreen` composes `ReadingToolbar` only `if (!fullScreen)`,
     * and the toolbar is what hosts both the overflow button and the `ReadingOverflowMenu`
     * `DropdownMenu` that `overflowExpanded` drives. `"AltKeyO"` is reachable in fullscreen (the
     * BibleView has focus, so `keyboard.ts` sends it), so setting the flag there used to show
     * nothing AND leave it stuck true until the toolbar came back — at which point the menu popped
     * open unrequested. Leaving fullscreen first is what `ReadingSearchController` already does for
     * the identical problem (`onLeaveFullScreen`).
     */
    @Test
    fun openOverflowMenuLeavesFullscreenSoThereIsAToolbarToAnchorTheMenuOn() {
        val host = ComposeReadingViewHost(activity)
        activity.fullScreen = true
        assertTrue(activity.fullScreen, "sanity: the fixture really is in fullscreen")

        host.openOverflowMenu()

        assertFalse(
            activity.fullScreen,
            "opening the overflow menu must leave fullscreen — in fullscreen no ReadingToolbar is " +
                "composed, so the DropdownMenu has nothing to anchor on",
        )
        assertTrue(host.overflowExpandedForTest, "…and then the menu opens")
    }

    /**
     * Fix round 1, Major 1, the other toolbar-less state: `ReadingToolbar` renders the search row
     * and `return`s before the normal toolbar, so the overflow button and its menu do not exist in
     * search mode either. Here the fix is to do NOTHING rather than to close search mode: closing
     * it would throw away the user's typed query, and there is no classic behaviour to preserve
     * (toolbar search mode is a Compose-era feature; classic search was a separate Activity).
     */
    @Test
    fun openOverflowMenuDoesNothingWhileTheToolbarIsInSearchMode() {
        val host = ComposeReadingViewHost(activity)
        host.searchController.open()
        assertTrue(host.searchController.searchModeActive.value, "sanity: search mode is active")

        host.openOverflowMenu()

        assertFalse(
            host.overflowExpandedForTest,
            "the search row has no overflow button to anchor the menu on, so nothing may be set",
        )
        assertTrue(
            host.searchController.searchModeActive.value,
            "…and the user's search session (and typed query) must survive untouched",
        )
    }

    @Test
    fun altKeyOGoesToTheComposeOverflowMenuNotTheNativePopup() {
        val src = codeOf(File("src/main/java/net/bible/android/view/activity/page/BibleJavascriptInterface.kt"))
        val line = src.lineSequence().firstOrNull { it.contains("\"AltKeyO\"") }
        assertTrue(line != null, "the AltKeyO shortcut must still exist")
        assertTrue(
            line.contains("openOverflowMenu"),
            "AltKeyO must open the Compose overflow menu; got: $line",
        )
        assertFalse(
            src.contains("showOptionsMenu"),
            "no CALL of the deleted native options popup may remain",
        )
    }

    /** [file]'s source with whole-line `//` comments dropped, so a scan sees code, not prose. */
    private fun codeOf(file: File): String =
        file.readLines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

    // ---------------------------------------------------------------- swap-menu long press

    @Test
    fun bibleLongPressInSwapMenuModeOpensTheComposeQuickDocMenu() {
        CommonUtils.settings.setString("toolbar_button_actions", "swap-menu")
        val bibles = documentControl.biblesForVerse
        assertTrue(
            bibles.size > 2,
            "sanity: the test Sword modules must offer more than the 2 that QuickDocPicker would " +
                "switch between directly without opening a menu; found ${bibles.size}",
        )
        val host = ComposeReadingViewHost(activity)
        activity.composeReadingViewHost = host

        activity.readingCommands.composeBibleLongClick()

        assertTrue(host.bibleQuickDocForTest.expanded, "the Compose quick-doc menu must open")
        assertEquals(bibles.size, host.bibleQuickDocForTest.items.size, "one row per offered Bible")
        assertFalse(host.commentaryQuickDocForTest.expanded, "only the Bible menu opens")
    }

    /**
     * Fix round 1, Minor 5: the commentary long press's omission of GENERAL_BOOK/DICTIONARY books,
     * pinned **behaviourally** rather than only by the source scan below.
     *
     * What makes the difference observable is `QuickDocPicker`'s "exactly 2 documents -> switch
     * directly, show no menu" rule and the measured fixture:
     * - `commentariesForVerse` is exactly 2 here. NOT zero: no commentary SWORD module is
     *   installed, but [net.bible.service.download.FakeBookFactory]'s two
     *   pseudo-commentaries (`MyNote`, `Compare`) are always appended, so the bare list is 2 ->
     *   `SwitchDirectly` -> no menu, and the current document becomes one of those two.
     * - the extras the SHORT press appends are at least the two Strong's lexicons
     *   (`strongsgreek`, `strongshebrew`), so appending them takes the list past 2 ->
     *   `ShowPopup` -> a menu and no switch.
     *
     * The extras count is asserted as "at least one", NOT as an exact number: in the FULL `:app`
     * suite (one JVM, shared JSword state) an earlier test registers a third GENERAL_BOOK,
     * `AIDocuments`, so an `assertEquals(2, extras.size)` premise passes in a scoped `--tests` run
     * and fails in the real gate. What the argument actually needs is only that appending the
     * extras would push the list past the switch-directly threshold.
     *
     * Hence both assertions below can see the regression, from opposite sides.
     */
    @Test
    fun commentaryLongPressSwitchesToACommentaryAndNeverOffersDictionaries() {
        CommonUtils.settings.setString("toolbar_button_actions", "swap-menu")
        val commentaries = documentControl.commentariesForVerse
        assertEquals(
            2,
            commentaries.size,
            "fixture premise: the bare list is the 2 pseudo-commentaries, the size QuickDocPicker " +
                "switches directly on; got ${commentaries.map { it.initials }}",
        )
        val extras = SwordDocumentFacade.getBooks(BookCategory.GENERAL_BOOK) +
            SwordDocumentFacade.getBooks(BookCategory.DICTIONARY)
        assertTrue(
            commentaries.size + extras.size > 2,
            "fixture premise: appending the short press's extras must push the list past the " +
                "switch-directly threshold of 2, or this test cannot see the regression; got " +
                "${commentaries.map { it.initials }} + ${extras.map { it.initials }}",
        )
        val host = ComposeReadingViewHost(activity)
        activity.composeReadingViewHost = host
        val before = documentControl.currentDocument?.initials

        activity.readingCommands.composeCommentaryLongClick()

        assertFalse(
            host.commentaryQuickDocForTest.expanded,
            "with exactly 2 documents the picker switches directly and shows no menu — a menu here " +
                "means the GENERAL_BOOK/DICTIONARY extras were appended (4 rows)",
        )
        val after = documentControl.currentDocument?.initials
        assertTrue(after != before, "…and the direct switch really happened; still on $before")
        assertTrue(
            after in commentaries.map { it.initials },
            "the long press must switch to one of ITS OWN book list's documents, never to a " +
                "dictionary; got $after, list ${commentaries.map { it.initials }}",
        )
    }

    /**
     * The commentary long-press book list must stay **verbatim** what classic's `commentaryLongPress`
     * passed: `commentariesForVerse` and nothing else. Unlike the short-press
     * (`composeCommentaryClick`/`onCommentary`), it deliberately does NOT append GENERAL_BOOK +
     * DICTIONARY books. Kept alongside the behavioural test above: this one names the mistake in
     * the source, which is the faster diagnosis when it is made again.
     */
    @Test
    fun theTwoLongPressBranchesUseTheComposeQuickDocMenuWithTheirOwnBookLists() {
        // Reading-host re-typing R3 (design spec §3.2): both long-press bodies moved off
        // `MainBibleActivity` into `ReadingCommands`, so the collaborator is where the needles below
        // live. (The behavioural tests above drive `ReadingCommands` on the reading-route nav host
        // since slice 8 F2.)
        val src = codeOf(File("src/main/java/net/bible/android/view/activity/page/ReadingCommands.kt"))
        val bibleBody = bodyOf(src, "internal fun composeBibleLongClick")
        val commentaryBody = bodyOf(src, "internal fun composeCommentaryLongClick")

        assertFalse("menuForDocs" in bibleBody, "the Bible long press must not open the native popup:\n$bibleBody")
        assertFalse("menuForDocs" in commentaryBody, "the commentary long press must not open the native popup:\n$commentaryBody")
        assertTrue("openBibleQuickDoc" in bibleBody, "…it opens the Compose quick-doc menu instead:\n$bibleBody")
        assertTrue("openCommentaryQuickDoc" in commentaryBody, "…it opens the Compose quick-doc menu instead:\n$commentaryBody")

        assertTrue(
            "documentControl.commentariesForVerse" in commentaryBody,
            "the commentary long press keeps its own book list:\n$commentaryBody",
        )
        assertFalse(
            "GENERAL_BOOK" in commentaryBody || "DICTIONARY" in commentaryBody,
            "the commentary LONG press deliberately does not append general books/dictionaries " +
                "(unlike the short press):\n$commentaryBody",
        )
        assertTrue(
            "documentControl.biblesForVerse" in bibleBody,
            "the Bible long press keeps its own book list:\n$bibleBody",
        )
    }

    /** The text between [signature] and the next top-level `    internal fun `/`    fun `/`    private fun `. */
    private fun bodyOf(src: String, signature: String): String {
        val start = src.indexOf(signature)
        assertTrue(start >= 0, "$signature not found")
        val rest = src.substring(start + signature.length)
        val end = Regex("""\n    (?:/\*\*|(?:internal |private |public )?(?:fun|val|var|@) )""")
            .find(rest)?.range?.first ?: rest.length
        return signature + rest.substring(0, end)
    }

    // ---------------------------------------------------------------- no native popup left

    /**
     * Guard. Exactly ONE `PopupMenu(` may remain in `MainBibleActivity`: `menuForDocs`, which is
     * still reachable from the CLASSIC toolbar's `binding.bibleButton`/`commentaryButton`
     * listeners in `setupToolbarButtons` (and from `composeBibleClick`/`composeCommentaryClick`'s
     * non-swap `else` branches, which the Compose host never takes). Removing those belongs to the
     * XML/drawer removal task, not here.
     *
     * Expect this to go RED at Task 11, which deletes `menuForDocs` along with the classic toolbar
     * XML and its listeners: the correct count becomes 0 then, and that red is the reminder, not a
     * mystery.
     */
    @Test
    fun mainBibleActivityHasNoNativeOptionsPopupLeft() {
        // Reading-host re-typing R3 (design spec §3.2): scanned across BOTH files. The command
        // surface moved to `ReadingCommands.kt` and `composeBibleClick`/`composeCommentaryClick`'s
        // non-swap branches went with it, so a re-introduced native options popup could now land in
        // either file. `menuForDocs` itself — the one surviving `PopupMenu(` — deliberately stayed
        // on the Activity (the CLASSIC toolbar listeners still call it), so the count is still 1,
        // but it is now 1 ACROSS the pair rather than 1 in one file.
        val activitySrc = codeOf(File("src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt"))
        val commandsSrc = codeOf(File("src/main/java/net/bible/android/view/activity/page/ReadingCommands.kt"))
        val src = activitySrc + "\n" + commandsSrc

        assertTrue(commandsSrc.length > 1000, "ReadingCommands.kt is empty or missing")
        assertFalse("fun showOptionsMenu" in src, "the native options PopupMenu must be gone")
        assertFalse("fun handlePrefItem" in src, "its MenuItem-typed dispatcher must be gone with it")
        assertFalse("getItemOptions(item: MenuItem)" in src, "…and its MenuItem-typed overload")

        val popups = Regex("""PopupMenu\(""").findAll(src).count()
        assertEquals(1, popups, "only menuForDocs' PopupMenu may remain (see this test's kdoc)")
    }
}
