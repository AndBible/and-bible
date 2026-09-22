package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import net.bible.android.activity.R
import net.bible.sharedcore.startup.StartupWelcomeState
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
        showRedownload: Boolean = false,
        showRestore: Boolean = true,
        showEasyStart: Boolean = false,
        progress: String? = null,
    ) = StartupWelcomeState(
        welcomeText = "Thank you for downloading AndBible. There are currently no Bibles or documents installed.",
        versionText = "Version: 5.1",
        supportedFormatsText = "Supported formats: AndBible zip, MyBible, MySword, EPUB",
        redownloadMessage = "A previous installation was detected. You can redownload your documents.",
        easyStartMessage = "New here? Get started quickly with a recommended set of documents.",
        showRedownload = showRedownload,
        showRestore = showRestore,
        showEasyStart = showEasyStart,
        progressText = progress,
    )

    @Composable
    private fun screen(s: StartupWelcomeState) {
        StartupWelcomeScreen(
            state = s,
            appName = "AndBible",
            logo = painterResource(R.drawable.ic_logo),
            onDownload = {}, onImport = {}, onRestore = {}, onRedownload = {},
            onEasyStart = {}, onOpenHomepage = {}, onOpenGithub = {},
        )
    }

    // Primary = fresh install: Download + Import + Restore, no previous install, no easy start.
    @Test fun welcome_primary() {
        captureMatrix("StartupWelcome", "primary") { screen(state()) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun welcome_primary_rtl() {
        captureRtl("StartupWelcome", "primary") { screen(state()) }
    }

    @Test fun welcome_previousInstall() {
        captureGolden("StartupWelcome", "previousInstall", EDGE_MODE) {
            screen(state(showRedownload = true, showRestore = false))
        }
    }

    @Test fun welcome_easyStart() {
        captureGolden("StartupWelcome", "easyStart", EDGE_MODE) {
            screen(state(showEasyStart = true))
        }
    }

    @Test fun welcome_progress() {
        captureGolden("StartupWelcome", "progress", EDGE_MODE) {
            screen(state(progress = "Installing document 1 of 3…"))
        }
    }
}
