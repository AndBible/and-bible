package net.bible.android.view.compose.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.AbLoadingOverlay
import net.bible.sharedui.components.AbSettingsRow
import net.bible.sharedui.components.AbSwitchRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class FinalMonochromeStatesTest {
    @Test fun disabledChipBorderIsSolidGrey() {
        auditMono("FinalStates", "disabledChip", 100, emptyList()) {
            androidx.compose.material3.FilterChip(selected = false, enabled = false, onClick = {}, label = { Text("Disabled chip") }, colors = net.bible.sharedui.components.abFilterChipColors(), border = net.bible.sharedui.components.abFilterChipBorder(false, false))
        }
        for (mode in MONO_MODES) {
            val image = javax.imageio.ImageIO.read(java.io.File("build/mono-audit/FinalStates_disabledChip_${mode.tag}.png"))
            org.junit.Assert.assertTrue("Disabled border needs a solid 808080 stroke", (0 until image.height).any { y ->
                (0 until image.width - 30).any { x -> (x until x + 30).all { (image.getRGB(it, y) and 0xffffff) == 0x808080 } }
            })
        }
    }
    @Test fun disabledSettings() = auditMono("FinalStates", "disabledSettings", 300, emptyList()) {
        Column {
            AbSettingsRow("Credentials", "Signed in", false, {})
            AbSwitchRow("Dependent off", false, {}, enabled = false)
            AbSwitchRow("Dependent on", true, {}, enabled = false)
        }
    }
    @Test fun loadingDoesNotDimPaper() {
        auditMono("FinalStates", "loading", 300, emptyList()) {
            Box(Modifier.size(320.dp, 300.dp).background(MaterialTheme.colorScheme.surface)) {
                Text("Underlying content")
                AbLoadingOverlay()
            }
        }
        for (mode in MONO_MODES) {
            val image = javax.imageio.ImageIO.read(java.io.File("build/mono-audit/FinalStates_loading_${mode.tag}.png"))
            val ink = if (mode.dark) 0xffffff else 0
            org.junit.Assert.assertTrue("Loading indicator must actually paint ink at the center, ${mode.tag}",
                (130 until 170).any { y -> (140 until 180).any { x -> (image.getRGB(x, y) and 0xffffff) == ink } })
        }
    }
}
