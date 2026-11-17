package net.bible.service.resources

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import net.bible.android.activity.R
import net.bible.sharedui.strings.AndroidStrings
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class EnglishWordingTest {
    @Test
    fun downloadWarningNamesTheFileImportMenuItem() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        })
        val menuLabel = context.getString(R.string.install_zip)
        val warning = context.getString(R.string.download_question_message, menuLabel)
        assertTrue("Warning must name the actual file import action: $warning", warning.contains(menuLabel))
        val strings = AndroidStrings(context)
        assertTrue(strings.downloadQuestionMessage("Renamed import action").contains("Renamed import action"))
    }
}
