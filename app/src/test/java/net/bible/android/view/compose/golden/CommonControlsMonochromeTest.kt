package net.bible.android.view.compose.golden

import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbCreateItemSheetContent
import net.bible.sharedui.components.AbTextInputContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Unlisted real-component scenes: selection and blank-name disabled state must pass both audits. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class CommonControlsMonochromeTest {
    @Test fun selectedText() = captureMatrix("CommonControls", "selectedText") {
        AbTextInputContent(initial = "Selected text", onValueChange = {})
    }

    @Test fun selectedPassword() = captureMatrix("CommonControls", "selectedPassword") {
        AbTextInputContent(initial = "secret", masked = true, onValueChange = {})
    }

    @Test fun blankNameDisablesConfirm() = captureMatrix("CommonControls", "blankName") {
        AbCreateItemSheetContent(
            title = "Create", initialName = "", confirmText = "OK", importText = "Import",
            onCreate = {}, onImport = {},
        )
    }
}
