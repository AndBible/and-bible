package net.bible.service.resources

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import net.bible.android.activity.R
import net.bible.sharedui.strings.AndroidStrings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class EnglishWordingTest {
    @Test
    fun aiSetupDoesNotRestrictConnectionsToOpenAiCompatibleProviders() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        })
        val description = AndroidStrings(context).aiSetupDescription
        assertFalse("Setup must not imply only OpenAI-compatible providers are supported: $description",
            description.contains("an OpenAI-compatible API provider"))
        for (provider in listOf("OpenAI", "Anthropic", "Gemini")) {
            assertTrue("Setup must include $provider as a supported example", description.contains(provider))
        }
    }

    @Test
    fun englishMainLabelSwitchDescribesAutomaticAssignment() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        })
        val label = AndroidStrings(context).autoAssignPrimaryLabelSwitchLabel
        assertTrue("Switch must describe future automatic assignment: $label", label.contains("Auto-assign"))
        assertTrue("Switch must identify the main label: $label", label.contains("main label"))
    }

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
