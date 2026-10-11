package net.bible.service.format

import androidx.test.core.app.ApplicationProvider
import net.bible.android.BibleApplication
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.platform.AndroidSpeakPlatform
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [33])
class AndroidSpeakPhrasesTest {
    @Test fun eachRequestReadsDivineNamesForItsLanguageFromResources() {
        val platform = AndroidSpeakPlatform(ApplicationProvider.getApplicationContext())
        for (language in listOf("fi", "en", "fi")) {
            val resources = BibleApplication.application.getLocalizedResources(language)
            val phrases = platform.phrasesFor(language)
            assertEquals(resources.getStringArray(R.array.speak_divinename_original).toList(), phrases.divineNameOriginal)
            assertEquals(resources.getStringArray(R.array.speak_divinename_replace).toList(), phrases.divineNameReplace)
            assertEquals(resources.getString(R.string.speak_chapter_changed), phrases.chapterChanged)
        }
    }
}
