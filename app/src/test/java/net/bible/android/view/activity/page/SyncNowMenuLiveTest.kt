package net.bible.android.view.activity.page

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.android.TEST_SDK
import net.bible.sharedui.reading.ReadingOverflowMenuRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = net.bible.android.TestBibleApplication::class)
class SyncNowMenuLiveTest {
    @get:Rule val rule = createComposeRule()

    @Test fun openAndroidMenuTracksSyncStartAndFinishAndStopsCollectingWhenClosed() {
        val running = MutableStateFlow(false)
        val open = mutableStateOf(true)
        val items = OptionsMenuStateBuilder.build({ true }, { running.value }, { false }) { _, _ -> SubMenuPreference() }
        rule.setContent {
            if (open.value) Column { ReadingOverflowMenuRows(items, {}, syncRunning = running) }
        }
        rule.onNodeWithText("Sync now").assertIsEnabled()
        rule.runOnIdle { running.value = true }
        rule.onNodeWithText("Sync now").assertIsNotEnabled()
        rule.runOnIdle { running.value = false }
        rule.onNodeWithText("Sync now").assertIsEnabled()
        rule.runOnIdle { open.value = false }
        rule.waitForIdle()
        assertEquals(0, running.subscriptionCount.value)
        rule.runOnIdle { running.value = true; open.value = true }
        rule.onNodeWithText("Sync now").assertIsNotEnabled()
        rule.runOnIdle { running.value = false }
        rule.onNodeWithText("Sync now").assertIsEnabled()
    }
}
