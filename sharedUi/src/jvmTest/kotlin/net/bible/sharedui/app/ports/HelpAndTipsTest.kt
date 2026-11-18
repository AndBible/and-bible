package net.bible.sharedui.app.ports

import net.bible.sharedcore.docs.TutorialVideos
import java.io.File
import kotlin.test.*
import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.strings.keyed.StringTable

class HelpAndTipsTest {
    @Test fun helpItemsRetainManualPagesAndTutorialTargets() {
        assertEquals(
            listOf("navigation", null, "documents", "windows", "bookmarks", "study_pads", "search", "workspaces", "look_and_feel", null),
            HelpAndTips.items.map { it.docPage })
        val e = TutorialVideos.Episode.entries.associateBy { it.name }
        assertEquals(
            listOf(e.getValue("TUTORIAL_02_FINDING_YOUR_WAY"), e.getValue("TUTORIAL_03_VERSE_MENU"), e.getValue("TUTORIAL_04_STRONGS_DICTIONARIES_CROSSREFS"),
                e.getValue("TUTORIAL_06_WINDOWS_2_PIN_SYNC_LINKS"), e.getValue("TUTORIAL_08_BOOKMARKS"), e.getValue("TUTORIAL_11_STUDY_PADS"),
                e.getValue("TUTORIAL_12_SEARCH_1_BASICS"), e.getValue("TUTORIAL_07_WORKSPACES"), e.getValue("TUTORIAL_19_APPEARANCE_1_NIGHT_MODE_FONTS_COLOURS"), null)
                ,
            HelpAndTips.items.map { it.episode })
        // Every video link comes from the registry (a published episode's own URL, else the placeholder).
        assertEquals(HelpAndTips.items.map { it.episode?.let(TutorialVideos::url) }, HelpAndTips.items.map { HelpAndTips.videoUrl(it, "en") })
    }

    @Test fun optionalSponsorDoesNotRemoveHelpDocsOrVersionAndDefaultsToAndroidBehavior() {
        val table = StringTable("en", { it }, { key, args -> key + args.joinToString() }, { n, _ -> n }, { emptyList() })
        val core = DocumentDialogsCore(AppDialogController(), { table })
        val items = listOf(DocumentDialogsCore.HelpItem("Navigation", "Body", docUrl = DocsLink.NAVIGATION.url))
        val android = core.helpNotice(items, versionMsg = "Version 1")
        assertTrue(android.blocks.any { it is AppDialogRequest.NoticeBlock.IconLine })
        val ios = core.helpNotice(items, versionMsg = "Version 1", includeSponsor = false)
        assertFalse(ios.blocks.any { it is AppDialogRequest.NoticeBlock.IconLine })
        val html = ios.blocks.filterIsInstance<AppDialogRequest.NoticeBlock.Html>().map { it.html }
        assertEquals(3, html.size)
        assertContains(html[0], "<b>Navigation</b>")
        assertContains(html[0], DocsLink.NAVIGATION.url)
        assertContains(html[1], DocsLinks.BASE_URL)
        assertEquals("<i>Version 1</i>", html[2])
        assertFalse(html.joinToString().contains("shop.andbible.org"))
    }

    @Test fun videoLabelIsPlainForEnglishAndKeepsEnglishQualifierWithoutMatchingCaptions() {
        val strings = net.bible.sharedui.strings.iosStrings("en")
        val labels = { lang: String -> HelpAndTips.toUi(strings, { it }, appLanguage = lang).filter { it.videoUrl != null }.map { it.videoLabel }.distinct() }
        assertEquals(listOf(strings.watchTutorialVideoPlain), labels("en-GB"))
        // No real episode has captions yet, so a non-English app keeps "(English)".
        assertEquals(listOf(strings.watchTutorialVideo), labels("fi"))
        assertNotEquals(strings.watchTutorialVideo, strings.watchTutorialVideoPlain)
        assertFalse(strings.watchTutorialVideoPlain.contains("English"))
    }

    @Test fun docUrlMapsEveryItemPageAndRejectsUnknown() {
        HelpAndTips.items.forEach { item ->
            // Keep the help-item metadata aligned with the catalog's resolved URLs.
            val expected = item.docPage?.let { DocsLinks.BASE_URL + it + "/" }
            assertEquals(expected, HelpAndTips.docUrl(item.docPage))
        }
        assertFailsWith<IllegalStateException> { HelpAndTips.docUrl("nope") }
    }
}
