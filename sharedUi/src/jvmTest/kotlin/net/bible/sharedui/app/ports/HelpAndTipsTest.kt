package net.bible.sharedui.app.ports

import net.bible.sharedcore.docs.TutorialVideos
import java.io.File
import kotlin.test.*
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.strings.keyed.StringTable

class HelpAndTipsTest {
    @Test fun matchesAndroidShowHelp() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "app/src/main/java/net/bible/service/common/CommonUtils.kt").isFile }
        val source = File(root, "app/src/main/java/net/bible/service/common/CommonUtils.kt").readText()
            .substringAfter("fun showHelp(").substringBefore("fun showHelpDialog(")
        val pairs = Regex("HelpItem\\(R\\.string\\.(\\w+), R\\.string\\.(\\w+)")
            .findAll(source).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals(10, pairs.size, "Android extraction must not pass vacuously")
        assertEquals(pairs, HelpAndTips.items.map { it.titleKey to it.textKey })
    }

    @Test fun helpItemsRetainManualPagesAndTutorialTargets() {
        assertEquals(
            listOf("navigation", null, "documents", "windows", "bookmarks", "study_pads", "search", "workspaces", "look_and_feel", null),
            HelpAndTips.items.map { it.docPage })
        val e = TutorialVideos.Episode
        // Every video link comes from the registry (a published episode's own URL, else the placeholder).
        assertEquals(
            listOf(e.TUTORIAL_02_FINDING_YOUR_WAY, e.TUTORIAL_03_VERSE_MENU, e.TUTORIAL_04_STRONGS_DICTIONARIES_CROSSREFS,
                e.TUTORIAL_06_WINDOWS_2_PIN_SYNC_LINKS, e.TUTORIAL_08_BOOKMARKS, e.TUTORIAL_11_STUDY_PADS,
                e.TUTORIAL_12_SEARCH_1_BASICS, e.TUTORIAL_07_WORKSPACES, e.TUTORIAL_19_APPEARANCE_1_NIGHT_MODE_FONTS_COLOURS, null)
                .map { ep -> ep?.let(TutorialVideos::url) },
            HelpAndTips.items.map { it.videoLink })
    }

    @Test fun optionalSponsorDoesNotRemoveHelpDocsOrVersionAndDefaultsToAndroidBehavior() {
        val table = StringTable("en", { it }, { key, args -> key + args.joinToString() }, { n, _ -> n }, { emptyList() })
        val core = DocumentDialogsCore(AppDialogController(), { table })
        val items = listOf(DocumentDialogsCore.HelpItem("Navigation", "Body", docUrl = DocsLinks.page("navigation")))
        val android = core.helpNotice(items, versionMsg = "Version 1")
        assertTrue(android.blocks.any { it is AppDialogRequest.NoticeBlock.IconLine })
        val ios = core.helpNotice(items, versionMsg = "Version 1", includeSponsor = false)
        assertFalse(ios.blocks.any { it is AppDialogRequest.NoticeBlock.IconLine })
        val html = ios.blocks.filterIsInstance<AppDialogRequest.NoticeBlock.Html>().map { it.html }
        assertEquals(3, html.size)
        assertContains(html[0], "<b>Navigation</b>")
        assertContains(html[0], DocsLinks.page("navigation"))
        assertContains(html[1], DocsLinks.BASE_URL)
        assertEquals("<i>Version 1</i>", html[2])
        assertFalse(html.joinToString().contains("shop.andbible.org"))
    }
}
