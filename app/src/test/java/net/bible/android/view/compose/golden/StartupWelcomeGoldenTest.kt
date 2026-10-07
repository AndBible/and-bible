package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import net.bible.android.activity.R
import net.bible.sharedcore.startup.StartupWelcomeState
import net.bible.sharedcore.startup.StartupWelcomeTab
import net.bible.sharedui.startup.StartupWelcomeScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class StartupWelcomeGoldenTest {
    // The default viewport clips the Advanced list, the version line and the Homepage/GitHub row.
    private companion object { const val HEIGHT_DP = 900 }

    private fun state(
        tabs: Boolean = true,
        tab: StartupWelcomeTab = StartupWelcomeTab.EASY,
        prev: Boolean = false,
        progress: String? = null,
    ) = StartupWelcomeState(
        versionText = "Version: 5.1",
        supportedFormatsText = "Supported formats: AndBible zip, MyBible, MySword, EPUB",
        showTabs = tabs,
        selectedTab = if (tabs) tab else StartupWelcomeTab.ADVANCED,
        showRedownload = prev,
        showRedownloadHint = tabs && prev,
        progressText = progress,
    )

    @Composable
    private fun screen(s: StartupWelcomeState) {
        StartupWelcomeScreen(
            state = s,
            appName = "AndBible",
            logo = painterResource(R.drawable.ic_logo),
            onSelectTab = {},
            onDownload = {}, onImport = {}, onRestore = {}, onRedownload = {},
            onEasyStart = {}, onOpenHomepage = {}, onOpenGithub = {},
        )
    }

    // Easy (English, fresh install) across light / dark / bw / e-ink.
    @Test fun welcome_easy() {
        captureMatrix("StartupWelcome", "easy", heightDp = HEIGHT_DP) { screen(state()) }
    }

    @Test fun welcome_easyPreviousInstall() {
        captureGolden("StartupWelcome", "easyPreviousInstall", EDGE_MODE, heightDp = HEIGHT_DP) { screen(state(prev = true)) }
    }

    @Test fun welcome_advancedPreviousInstall() {
        captureGolden("StartupWelcome", "advancedPreviousInstall", EDGE_MODE, heightDp = HEIGHT_DP) {
            screen(state(tab = StartupWelcomeTab.ADVANCED, prev = true))
        }
    }

    // Non-English: no tabs, the Advanced list alone (the old "primary").
    @Test fun welcome_primary() {
        captureMatrix("StartupWelcome", "primary", heightDp = HEIGHT_DP) { screen(state(tabs = false)) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun welcome_primary_rtl() {
        captureRtl("StartupWelcome", "primary", heightDp = HEIGHT_DP) { screen(state(tabs = false, prev = true)) }
    }

    @Test fun welcome_progress() {
        captureGolden("StartupWelcome", "progress", EDGE_MODE, heightDp = HEIGHT_DP) {
            screen(state(tab = StartupWelcomeTab.ADVANCED, progress = "Installing document 1 of 3…"))
        }
    }
}
