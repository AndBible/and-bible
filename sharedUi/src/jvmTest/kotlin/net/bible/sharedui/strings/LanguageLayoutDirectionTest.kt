package net.bible.sharedui.strings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.getBoundsInRoot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LanguageLayoutDirectionTest {
    @Test
    fun rtlTagsMapToRtl() {
        for (tag in listOf("he", "ar", "fa", "ur", "he-IL", "ar_EG", "iw", "HE")) {
            assertEquals(LayoutDirection.Rtl, layoutDirectionForLanguage(tag), tag)
        }
        for (tag in listOf("en", "fi", "pt-BR", "zh-Hans", "sr-Latn", "")) {
            assertEquals(LayoutDirection.Ltr, layoutDirectionForLanguage(tag), tag)
        }
    }

    @Test
    fun directionFollowsEachSwitch() = runComposeUiTest {
        var tag by mutableStateOf("en")
        val seen = mutableListOf<LayoutDirection>()
        setContent {
            ProvideLanguageLayoutDirection(tag) {
                val dir = LocalLayoutDirection.current
                if (seen.lastOrNull() != dir) seen += dir
                Row {
                    Box(Modifier.size(20.dp).testTag("a"))
                    Box(Modifier.size(20.dp).testTag("b"))
                }
            }
        }
        waitForIdle()
        assertTrue(onNodeWithTag("a").getBoundsInRoot().left < onNodeWithTag("b").getBoundsInRoot().left)
        tag = "he"
        waitForIdle()
        assertTrue(onNodeWithTag("a").getBoundsInRoot().left > onNodeWithTag("b").getBoundsInRoot().left)
        tag = "en"
        waitForIdle()
        assertTrue(onNodeWithTag("a").getBoundsInRoot().left < onNodeWithTag("b").getBoundsInRoot().left)
        assertEquals(listOf(LayoutDirection.Ltr, LayoutDirection.Rtl, LayoutDirection.Ltr), seen)
    }
}
