package net.bible.android.view.compose

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec §3.4: the calculator follows rotation without being recreated, so the display and a PIN in progress survive. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CalculatorRotationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun info(): ActivityInfo {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        return app.packageManager.getActivityInfo(ComponentName(app, CalculatorComposeActivity::class.java), PackageManager.GET_META_DATA)
    }

    @Test fun theManifestNeitherLocksNorRecreatesOnRotation() {
        val i = info()
        assertEquals("no orientation lock (#3040)", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, i.screenOrientation)
        val needed = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SCREEN_LAYOUT or ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE
        assertEquals(needed, i.configChanges and needed)
        assertEquals("night mode still recreates", 0, i.configChanges and ActivityInfo.CONFIG_UI_MODE)
    }

    @Test fun rotatingKeepsTheSameActivityInstance() {
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        try {
            val before = controller.get()
            val land = Configuration(before.resources.configuration).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
            // Display "0" plus the "7" key; after the tap the display shows a second "7".
            compose.onNodeWithText("7").performClick()
            compose.waitForIdle()
            compose.onAllNodesWithText("7").assertCountEquals(2)
            controller.configurationChange(land)
            compose.waitForIdle()
            compose.onAllNodesWithText("7").assertCountEquals(2)
            assertSame("a recreation would drop the display value and the PIN in progress", before, controller.get())
        } finally {
            runCatching { controller.pause().stop().destroy() }
        }
    }
}
