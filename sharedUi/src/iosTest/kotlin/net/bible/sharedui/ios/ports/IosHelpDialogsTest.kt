package net.bible.sharedui.ios.ports

import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.strings.keyed.StringTable
import platform.Foundation.NSBundle
import kotlin.test.*
import net.bible.sharedui.host.ports.HostHelpDialogs

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
    @Test fun licenceShowsEntireBundledHtmlWithOneOkButton() {
        val dialogs = AppDialogController()
        val reads = mutableListOf<String>()
        val html = "<!doctype html>\n<p>GPL &amp; full license</p>\n<pre>last line</pre>"
        try {
            HostHelpDialogs(dialogs, { task4Table() }, bundleHtml = { name -> reads += name; html }, versionName = ::iosBundleVersionName)
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
