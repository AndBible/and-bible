@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlin.native.runtime.NativeRuntimeApi::class)

package net.bible.sharedui.ios.diagnostics

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import net.bible.sharedui.host.diagnostics.DiagnosticSource
import net.bible.sharedui.host.diagnostics.DiagnosticState
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticEvent
import platform.Foundation.NSNotificationCenter
import platform.darwin.NSObject
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIViewController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosLifecycleDiagnosticsTest {
    private fun fixture(window: Any, scene: Any) = HostSnapshot(window, scene, DiagnosticState.BACKGROUND, DiagnosticState.BACKGROUND, false, true)

    @Test
    fun hostSnapshotUsesOwningSceneNotFirstScene() {
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        val first = UIViewController()
        val second = UIViewController()
        val firstWindow = NSObject()
        val secondWindow = NSObject()
        val firstScene = NSObject()
        val secondScene = NSObject()
        val diagnostics = IosLifecycleDiagnostics(events::add) { host ->
            if (host === first) fixture(firstWindow, firstScene) else fixture(secondWindow, secondScene)
        }
        diagnostics.attachHost("first", first)
        diagnostics.attachHost("second", second)
        val observations = events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>()
        assertEquals(listOf("first", "second"), observations.map { it.hostId })
        assertNotEquals(observations[0].windowId, observations[1].windowId)
        assertNotEquals(observations[0].sceneId, observations[1].sceneId)
        diagnostics.composeState("second", Lifecycle.State.STARTED)
        assertEquals(observations[1].sceneId, events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>().last().sceneId)
        diagnostics.close()
    }

    @Test
    fun activationRequestDoesNotInventForegroundState() {
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        val window = NSObject()
        val scene = NSObject()
        val diagnostics = IosLifecycleDiagnostics(events::add) { fixture(window, scene) }
        val host = UIViewController()
        diagnostics.attachHost("host", host)
        diagnostics.install()
        NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationDidBecomeActiveNotification, null)
        val transition = events.filterIsInstance<RuntimeDiagnosticEvent.LifecycleObserved>().last()
        val observed = events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>().last()
        assertEquals(DiagnosticSource.APPLICATION, transition.source)
        assertEquals(DiagnosticState.ACTIVE, transition.state)
        assertEquals(DiagnosticState.BACKGROUND, observed.applicationState)
        assertEquals(DiagnosticState.BACKGROUND, observed.sceneState)
        diagnostics.close()
    }

    @Test
    fun installIsIdempotentAndDetachStopsHostTracking() {
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        val diagnostics = IosLifecycleDiagnostics(events::add)
        diagnostics.install()
        val count = diagnostics.observerCount
        diagnostics.install()
        assertEquals(10, count)
        assertEquals(count, diagnostics.observerCount)
        val host = UIViewController()
        diagnostics.attachHost("host", host)
        diagnostics.detachHost("host")
        assertNull(diagnostics.attachedHost("host"))
        val before = events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>().size
        NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationDidBecomeActiveNotification, null)
        assertEquals(before, events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>().size)
        diagnostics.close()
    }

    @Test
    fun detachedControllerIsNotRetained() {
        val diagnostics = IosLifecycleDiagnostics(emit = { })
        fun attachAndDetach(): kotlin.native.ref.WeakReference<UIViewController> {
            val controller = UIViewController()
            val weak = kotlin.native.ref.WeakReference(controller)
            diagnostics.attachHost("host", controller)
            diagnostics.detachHost("host")
            return weak
        }
        val weak = attachAndDetach()
        repeat(3) { kotlin.native.runtime.GC.collect() }
        assertNull(weak.get())
        diagnostics.close()
    }

    @Test
    fun composeTransitionUsesActualHostLifecycle() {
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        val diagnostics = IosLifecycleDiagnostics(events::add)
        val host = UIViewController()
        diagnostics.attachHost("host", host)
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
        val stopObserving = diagnostics.observeLifecycle("host", owner)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        assertTrue(events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>().any { it.composeState == DiagnosticState.STARTED })
        stopObserving()
        val size = events.size
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        assertEquals(size, events.size)
        diagnostics.close()
    }
}
