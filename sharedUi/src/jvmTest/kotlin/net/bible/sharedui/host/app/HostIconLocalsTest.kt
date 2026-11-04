package net.bible.sharedui.host.app

import androidx.compose.foundation.Image
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedui.navigation.LocalCategoryIcon
import net.bible.sharedui.reading.LocalPinIcon
import net.bible.sharedui.reading.host.slots.LocalAgentStatusIcon
import kotlin.test.Test

/**
 * Every icon seam that defaults to `error(...)` must be provided by the iOS/desktop host, or the
 * screen reading it crashes there (the AI agent log did: "No LocalAgentStatusIcon provided").
 */
@OptIn(ExperimentalTestApi::class)
class HostIconLocalsTest {
    @Test
    fun hostProvidesEveryErrorDefaultedIconSeam() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(*hostIconLocals()) {
                Image(LocalCategoryIcon.current(DocCategory.BIBLE), null, Modifier.testTag("category"))
                Image(LocalPinIcon.current(), null, Modifier.testTag("pin"))
                Image(LocalAgentStatusIcon.current(), null, Modifier.testTag("agent"))
            }
        }
        onNodeWithTag("category").assertExists()
        onNodeWithTag("pin").assertExists()
        onNodeWithTag("agent").assertExists()
    }
}
