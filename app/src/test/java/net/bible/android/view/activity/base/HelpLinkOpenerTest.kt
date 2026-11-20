package net.bible.android.view.activity.base

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.docs.HelpLinks
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.components.AbHtmlText
import org.junit.After
import org.junit.Assert.assertEquals
import kotlin.test.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * #4159: on Android, a help link opens the nav host on Help & tips with the topic (the singleTop host navigates its
 * live graph when it is already on top), even in discrete mode, where an external link would first ask.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = TestBibleApplication::class)
class HelpLinkOpenerTest {
    @get:Rule val compose = createComposeRule()

    @After
    fun tearDown() {
        CommonUtils.realSharedPreferences.edit().remove("discrete_mode").apply()
    }

    @Test fun aHelpLinkStartsTheNavHostOnTheTopicsHelpRoute() {
        CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", true).apply()
        compose.setContent {
            AbAppTheme {
                FailClosedLinkRouting {
                    AbHtmlText(html = "<a href=\"${HelpLinks.url(DocsLink.SPEAK)}\">help me</a>")
                }
            }
        }
        compose.onNodeWithText("help me").performClick()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.onNodeWithText(context.getString(R.string.external_link_open_question)).assertDoesNotExist()
        val started = assertNotNull(shadowOf(context).nextStartedActivity)
        assertEquals(NavHostComposeActivity::class.java.name, started.component?.className)
        assertEquals(NavRoutes.help(DocsLink.SPEAK), started.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
    }
}
