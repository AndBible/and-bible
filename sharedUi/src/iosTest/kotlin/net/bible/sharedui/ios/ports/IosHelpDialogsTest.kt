package net.bible.sharedui.ios.ports

import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.strings.keyed.StringTable
import platform.Foundation.NSBundle
import kotlin.test.*

internal fun task4Table(language: String = "en") = StringTable(
    language, { "$language:$it" },
    { key, args -> "$language:$key(${args.joinToString("|")})" },
    { key, count -> "$language:$key:$count" }, { emptyList() },
)

internal fun AppDialogController.task4Request(): AppDialogRequest {
    val request = assertNotNull(pending.value).request
    return (request as? AppDialogRequest.Localized)?.resolve() ?: request
}

class IosHelpDialogsTest {
    @Test fun helpAndTipsPostsAllEightItemsWithVersionAndWithoutShopSponsor() {
        val dialogs = AppDialogController()
        try {
            IosHelpDialogs(dialogs, { task4Table() }, ::iosBundledHtml, ::iosBundleVersionName).show(HelpTopic.HELP_AND_TIPS)
            val notice = assertIs<AppDialogRequest.Notice>(dialogs.task4Request())
            assertEquals("en:help", notice.title)
            assertEquals("en:okay", notice.confirmText)
            assertTrue(notice.showTitleLogo)
            assertFalse(notice.blocks.any { it is AppDialogRequest.NoticeBlock.IconLine })
            val blocks = notice.blocks.map { assertIs<AppDialogRequest.NoticeBlock.Html>(it).html }
            assertEquals(3, blocks.size)
            val pairs = listOf(
                "help_nav_title" to "help_nav_text",
                "help_contextmenus_title" to "help_contextmenus_text",
                "help_window_pinning_title" to "help_window_pinning_text",
                "help_bookmarks_title" to "help_bookmarks_text",
                "studypads" to "help_studypads_text",
                "help_search_title" to "help_search_text2",
                "help_workspaces_title" to "help_workspaces_text",
                "help_hidden_features_title" to "help_hidden_features_text",
            )
            var previous = -1
            pairs.forEach { (title, body) ->
                val position = blocks[0].indexOf("<b>en:$title</b>")
                assertTrue(position > previous, "Missing or reordered $title")
                assertContains(blocks[0], "en:$body")
                previous = position
            }
            listOf(DocsLinks.page("navigation"), DocsLinks.page("windows"), DocsLinks.page("bookmarks"),
                DocsLinks.page("study_pads"), DocsLinks.page("search"), DocsLinks.page("workspaces"),
                "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mmiq_X6G-vDhoAIq9sDnrIQ",
                "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MlzNt0Zpna-QoTBpEpWSden",
                "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MkMiGz7cjGASOYjElr1Q76m",
            ).forEach { assertContains(blocks[0], "href=\"$it\"") }
            assertContains(blocks[1], DocsLinks.BASE_URL)
            val version = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
            assertEquals("<i>en:version_text($version)</i>", blocks[2])
            assertFalse(blocks.joinToString().contains("shop.andbible.org"))
        } finally { dialogs.cancelAll() }
    }

    @Test fun retainedHelpReadsCurrentTableWhenResolvedAgain() {
        val dialogs = AppDialogController()
        var table = task4Table("en")
        try {
            IosHelpDialogs(dialogs, { table }, ::iosBundledHtml, ::iosBundleVersionName).show(HelpTopic.HELP_AND_TIPS)
            assertEquals("en:help", assertIs<AppDialogRequest.Notice>(dialogs.task4Request()).title)
            table = task4Table("fi")
            val refreshed = assertIs<AppDialogRequest.Notice>(dialogs.task4Request())
            assertEquals("fi:help", refreshed.title)
            assertContains(assertIs<AppDialogRequest.NoticeBlock.Html>(refreshed.blocks.first()).html, "fi:help_nav_text")
            assertContains(assertIs<AppDialogRequest.NoticeBlock.Html>(refreshed.blocks.last()).html, "fi:version_text(")
        } finally { dialogs.cancelAll() }
    }

    @Test fun licenceShowsEntireBundledHtmlWithOneOkButton() {
        val dialogs = AppDialogController()
        val reads = mutableListOf<String>()
        val html = "<!doctype html>\n<p>GPL &amp; full license</p>\n<pre>last line</pre>"
        try {
            IosHelpDialogs(dialogs, { task4Table() }, bundleHtml = { name -> reads += name; html }, versionName = ::iosBundleVersionName)
                .show(HelpTopic.LICENCE)
            val request = assertIs<AppDialogRequest.Message>(dialogs.task4Request())
            assertTrue(reads.isNotEmpty())
            assertTrue(reads.all { it == "license" })
            assertEquals("en:app_licence_title", request.title)
            assertEquals(html, request.message)
            assertEquals("en:okay", request.confirmText)
            assertNull(request.dismissText)
            assertNull(request.neutralText)
            assertTrue(request.cancellable)
        } finally { dialogs.cancelAll() }
    }
}
