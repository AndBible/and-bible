package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbMenuItem
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers the shared [AbMenuItem] seam (F49): the leading-icon slot, the reserved empty slot, the
 * single trailing-check selected style, and the disabled look.
 *
 * Captured inside a plain `Column`, NOT inside a real `DropdownMenu`: force-opening a Compose
 * `DropdownMenu` under Robolectric/Roborazzi has repeatedly, intermittently HUNG this repo's golden
 * capture, and two open popups on one page hang it reliably. Every menu golden here follows the
 * same rule (see `QuickDocMenuGoldenTest` and `ReadingOverflowMenuGoldenTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbMenuGoldenTest {

    @Test fun rows_matrix() = captureMatrix("AbMenu", "rows") {
        Column {
            AbMenuItem("Settings", onClick = {}, icon = { Icon(Icons.Filled.Settings, null) })
            AbMenuItem("Show notes", onClick = {}, icon = { Icon(Icons.Filled.Settings, null) }, checkable = true, checked = true)
            AbMenuItem("Show labels", onClick = {}, icon = { Icon(Icons.Filled.Settings, null) }, checkable = true, checked = false)
            AbMenuItem("No icon here", onClick = {}, reserveIconSlot = true)
            AbMenuItem("Delete", onClick = {}, icon = { Icon(Icons.Filled.Delete, null) }, enabled = false)
        }
    }

    @Test fun iconless_level_reserves_nothing() = captureGolden("AbMenu", "iconless", EDGE_MODE) {
        Column {
            AbMenuItem("First", onClick = {})
            AbMenuItem("Second", onClick = {})
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun rows_rtl() = captureRtl("AbMenu", "rows") {
        Column {
            AbMenuItem("Settings", onClick = {}, icon = { Icon(Icons.Filled.Settings, null) })
            AbMenuItem("Show notes", onClick = {}, icon = { Icon(Icons.Filled.Settings, null) }, checkable = true, checked = true)
        }
    }
}
