package net.bible.android.view.activity.nav.ports

import net.bible.sharedcore.docs.TutorialVideos
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

    @Test fun workspacesTopicStillShowsItsSingleTipWithSponsor() = withHost { activity, dialogs ->
        AndroidHelpDialogs(activity).show(HelpTopic.WORKSPACES)
        val notice = assertIs<AppDialogRequest.Notice>(assertNotNull(dialogs.pending.value).request)
        val html = assertIs<AppDialogRequest.NoticeBlock.Html>(notice.blocks.first()).html
        assertContains(html, "<b>${activity.getString(R.string.help_workspaces_title)}</b>")
        assertContains(html, "href=\"${DocsLinks.page("workspaces")}\"")
        assertContains(html, "href=\"${TutorialVideos.url(TutorialVideos.Episode.TUTORIAL_07_WORKSPACES, "en")}\"")
        // English app: the video label carries no "(English)" qualifier.
        assertContains(html, ">${activity.getString(R.string.watch_tutorial_video_plain)}</a>")
        assertIs<AppDialogRequest.NoticeBlock.IconLine>(notice.blocks.last())
    }

    @Test fun appLanguageIsTheUiLocaleTag() = withHost { activity, _ ->
        assertEquals(activity.resources.configuration.locales.get(0).toLanguageTag(), AndroidHelpDialogs(activity).appLanguage())
    }

    @Test fun everyTipKeyResolvesToAnAndroidString() = withHost { activity, _ ->
        val help = AndroidHelpDialogs(activity)
        net.bible.sharedui.app.ports.HelpAndTips.items.forEach {
            assertTrue(help.tipText(it.titleKey).isNotBlank(), it.titleKey)
            assertTrue(help.tipText(it.textKey).isNotBlank(), it.textKey)
        }
        assertEquals(CommonUtils.applicationVersionName, help.versionName())
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
