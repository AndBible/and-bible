package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedui.settings.BackgroundImageChooserLabels
import net.bible.sharedui.settings.BackgroundImageChooserScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for [BackgroundImageChooserScreen]: the None/Import fixed tiles + one tile per installed
 * background-image module, the empty state, the delete-confirm dialog, and the import loading overlay.
 * `thumbnailFor = { null }` throughout — placeholders only, so the golden can't drift on a real bitmap
 * decode (spec §9, same reasoning as [net.bible.sharedui.components.AbLoadingOverlay]'s inspection frame).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BackgroundImageChooserScreenGoldenTest {

    private val opts = listOf(
        BackgroundImageOption("BGIMG_hills", "hills", "BGIMG_hills"),
        BackgroundImageOption("BGIMG_sky", "sky", "BGIMG_sky"),
    )

    private fun screen(
        options: List<BackgroundImageOption> = opts,
        loading: Boolean = false,
        deleteConfirm: BackgroundImageOption? = null,
    ): @Composable () -> Unit = {
        BackgroundImageChooserScreen(
            options, BackgroundImageChooserLabels.forTest(), loading, deleteConfirm,
            thumbnailFor = { null }, onUp = {}, onSelect = {}, onImport = {},
            onRequestDelete = {}, onConfirmDelete = {}, onDismissDelete = {},
        )
    }

    @Test fun populated_matrix() =
        captureMatrix("BackgroundImageChooser", "populated", heightDp = 1200, content = screen())

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun populated_rtl() =
        captureRtl("BackgroundImageChooser", "populated", heightDp = 1200, content = screen())

    @Test fun empty_edge() =
        captureGolden("BackgroundImageChooser", "empty", EDGE_MODE, heightDp = 1200, content = screen(options = emptyList()))

    @Test fun deleteconfirm_edge() =
        captureGolden("BackgroundImageChooser", "deleteconfirm", EDGE_MODE, heightDp = 1200, content = screen(deleteConfirm = opts.first()))

    @Test fun loading_edge() =
        captureGolden("BackgroundImageChooser", "loading", EDGE_MODE, heightDp = 1200, content = screen(loading = true))
}
