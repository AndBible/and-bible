package net.bible.android.view.compose.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.abBottomFade
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bottom-fade primitive of round 14a's design §7.b.
 *
 * This is the ONE piece of sheet-internal chrome the port can actually golden: the fade needs no
 * popup to render, so unlike the `ModalBottomSheet` that hosts it, it does not hang Roborazzi.
 * These captures prove the fade and NOT the sheet around it.
 *
 * Three states, because each is a different way to get it wrong: content below the viewport (the
 * fade must show), scrolled to the very end (it must be gone, or it becomes a permanent smudge),
 * and a viewport shorter than the fade itself (it must clamp instead of overdrawing the whole box).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbBottomFadeGoldenTest {

    @Test fun bottomfade_scrollable() =
        captureMatrix("AbBottomFade", "scrollable", heightDp = 200) {
            FadeProbe(initialScroll = 0, viewportDp = 160)
        }

    @Test fun bottomfade_atEnd() =
        captureMatrix("AbBottomFade", "atEnd", heightDp = 200) {
            FadeProbe(initialScroll = 100_000, viewportDp = 160)
        }

    @Test fun bottomfade_shortViewport() =
        captureMatrix("AbBottomFade", "shortViewport", heightDp = 60) {
            FadeProbe(initialScroll = 0, viewportDp = 16)
        }
}

/**
 * A bounded scroll region of the shape every sheet page has: a fixed-height box whose content
 * overflows. The scroll state is created OUTSIDE the modifier and read INSIDE the draw phase
 * (`abBottomFade` takes a lambda), which is what keeps the fade correct on the very first frame --
 * a `Boolean` argument would be evaluated before the content had measured, and an unmeasured
 * `ScrollState` reports `canScrollForward == true` (its `maxValue` starts at `Int.MAX_VALUE`, not 0),
 * so the fade would wrongly APPEAR on the very first frame rather than being missing from it.
 */
@Composable
private fun FadeProbe(initialScroll: Int, viewportDp: Int) {
    val scroll = rememberScrollState(initial = initialScroll)
    Surface {
        Box(
            Modifier
                .fillMaxWidth()
                .height(viewportDp.dp)
                .abBottomFade(color = MaterialTheme.colorScheme.surface) { scroll.canScrollForward }
        ) {
            Column(Modifier.verticalScroll(scroll)) {
                repeat(12) { i ->
                    Text(
                        text = "Row ${i + 1}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
