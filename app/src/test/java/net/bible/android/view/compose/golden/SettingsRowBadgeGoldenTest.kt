package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.settings.AbSettingsContent
import net.bible.sharedui.settings.LocalSettingsRowBadge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden for the [LocalSettingsRowBadge] inheritance-badge seam (Batch 12d-A Task 3): rows "a"/"b"
 * have a badge lookup hit ("Workspace"/"Global") and render a small trailing chip; row "c" has none
 * and renders exactly like the pre-badge-seam [AbSettingsContent] output.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SettingsRowBadgeGoldenTest {

    private fun state() = SettingsScreenState("Badges", listOf(
        SettingsItem.SwitchRow(key = "a", title = "Own value", checked = true),
        SettingsItem.ListChoiceRow(key = "b", title = "Inherited", entries = listOf(SettingsItem.Choice("x", "X")), selectedValue = "x"),
        SettingsItem.NavigationRow(key = "c", title = "No badge"),
    ))

    private fun content(): @Composable () -> Unit = {
        CompositionLocalProvider(LocalSettingsRowBadge provides { key ->
            when (key) { "a" -> "Workspace"; "b" -> "Global"; else -> null }
        }) {
            AbSettingsContent(
                state = state(),
                onSwitch = { _, _ -> }, onListChoice = { _, _ -> }, onTextInput = { _, _ -> }, onNavigate = {},
                onOpenEditor = {},
            )
        }
    }

    @Test fun badges_matrix() = captureMatrix("SettingsRowBadge", "baseline", content = content())
}
