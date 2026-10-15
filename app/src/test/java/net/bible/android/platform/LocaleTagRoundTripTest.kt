package net.bible.android.platform

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.platform.speak.SpeakEngine
import net.bible.service.device.speak.ChangeLanguageCommand
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LocaleTagRoundTripTest {
    private val locales = listOf(
        Locale("iw"), Locale("he"), Locale("grc"), Locale("en", "GB"),
        Locale.forLanguageTag("zh-Hant-TW"), Locale("fi"), Locale.ENGLISH, Locale.getDefault(),
    )

    @Test fun languageTagsPreserveTtsLanguageAndCountry() {
        for (locale in locales) {
            val back = Locale.forLanguageTag(locale.toLanguageTag())
            assertEquals("ISO3 language for $locale", locale.isO3Language, back.isO3Language)
            assertEquals("Country for $locale", locale.country, back.country)
        }
    }

    @Test fun changeLanguageCommandPassesTheCompleteLanguageTag() {
        for (locale in locales) {
            val engine = mock(SpeakEngine::class.java)
            ChangeLanguageCommand(locale).speak(engine, "language-change")
            verify(engine).setLanguage(locale.toLanguageTag())
        }
    }
}
