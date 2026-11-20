package net.bible.android.view.widget

import net.bible.android.TEST_SDK
import net.bible.android.control.widget.VerseBible
import net.bible.android.database.IdType
import net.bible.android.view.compose.golden.captureMatrix
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden of the Verse widget's configuration dialog in all four modes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class VerseWidgetConfigGoldenTest {
    @Test fun configScreen_matrix() = captureMatrix("VerseWidgetConfig", "default") {
        VerseWidgetConfigScreen(
                options = VerseConfigOptions(
                    labels = listOf(IdType() to "Memory verses", IdType() to "Comfort"),
                    bibles = listOf(
                        VerseBible("KJV", "KJV", "KJV"),
                        VerseBible("FinRK", "FinRK", "German"),
                    ),
                    defaultBible = VerseBible("KJV", "KJV", "KJV"),
                    hasReadingPlan = true,
                ),
                existing = null, newSeed = 1L, onSave = {}, onCancel = {},
            )
    }
}
