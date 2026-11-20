package net.bible.android.view.activity.nav.ports

import net.bible.sharedcore.docs.HelpLinks
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.docs.DocsLink
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
        // #4159: one link into the in-app user guide's Workspaces page (with its video) instead of video + manual.
        assertContains(html, "<a href=\"${HelpLinks.url(DocsLink.WORKSPACES)}\">${activity.getString(R.string.help_in_user_guide)}</a>")
        assertFalse(html.contains("youtube"))
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

    @Test fun guideKeysResolveFromAndroidResourcesAndUnknownKeysFallBack() = withHost { activity, _ ->
        val help = AndroidHelpDialogs(activity)
        val titles = net.bible.sharedcore.docs.UserGuideToc.sections.flatMap { section ->
            listOf(section.key to section.title) + section.pages.map { it.key to it.title }
        } + net.bible.sharedcore.docs.UserGuideToc.episodeTitles.values.map { it.key to it.title }
        titles.forEach { (key, title) -> assertEquals(title, help.guideTitle(key), key) }
        assertEquals("unknown_guide_key", help.guideTitle("unknown_guide_key"))
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
