package net.bible.android.view.activity.bookmark

import android.graphics.Color
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Android-free colour packing that replaced `Color.argb` in the bookmark domain gives the same ints. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ArgbPackingTest {
    @Test fun packArgbMatchesColorArgb() {
        for ((r, g, b) in listOf(Triple(0, 0, 0), Triple(254, 254, 254), Triple(1, 128, 77), Triple(200, 3, 0))) {
            assertEquals("$r,$g,$b", Color.argb(255, r, g, b), packArgb(255, r, g, b))
        }
    }

    /** `BookmarkControl`'s new-label colour constant. */
    @Test fun newLabelColourConstantIsTheOldArgb() {
        assertEquals(Color.argb(255, 100, 100, 255), 0xFF6464FF.toInt())
    }
}
