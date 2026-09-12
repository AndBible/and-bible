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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
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
    private lateinit var activity: MainBibleActivity

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        // Built WITHOUT `.create()`, for the reasons OptionsMenuStateBuilderTest's kdoc gives.
        activity = Robolectric.buildActivity(MainBibleActivity::class.java).get()
        activity.windowRepository = windowRepository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
    }

    @After
    fun tearDown() {
        CommonUtils.settings.setString("toolbar_button_actions", null)
        CommonUtils.settings.setString("lastDisplaySettings", null)
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
        val ids = activity.buildOptionsMenuItems().map { it.id }
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
        assertEquals(
            activity.buildOptionsMenuItems().map { it.id },
            host.overflowItemsForTest.map { it.id },
            "it must show exactly what the bridge builds",
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
        val bibles = activity.documentControl.biblesForVerse
        assertTrue(
            bibles.size > 2,
            "sanity: the test Sword modules must offer more than the 2 that QuickDocPicker would " +
                "switch between directly without opening a menu; found ${bibles.size}",
        )
        val host = ComposeReadingViewHost(activity)
        activity.composeReadingViewHost = host

        activity.composeBibleLongClick()

        assertTrue(host.bibleQuickDocForTest.expanded, "the Compose quick-doc menu must open")
        assertEquals(bibles.size, host.bibleQuickDocForTest.items.size, "one row per offered Bible")
        assertFalse(host.commentaryQuickDocForTest.expanded, "only the Bible menu opens")
    }

    /**
     * The commentary long-press book list must stay **verbatim** what classic's `commentaryLongPress`
     * passed: `commentariesForVerse` and nothing else. Unlike the short-press
     * (`composeCommentaryClick`/`onCommentary`), it deliberately does NOT append GENERAL_BOOK +
     * DICTIONARY books. A behavioural assertion cannot see this difference in a test fixture with no
     * commentary modules installed (both lists would be trivially short), so it is pinned on the
     * source: the branch must pass the bare property.
     */
    @Test
    fun theTwoLongPressBranchesUseTheComposeQuickDocMenuWithTheirOwnBookLists() {
        val src = codeOf(File("src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt"))
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
     */
    @Test
    fun mainBibleActivityHasNoNativeOptionsPopupLeft() {
        val src = codeOf(File("src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt"))

        assertFalse("fun showOptionsMenu" in src, "the native options PopupMenu must be gone")
        assertFalse("fun handlePrefItem" in src, "its MenuItem-typed dispatcher must be gone with it")
        assertFalse("getItemOptions(item: MenuItem)" in src, "…and its MenuItem-typed overload")

        val popups = Regex("""PopupMenu\(""").findAll(src).count()
        assertEquals(1, popups, "only menuForDocs' PopupMenu may remain (see this test's kdoc)")
    }
}
