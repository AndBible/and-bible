package net.bible.android.view.compose

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Slice 8 B3: the four `startKeyChooser` implementations that use `STD_REQUEST_CODE` launch the
 * nav host on the chooser's ROUTE (arguments built in, never put as extras), at STD. The reading
 * destination's collector (B1) answers them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ChooserKeyLaunchRouteTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        DatabaseResetter.resetDatabase()
    }

    private fun host(): NavHostComposeActivity {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }.create().get()
    }

    private fun assertLaunched(activity: NavHostComposeActivity, route: String) {
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(NavHostComposeActivity::class.java.name, started.intent.component?.className)
        assertEquals(route, started.intent.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE))
        assertEquals(ActivityBase.STD_REQUEST_CODE, started.requestCode)
    }

    @Test
    fun theBibleAndCommentaryChoosersOpenTheScriptureGrid() {
        val activity = host()
        val pages = CommonUtils.windowControl.activeWindowPageManager
        pages.currentBible.startKeyChooser(activity)
        assertLaunched(activity, NavRoutes.gridChoosePassage(isScripture = true))
        pages.currentCommentary.startKeyChooser(activity)
        assertLaunched(activity, NavRoutes.gridChoosePassage(isScripture = true))
    }

    @Test
    fun theDictionaryAndMapChoosersOpenTheirRoutes() {
        val activity = host()
        val pages = CommonUtils.windowControl.activeWindowPageManager
        pages.currentDictionary.startKeyChooser(activity)
        assertLaunched(activity, NavRoutes.CHOOSE_DICTIONARY_WORD)
        pages.currentMap.startKeyChooser(activity)
        assertLaunched(activity, NavRoutes.CHOOSE_MAP_KEY)
    }
}
