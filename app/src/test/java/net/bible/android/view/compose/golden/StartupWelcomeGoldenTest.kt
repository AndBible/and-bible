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

    // Primary = fresh install: Download + Import + Restore, no previous install, no easy start.
    @Test fun welcome_primary() {
        captureMatrix("StartupWelcome", "primary") { screen(state(tabs = false)) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun welcome_primary_rtl() {
        captureRtl("StartupWelcome", "primary") { screen(state(tabs = false)) }
    }

    @Test fun welcome_previousInstall() {
        captureGolden("StartupWelcome", "previousInstall", EDGE_MODE) {
            screen(state(tabs = false, prev = true))
        }
    }

    @Test fun welcome_easyStart() {
        captureGolden("StartupWelcome", "easyStart", EDGE_MODE) {
            screen(state())
        }
    }

    @Test fun welcome_progress() {
        captureGolden("StartupWelcome", "progress", EDGE_MODE) {
            screen(state(progress = "Installing document 1 of 3…"))
        }
    }
}
