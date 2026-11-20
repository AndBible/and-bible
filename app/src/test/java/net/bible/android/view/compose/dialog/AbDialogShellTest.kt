package net.bible.android.view.compose.dialog

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.dialog.AbDialogButton
import net.bible.sharedui.components.dialog.AbDialogShell
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Height safety and scroll dividers of [AbDialogShell]. Dialog content lives in its own window, so
 * "the root" of these tests is the dialog's own root node (the last root in the semantics trees).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbDialogShellTest {
    @get:Rule val compose = createComposeRule()

    private val longBody = (1..250).joinToString(" ") { "word$it" } // ~2000 chars
    private val ax5Lead = List(3) { "Delete the label “Sermon notes from the 2026 autumn conference”?" }.joinToString(" ")

    private fun show(
        fontScale: Float = 1f,
        onDismiss: () -> Unit = {},
        lead: String = "Lead",
        body: String? = null,
    ) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
            AbTheme {
                AbDialogShell(
                    onDismissRequest = onDismiss,
                    buttons = {
                        AbDialogButton("Cancel", DialogButtonRole.Cancel, {})
                        AbDialogButton("Delete", DialogButtonRole.Destructive, {})
                    },
                    lead = { Text(lead) },
                    content = body?.let { b -> { Text(b, modifier = androidx.compose.ui.Modifier) } },
                )
            }
        }
    }

    /** The dialog window's root node: the semantics roots are the activity's and then the dialog's. */
    private fun dialogRoot(): SemanticsNodeInteraction =
        compose.onAllNodes(isRoot()).let { all ->
            val n = all.fetchSemanticsNodes().size
            all[n - 1]
        }

    private fun SemanticsNodeInteraction.rootBounds(): Rect = fetchSemanticsNode().boundsInRoot

    @Test fun buttonsStayVisibleAtAx5() {
        show(fontScale = 3f, lead = ax5Lead, body = longBody)
        compose.waitForIdle()
        compose.onNodeWithTag("dialog-confirm").assertIsDisplayed()
        compose.onNodeWithTag("dialog-dismiss").assertIsDisplayed()
        val root = dialogRoot().rootBounds()
        listOf("dialog-confirm", "dialog-dismiss").forEach { tag ->
            val b = compose.onNodeWithTag(tag).rootBounds()
            assertTrue("$tag $b outside dialog root $root", b.top >= root.top && b.bottom <= root.bottom && b.left >= root.left && b.right <= root.right)
        }
    }

    @Test fun leadIsCappedAt40PercentOfTheDialog() {
        show(lead = (1..30).joinToString("\n") { "Lead line $it" }, body = "First body line\nsecond")
        compose.waitForIdle()
        // The dialog shrinks to its content, so the cap is measured against the window the dialog may fill.
        val windowH = ApplicationProvider.getApplicationContext<android.content.Context>().resources.displayMetrics.heightPixels.toFloat()
        val rootH = windowH
        val leadH = compose.onNodeWithTag("dialog-lead").rootBounds().height
        val oneDp = compose.density.density
        assertTrue("lead $leadH > 0.4 * $rootH + 1dp", leadH <= 0.4f * rootH + oneDp)
        compose.onNodeWithText("First body line", substring = true).assertIsDisplayed()
    }

    @Test fun dividersOnlyWhenScrollable() {
        show(body = "Short body")
        compose.waitForIdle()
        compose.onAllNodesWithTag("dialog-divider-top").assertCountEquals(0)
        compose.onAllNodesWithTag("dialog-divider-bottom").assertCountEquals(0)
    }

    @Test fun dividersFollowTheScrollPosition() {
        show(body = longBody)
        compose.waitForIdle()
        compose.onAllNodesWithTag("dialog-divider-top").assertCountEquals(0)
        compose.onAllNodesWithTag("dialog-divider-bottom").assertCountEquals(1)
        compose.onNodeWithTag("dialog-middle").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onAllNodesWithTag("dialog-divider-top").assertCountEquals(1)
    }

    @Test fun scrimDismissCallsOnDismissRequest() {
        var dismissed = 0
        show(onDismiss = { dismissed++ })
        compose.waitForIdle()
        // Compose's DialogWrapper turns android.app.Dialog.onTouchEvent (a touch outside the window) into onDismissRequest;
        // the semantics test injector cannot reach outside a window, so deliver that touch to the Dialog itself.
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        val down = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, -200f, -200f, 0)
        val up = android.view.MotionEvent.obtain(0, 10, android.view.MotionEvent.ACTION_UP, -200f, -200f, 0)
        dialog.onTouchEvent(down)
        dialog.onTouchEvent(up)
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }
}
