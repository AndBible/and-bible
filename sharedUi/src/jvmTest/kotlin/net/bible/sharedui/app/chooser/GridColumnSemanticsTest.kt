package net.bible.sharedui.app.chooser

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import net.bible.android.view.activity.navigation.columnMajor
import net.bible.sharedcore.navigation.GridButton
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.GridUi
import net.bible.sharedui.navigation.GridChoosePassageContent
import net.bible.sharedui.theme.AbTheme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GridColumnSemanticsTest {
    @Test
    fun incompleteColumnKeepsPhysicalSlotsWithoutExposingPlaceholders() = runComposeUiTest {
        val chapters = (1..21).map { GridButton(it, it.toString(), colorGroup = 5) }
        val ui = GridUi(
            step = GridStep.CHAPTER,
            title = "John",
            columns = 5,
            showLongNames = false,
            showProgress = false,
            showDeutToggle = false,
            buttons = columnMajor(chapters, 5),
            minRows = 10,
        )
        var picked: Int? = null

        setContent {
            AbTheme(darkTheme = false, disableAnimations = true) {
                GridChoosePassageContent(ui, onPick = { picked = it }, modifier = Modifier.size(500.dp, 600.dp))
            }
        }
        waitForIdle()

        onAllNodes(hasClickAction()).assertCountEquals(21)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Focused)).assertCountEquals(21)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(21)

        val one = cell("1")
        val two = cell("2")
        val five = cell("5")
        val six = cell("6")
        val twentyOne = cell("21")
        assertEquals(one.center.x, two.center.x, 1f)
        assertEquals(one.center.x, five.center.x, 1f)
        val rowPitch = two.center.y - one.center.y
        assertTrue(rowPitch > 0f)
        assertEquals(one.center.y + 4 * rowPitch, five.center.y, 1f)
        val columnPitch = six.center.x - one.center.x
        assertTrue(columnPitch > 0f)
        assertEquals(one.center.y, six.center.y, 1f)
        assertEquals(one.center.y, twentyOne.center.y, 1f)
        // GridCells distributes leftover pixels across columns, so pitches can differ by a pixel.
        assertEquals(4, ((twentyOne.center.x - one.center.x) / columnPitch).roundToInt())

        onNodeWithText("21").performClick()
        assertEquals(21, picked)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.cell(label: String) =
        onNode(hasClickAction() and hasText(label)).fetchSemanticsNode().boundsInRoot

}
