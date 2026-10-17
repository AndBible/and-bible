package net.bible.service.llm

import android.net.Uri
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class UriComponentsDifferentialTest {
    @Test fun defaultEncodingMatchesAndroidFramework() {
        val inputs = listOf("", "KJV", "Gen.1.1", "Matt 5:3-5", "a/b?c=d&e", "ÄÖ å", "中文", "emoji 😀", "\uD83D", "a\uDE00b", "\uFFFF", "100%", "'()*!~._-")
        for (input in inputs) assertEquals(input, Uri.encode(input), encodeUriComponent(input))
    }
}
