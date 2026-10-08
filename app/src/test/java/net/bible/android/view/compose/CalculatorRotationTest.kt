package net.bible.android.view.compose

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec §3.4: the calculator follows rotation without being recreated, so the display and a PIN in progress survive. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CalculatorRotationTest {
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
            controller.configurationChange(land)
            assertSame("a recreation would drop the display value and the PIN in progress", before, controller.get())
        } finally {
            runCatching { controller.pause().stop().destroy() }
        }
    }
}
