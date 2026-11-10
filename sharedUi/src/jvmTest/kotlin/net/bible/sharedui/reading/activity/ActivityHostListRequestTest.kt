package net.bible.sharedui.reading.activity

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.flow
import kotlin.test.assertEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.sharedui.MemoryAppSettings
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
    private val strings = iosStrings("en")

    private fun androidx.compose.ui.test.ComposeUiTest.host(enabled: Boolean, requests: ActivityListRequests, show: Boolean = true) {
        val center = ActivityCenter(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) { 0L }
        val wiring = ActivityHostWiring(
            center, ActivityMenuPreference(MemoryAppSettings(), ActivityMenuDefault { enabled }), onOpenItem = {},
            listRequests = requests.requests,
        )
        setContent {
            AbTheme(disableAnimations = true) {
                CompositionLocalProvider(LocalStrings provides strings) { ActivityHost(wiring) { Text("content") } }
            }
        }
    }

    @Test fun rootOwnsOneCollectorAcrossReadingRouteChangesAndToolbarUsesSameList() = runComposeUiTest {
        val requests = ActivityListRequests()
        val preference = ActivityMenuPreference(MemoryAppSettings(), ActivityMenuDefault { true })
        val center = ActivityCenter(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) { 0L }
        val reading = mutableStateOf(true)
        val rootPresent = mutableStateOf(true)
        var starts = 0
        var stops = 0
        val tracked = flow {
            starts++
            try { requests.requests.collect { emit(it) } } finally { stops++ }
        }
        val wiring = ActivityHostWiring(center, preference, listRequests = tracked, onOpenItem = {})
        setContent {
            AbTheme(disableAnimations = true) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    if (rootPresent.value) ActivityRootHost(wiring) {
                        if (reading.value) ActivityHost(wiring) { menu ->
                            TextButton(onClick = { menu?.onShowAll?.invoke() }) { Text("Open activity") }
                        } else Text("non-reading")
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(1, starts, "reading must not create a second notification collector")
        requests.request()
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(1)
        onNodeWithText("Close").performClick()
        runOnIdle { reading.value = false }
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(1, starts)
        assertEquals(0, stops, "the root collector outlives the reading route")
        runOnIdle { reading.value = true }
        waitForIdle()
        assertEquals(1, starts)
        onNodeWithText("Open activity").performClick()
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(1)
        onNodeWithText("Close").performClick()
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(0)
        runOnIdle { rootPresent.value = false }
        waitForIdle()
        assertEquals(1, stops, "disposing the app root cancels its one collector")
    }

    @Test fun disabledRootDoesNotInstallAReadingFallbackCollector() = runComposeUiTest {
        val requests = ActivityListRequests()
        val preference = ActivityMenuPreference(MemoryAppSettings(), ActivityMenuDefault { false })
        val center = ActivityCenter(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) { 0L }
        var collectors = 0
        val tracked = flow { collectors++; requests.requests.collect { emit(it) } }
        val wiring = ActivityHostWiring(center, preference, listRequests = tracked, onOpenItem = {})
        setContent {
            AbTheme(disableAnimations = true) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    ActivityRootHost(wiring) { ActivityHost(wiring) { Text("reading") } }
                }
            }
        }
        waitForIdle()
        assertEquals(1, collectors)
        runOnIdle { requests.request() }
        waitForIdle()
        runOnIdle { preference.set(true) }
        waitForIdle()
        assertEquals(1, collectors)
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(0)
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

    @Test fun requestBeforeHostIsComposedOpensOnceAndIsNotReplayed() = runComposeUiTest {
        val requests = ActivityListRequests()
        requests.request() // e.g. a notification click while Settings is open: the host is not composed
        host(enabled = true, requests = requests)
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(1)

        // The request was consumed: a later collector (a second composition of the host) gets nothing.
        val replayed = runBlocking { withTimeoutOrNull(300) { requests.requests.first() } }
        assertNull(replayed, "a consumed request is not replayed")
    }
}
