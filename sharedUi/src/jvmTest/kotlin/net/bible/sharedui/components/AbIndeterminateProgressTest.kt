package net.bible.sharedui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedui.theme.LocalDisableAnimations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * osTicket 3499: with animations off a duration scale of 0 freezes an `InfiniteTransition`, so an indeterminate
 * indicator must be replaced by a visible static stand-in.
 */
@OptIn(ExperimentalTestApi::class)
class AbIndeterminateProgressTest {
    private fun rangeInfo(tag: String, disable: Boolean, circular: Boolean): ProgressBarRangeInfo {
        var info: ProgressBarRangeInfo? = null
        runComposeUiTest {
            mainClock.autoAdvance = false // a running indeterminate indicator never goes idle
            setContent {
                AbTheme(darkTheme = false) {
                    CompositionLocalProvider(LocalDisableAnimations provides disable) {
                        val m = Modifier.testTag(tag)
                        if (circular) AbIndeterminateCircularProgress(m) else AbLoadingIndicator(m)
                    }
                }
            }
            info = onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        }
        return info!!
    }

    @Test fun circularIsIndeterminateWhileAnimationsAreOn() {
        assertEquals(ProgressBarRangeInfo.Indeterminate, rangeInfo("c", disable = false, circular = true))
    }

    @Test fun circularIsAStaticHalfArcWhileAnimationsAreOff() {
        val info = rangeInfo("c", disable = true, circular = true)
        assertEquals(0.5f, info.current)
    }

    @Test fun linearIsIndeterminateWhileAnimationsAreOn() {
        assertEquals(ProgressBarRangeInfo.Indeterminate, rangeInfo("l", disable = false, circular = false))
    }

    @Test fun linearIsAStaticPartialBarWhileAnimationsAreOff() {
        val info = rangeInfo("l", disable = true, circular = false)
        assertTrue(info.current > 0f && info.current < 1f, "visible fill, was ${info.current}")
    }
}
