/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * Licensed under the GNU General Public License, version 3 or later.
 */
package net.bible.sharedui.reading

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.speak.SpeakTransportVd
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #4199: the speak bar's close "X" is always shown, disabled while speech plays; an enabled tap reaches `onClose`. */
@OptIn(ExperimentalTestApi::class)
class SpeakTransportBarCloseTest {
    private val strings = iosStrings("en")
    private val stopped = SpeakTransportVd(visible = true, stopped = true)
    private val paused = SpeakTransportVd(visible = true, paused = true, stopped = false)
    private val playing = SpeakTransportVd(visible = true, playing = true, stopped = false)

    @Test fun closeIsEnabledWhenStoppedOrPausedButNotWhilePlaying() {
        assertTrue(speakCloseEnabled(stopped))
        assertTrue(speakCloseEnabled(paused))
        assertFalse(speakCloseEnabled(playing))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: SpeakTransportVd, onClose: () -> Unit) = setContent {
        CompositionLocalProvider(LocalStrings provides strings) {
            AbTheme(darkTheme = false) {
                SpeakTransportBar(state, {}, {}, {}, {}, {}, {}, {}, {}, onClose = onClose)
            }
        }
    }

    @Test fun tappingTheCloseButtonInvokesOnCloseOnceWhenStopped() = runComposeUiTest {
        var closed = 0
        show(stopped) { closed++ }
        onNodeWithContentDescription(strings.speakCloseBar).performClick()
        assertEquals(1, closed)
    }

    @Test fun closeButtonIsShownButDisabledWhilePlayingAndIgnoresTaps() = runComposeUiTest {
        var closed = 0
        show(playing) { closed++ }
        onNodeWithContentDescription(strings.speakCloseBar).assertIsNotEnabled().performClick()
        assertEquals(0, closed)
    }

    @Test fun closeButtonIsEnabledWhenPaused() = runComposeUiTest {
        show(paused) {}
        onNodeWithContentDescription(strings.speakCloseBar).assertIsEnabled()
    }
}
