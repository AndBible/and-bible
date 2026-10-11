package net.bible.service.sword

import android.text.TextUtils
import android.util.LayoutDirection
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [33])
class RtlLanguageTest {
    @Test
    fun languageDirectionMatchesFrameworkForEveryAvailableLocale() {
        val languages = Locale.getAvailableLocales().map { it.language }.toSet() +
            setOf("ar", "fa", "he", "iw", "ur", "ps", "sd", "ug", "yi", "ji", "dv", "ckb", "syr")
        val mismatches = languages.sorted().mapNotNull { language ->
            val expected = TextUtils.getLayoutDirectionFromLocale(Locale(language)) == LayoutDirection.RTL
            if (expected == isRtlLanguage(language)) null else "$language=$expected"
        }
        assertEquals("Framework direction mismatches", emptyList<String>(), mismatches)
    }
}
