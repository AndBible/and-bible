package net.bible.android.view.compose

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.LocalVolumeScrollRegistry
import net.bible.sharedui.components.VolumeScrollRegistry
import net.bible.sharedui.components.volumeScrollTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fix batch 5 F105: [VolumeScrollRegistry] pages the list a `volumeScrollTarget` registered. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class VolumeScrollRegistryTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var state: androidx.compose.foundation.lazy.LazyListState

    private fun mount(registry: VolumeScrollRegistry, showList: () -> Boolean = { true }) {
        compose.setContent {
            ProvideAppLocals {
                CompositionLocalProvider(LocalVolumeScrollRegistry provides registry) {
                    if (showList()) {
                        val s = rememberLazyListState()
                        state = s
                        LazyColumn(state = s, modifier = Modifier.height(300.dp).volumeScrollTarget(s)) {
                            items((0 until 100).toList()) { Text("row $it", modifier = Modifier.height(48.dp)) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun pagesDownThenBackUp() {
        val registry = VolumeScrollRegistry()
        mount(registry)
        assertTrue(registry.hasTarget)
        assertEquals(0, state.firstVisibleItemIndex)

        assertTrue(runBlocking { registry.scrollPage(true) })
        compose.waitForIdle()
        val afterDown = state.firstVisibleItemIndex
        assertTrue("page down must move the list, was $afterDown", afterDown > 0)

        assertTrue(runBlocking { registry.scrollPage(false) })
        compose.waitForIdle()
        assertTrue("page up must return towards 0", state.firstVisibleItemIndex < afterDown)
    }

    @Test
    fun withNothingRegisteredThereIsNothingToPage() {
        val registry = VolumeScrollRegistry()
        assertFalse(registry.hasTarget)
        assertFalse(runBlocking { registry.scrollPage(true) })
    }

    @Test
    fun leavingCompositionUnregisters() {
        val registry = VolumeScrollRegistry()
        var show by mutableStateOf(true)
        mount(registry) { show }
        assertTrue(registry.hasTarget)
        show = false
        compose.waitForIdle()
        assertFalse(registry.hasTarget)
    }
}
