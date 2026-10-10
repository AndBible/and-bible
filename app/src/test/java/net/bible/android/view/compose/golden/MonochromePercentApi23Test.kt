package net.bible.android.view.compose.golden

import androidx.test.core.app.ApplicationProvider
import net.bible.sharedui.strings.AndroidStrings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Formatting used while composing mono book cells must work at the actual minimum SDK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23], application = android.app.Application::class, qualifiers = "fi")
class MonochromePercentApi23Test {
    @Test fun minimumSdkUsesConfiguredLocaleWithoutLosingFractionalProgress() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("12,5%", AndroidStrings(context).monochromePercent(12.5f))
        assertEquals("0,125%", AndroidStrings(context).monochromePercent(0.125f))
    }
}
