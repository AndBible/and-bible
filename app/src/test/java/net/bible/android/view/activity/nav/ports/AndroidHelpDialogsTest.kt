package net.bible.android.view.activity.nav.ports

import net.bible.android.AppDialogControllerResetRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.app.ports.HelpTopic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidHelpDialogsTest {
    @get:Rule val dialogReset = AppDialogControllerResetRule()

    private fun withHost(block: (CalculatorComposeActivity, AppDialogController) -> Unit) {
        val host = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        val dialogs = KoinJavaComponent.get<AppDialogController>(AppDialogController::class.java)
        try { block(host.get(), dialogs) }
        finally {
            dialogs.cancelAll()
            host.pause().stop().destroy()
        }
    }

    @Test fun helpAndTipsDispatchesAllItemsWithRealVersionAndKeepsAndroidSponsor() = withHost { activity, dialogs ->
        AndroidHelpDialogs(activity).show(HelpTopic.HELP_AND_TIPS)
        val notice = assertIs<AppDialogRequest.Notice>(assertNotNull(dialogs.pending.value).request)
        assertEquals(activity.getString(R.string.help), notice.title)
        assertEquals(activity.getString(android.R.string.ok), notice.confirmText)
        assertTrue(notice.showTitleLogo)
        val html = assertIs<AppDialogRequest.NoticeBlock.Html>(notice.blocks.first()).html
        val pairs = listOf(
            R.string.help_nav_title to R.string.help_nav_text,
            R.string.help_contextmenus_title to R.string.help_contextmenus_text,
            R.string.help_window_pinning_title to R.string.help_window_pinning_text,
            R.string.help_bookmarks_title to R.string.help_bookmarks_text,
            R.string.studypads to R.string.help_studypads_text,
            R.string.help_search_title to R.string.help_search_text2,
            R.string.help_workspaces_title to R.string.help_workspaces_text,
            R.string.help_hidden_features_title to R.string.help_hidden_features_text,
        )
        var previous = -1
        pairs.forEach { (title, text) ->
            val position = html.indexOf("<b>${activity.getString(title)}</b>")
            assertTrue(position > previous, "Missing/reordered help item $title")
            assertContains(html, activity.getString(text).replace("\n", "<br>"))
            previous = position
        }
        listOf(DocsLinks.page("navigation"), DocsLinks.page("windows"), DocsLinks.page("bookmarks"),
            DocsLinks.page("study_pads"), DocsLinks.page("search"), DocsLinks.page("workspaces"),
            "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mmiq_X6G-vDhoAIq9sDnrIQ",
            "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MlzNt0Zpna-QoTBpEpWSden",
            "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MkMiGz7cjGASOYjElr1Q76m",
        ).forEach { assertContains(html, "href=\"$it\"") }
        assertEquals(4, notice.blocks.size)
        assertContains(assertIs<AppDialogRequest.NoticeBlock.Html>(notice.blocks[1]).html, DocsLinks.BASE_URL)
        assertIs<AppDialogRequest.NoticeBlock.IconLine>(notice.blocks[2])
        val version = activity.getString(R.string.version_text, CommonUtils.applicationVersionName)
        assertEquals("<i>$version</i>", assertIs<AppDialogRequest.NoticeBlock.Html>(notice.blocks.last()).html)
    }

    @Test fun licenceDispatchesEntireRealHtmlWithOneOkButton() = withHost { activity, dialogs ->
        AndroidHelpDialogs(activity).show(HelpTopic.LICENCE)
        val message = assertIs<AppDialogRequest.Message>(assertNotNull(dialogs.pending.value).request)
        val expected = activity.resources.openRawResource(R.raw.license).use { it.readBytes().decodeToString() }
        assertEquals(expected, message.message)
        assertEquals(activity.getString(R.string.app_licence_title), message.title)
        assertEquals(activity.getString(android.R.string.ok), message.confirmText)
        assertNull(message.dismissText)
        assertNull(message.neutralText)
        assertTrue(message.cancellable)
    }
}
