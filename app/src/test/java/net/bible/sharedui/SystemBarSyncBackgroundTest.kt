package net.bible.sharedui

import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.graphics.Color
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `applySystemBarColor` painted the content root when `fillWindowBackground = true` and did nothing
 * when `false`, so a screen that does not want the fill inherited the previous screen's colour.
 * Host-inset-ownership spec, section 3.5.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SystemBarSyncBackgroundTest {
    @Test
    fun aFalseFillClearsAPreviousScreensBackground() {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)

        applySystemBarColor(activity, Color.Blue, fillWindowBackground = true)
        assertTrue("precondition: a true fill paints the root", root.background is ColorDrawable)

        applySystemBarColor(activity, Color.Red, fillWindowBackground = false)
        assertNull("a false fill must clear the root, not leave the previous colour", root.background)
    }
}
