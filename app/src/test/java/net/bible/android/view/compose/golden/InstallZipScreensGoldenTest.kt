package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedui.installzip.InstallUiState
import net.bible.sharedui.installzip.InstallZipContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for [InstallZipContent] (Plan B Task B2) — every [InstallUiState] variant, each
 * captured across the full theme matrix (dark/light/BW/COLOR_EINK). Percent values are fixed
 * (never animated) so record == verify is stable, per the Batch 11 Task 4 golden trap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class InstallZipScreensGoldenTest {

    @Test fun progressAcquiring_matrix() =
        captureMatrix("InstallZipScreens", "progressAcquiring") {
            InstallZipContent(
                state = InstallUiState.Progress(
                    displayName = "MyDocument.zip",
                    statusText = "Checking given file…",
                    percent = 40,
                    indeterminate = false,
                ),
                onConfirm = {},
                onDismiss = {},
            )
        }

    @Test fun progressCommitting_matrix() =
        captureMatrix("InstallZipScreens", "progressCommitting") {
            InstallZipContent(
                state = InstallUiState.Progress(
                    displayName = "MyDocument.zip",
                    statusText = "Extracting Zip file now…",
                    percent = 80,
                    indeterminate = false,
                ),
                onConfirm = {},
                onDismiss = {},
            )
        }

    @Test fun progressIndeterminate_matrix() =
        captureMatrix("InstallZipScreens", "progressIndeterminate") {
            InstallZipContent(
                state = InstallUiState.Progress(
                    displayName = "MyDocument.zip",
                    statusText = "Checking given file…",
                    percent = null,
                    indeterminate = true,
                ),
                onConfirm = {},
                onDismiss = {},
            )
        }

    @Test fun confirmInstall_matrix() =
        captureMatrix("InstallZipScreens", "confirmInstall") {
            InstallZipContent(
                state = InstallUiState.ConfirmInstall(displayName = "MyDocument.zip"),
                onConfirm = {},
                onDismiss = {},
            )
        }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun confirmInstall_rtl() =
        captureRtl("InstallZipScreens", "confirmInstall") {
            InstallZipContent(
                state = InstallUiState.ConfirmInstall(displayName = "MyDocument.zip"),
                onConfirm = {},
                onDismiss = {},
            )
        }

    @Test fun formatInfo_matrix() =
        captureMatrix("InstallZipScreens", "formatInfo") {
            InstallZipContent(
                state = InstallUiState.FormatInfo(
                    formatsText = "Supported formats: AndBible zip, MyBible, MySword, e-Sword, EPUB, StudyPads, TTF, CSV prompts",
                ),
                onConfirm = {},
                onDismiss = {},
            )
        }

    @Test fun overwrite_matrix() =
        captureMatrix("InstallZipScreens", "overwrite") {
            InstallZipContent(
                state = InstallUiState.Overwrite(files = listOf("KJV/nt.z", "KJV/ot.z")),
                onConfirm = {},
                onDismiss = {},
            )
        }

    @Test fun studyPadImport_matrix() =
        captureMatrix("InstallZipScreens", "studyPadImport") {
            InstallZipContent(
                state = InstallUiState.StudyPadImport(
                    statsText = "3 StudyPads, 12 bookmarks, 4 labels",
                ),
                onConfirm = {},
                onDismiss = {},
            )
        }

    @Test fun epubUpgrade_matrix() =
        captureMatrix("InstallZipScreens", "epubUpgrade") {
            InstallZipContent(
                state = InstallUiState.EpubUpgrade,
                onConfirm = {},
                onDismiss = {},
            )
        }

    @Test fun error_matrix() =
        captureMatrix("InstallZipScreens", "error") {
            InstallZipContent(
                state = InstallUiState.Error(message = "Installation failed: invalid module."),
                onConfirm = {},
                onDismiss = {},
            )
        }
}
