package net.bible.sharedui.reading.activity

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import net.bible.android.control.activity.ActivityCenter
import net.bible.android.control.activity.ActivityListRequests
import net.bible.android.control.activity.ActivityMenuPreference
import net.bible.sharedcore.platform.ActivityMenuDefault
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme

/** A native notification click (#4012) asks the host to open the activity list through [ActivityListRequests]. */
@OptIn(ExperimentalTestApi::class)
class ActivityHostListRequestTest {
    private class MemorySettings : AppSettings {
        val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = values["String:$key"] as? String ?: default
        override fun getLong(key: String, default: Long) = values["Long:$key"] as? Long ?: default
        override fun getInt(key: String, default: Int) = values["Int:$key"] as? Int ?: default
        override fun getBoolean(key: String, default: Boolean) = values["Boolean:$key"] as? Boolean ?: default
        override fun getDouble(key: String, default: Double) = values["Double:$key"] as? Double ?: default
        override fun getFloat(key: String, default: Float) = values["Float:$key"] as? Float ?: default
        override fun setString(key: String, value: String?) { values["String:$key"] = value }
        override fun setLong(key: String, value: Long?) { values["Long:$key"] = value }
        override fun setInt(key: String, value: Int?) { values["Int:$key"] = value }
        override fun setBoolean(key: String, value: Boolean?) { values["Boolean:$key"] = value }
        override fun setDouble(key: String, value: Double?) { values["Double:$key"] = value }
        override fun setFloat(key: String, value: Float?) { values["Float:$key"] = value }
    }

    private val strings = iosStrings("en")

    private fun androidx.compose.ui.test.ComposeUiTest.host(enabled: Boolean, requests: ActivityListRequests) {
        val center = ActivityCenter(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) { 0L }
        val wiring = ActivityHostWiring(
            center, ActivityMenuPreference(MemorySettings(), ActivityMenuDefault { enabled }), onOpenItem = {},
            listRequests = requests.requests,
        )
        setContent {
            AbTheme(disableAnimations = true) {
                CompositionLocalProvider(LocalStrings provides strings) { ActivityHost(wiring) { Text("content") } }
            }
        }
    }

    @Test fun listRequestOpensListWhenEnabled() = runComposeUiTest {
        val requests = ActivityListRequests()
        host(enabled = true, requests = requests)
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(0)
        requests.request()
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun listRequestIgnoredWhenPreferenceOff() = runComposeUiTest {
        val requests = ActivityListRequests()
        host(enabled = false, requests = requests)
        waitForIdle()
        requests.request()
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(0)
    }
}
