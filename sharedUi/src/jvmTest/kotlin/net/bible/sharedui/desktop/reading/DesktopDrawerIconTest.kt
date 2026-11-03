package net.bible.sharedui.desktop.reading

import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedui.host.reading.rememberIosDrawerIcon
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The host (desktop and iOS) drawer icon slot must supply the AndBible logo for the drawer header. */
@OptIn(ExperimentalTestApi::class)
class DesktopDrawerIconTest {
    @Test
    fun logoResolvesToAPainter() = runComposeUiTest {
        var logo: Painter? = null
        var unknown: Painter? = null
        var material: Painter? = null
        setContent {
            logo = rememberIosDrawerIcon("ic_logo")
            unknown = rememberIosDrawerIcon("no_such_icon")
            material = rememberIosDrawerIcon("ic_help_white_24dp")
        }
        waitForIdle()
        assertNotNull(logo, "drawer header logo")
        assertNotNull(material)
        assertNull(unknown)
    }
}
