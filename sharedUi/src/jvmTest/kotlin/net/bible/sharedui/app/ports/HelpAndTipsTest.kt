package net.bible.sharedui.app.ports

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
        assertEquals(8, pairs.size, "Android extraction must not pass vacuously")
        assertEquals(pairs, HelpAndTips.items.map { it.titleKey to it.textKey })
    }

    @Test fun helpItemsRetainManualPagesAndTutorialTargets() {
        assertEquals(listOf("navigation", null, "windows", "bookmarks", "study_pads", "search", "workspaces", null),
            HelpAndTips.items.map { it.docPage })
        val windows = "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mmiq_X6G-vDhoAIq9sDnrIQ"
        assertEquals(listOf(null, null, windows,
            "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MlzNt0Zpna-QoTBpEpWSden",
            "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MkMiGz7cjGASOYjElr1Q76m", null, windows, null),
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
