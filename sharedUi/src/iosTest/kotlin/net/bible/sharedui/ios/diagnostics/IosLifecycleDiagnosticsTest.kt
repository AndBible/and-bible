@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlin.native.runtime.NativeRuntimeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package net.bible.sharedui.ios.diagnostics

import androidx.lifecycle.Lifecycle
import kotlinx.cinterop.objcPtr
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import net.bible.sharedui.host.diagnostics.DiagnosticSource
import net.bible.sharedui.host.diagnostics.DiagnosticState
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticEvent
import platform.Foundation.NSNotificationCenter
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.darwin.NSObject
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.CoreGraphics.CGRectMake
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** UIKit calls windowScene during UIWindow dealloc, after its Kotlin peer has gone away.
 * These overrides must never access receiver state, even after fixture disposal. */
internal object DiagnosticOwnerWindows {
    private var firstScene: UIWindowScene? = null
    private var secondScene: UIWindowScene? = null
    private var inUse = false

    private class FirstWindow : UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0)) {
        override fun windowScene(): UIWindowScene? = firstScene
    }
    private class SecondWindow : UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 480.0)) {
        override fun windowScene(): UIWindowScene? = secondScene
    }

    fun create(first: UIWindowScene, second: UIWindowScene): Pair<UIWindow, UIWindow> {
        check(platform.Foundation.NSThread.isMainThread)
        check(!inUse)
        inUse = true
        firstScene = first
        secondScene = second
        return try {
            FirstWindow() to SecondWindow()
        } catch (failure: Throwable) {
            firstScene = null
            secondScene = null
            inUse = false
            throw failure
        }
    }

    fun dispose(vararg windows: UIWindow) {
        check(platform.Foundation.NSThread.isMainThread)
        firstScene = null
        secondScene = null
        try {
            windows.forEach { window ->
                window.subviews.forEach { (it as platform.UIKit.UIView).removeFromSuperview() }
                window.rootViewController = null
            }
        } finally {
            inUse = false
        }
    }
}

class IosLifecycleDiagnosticsTest {
    private fun fixture(window: Any, scene: Any) = HostSnapshot(window, scene, DiagnosticState.BACKGROUND, DiagnosticState.BACKGROUND, false, true)

    @Test
    fun nativeOwnerKeepsDiagnosticIdAcrossWeakReads() {
        val owner = NSObject()
        val weak = kotlin.native.ref.WeakReference(owner)
        val recovered = weak.get()!!
        assertEquals(owner.objcPtr(), recovered.objcPtr())
        assertEquals(NativeDiagnosticIdentities.identity(owner), NativeDiagnosticIdentities.identity(recovered))
        assertNotEquals(NativeDiagnosticIdentities.identity(owner), NativeDiagnosticIdentities.identity(NSObject()))
        assertEquals("none", NativeDiagnosticIdentities.identity(null))
    }

    @Test
    fun uiKitAdapterUsesWindowOwningScene() {
        val firstScene = UIWindowScene()
        val secondScene = UIWindowScene()
        val (firstWindow, secondWindow) = DiagnosticOwnerWindows.create(firstScene, secondScene)
        val first = UIViewController()
        val second = UIViewController()
        try {
            firstWindow.rootViewController = first
            secondWindow.rootViewController = second
            firstWindow.addSubview(first.view)
            secondWindow.addSubview(second.view)
            val firstSnapshot = snapshotHost(first, UIKitHostOwnership)
            val secondSnapshot = snapshotHost(second, UIKitHostOwnership)
            assertEquals(firstWindow.objcPtr(), firstSnapshot.window?.objcPtr())
            assertEquals(secondWindow.objcPtr(), secondSnapshot.window?.objcPtr())
            assertEquals(firstScene.objcPtr(), firstSnapshot.scene?.objcPtr())
            assertEquals(secondScene.objcPtr(), secondSnapshot.scene?.objcPtr())
        } finally {
            DiagnosticOwnerWindows.dispose(firstWindow, secondWindow)
        }
    }

    @Test
    fun disposedUIKitOwnershipFixturesSurviveNativeFinalization() {
        kotlinx.cinterop.autoreleasepool {
            uiKitAdapterUsesWindowOwningScene()
        }
        // A later test that pumps the main loop must not inherit unsafe UIKit teardown.
        repeat(3) {
            kotlinx.cinterop.autoreleasepool {
                kotlin.native.runtime.GC.collect()
                platform.Foundation.NSRunLoop.mainRunLoop.runUntilDate(
                    platform.Foundation.NSDate.dateWithTimeIntervalSinceNow(0.02)
                )
            }
        }
    }

    @Test
    fun hostSnapshotUsesOwningSceneNotFirstScene() {
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        val first = UIViewController()
        val second = UIViewController()
        val firstWindow = NSObject()
        val secondWindow = NSObject()
        val firstScene = NSObject()
        val secondScene = NSObject()
        val access = object : HostOwnershipAccess {
            override fun window(controller: UIViewController): Any? = if (controller.objcPtr() == first.objcPtr()) firstWindow else secondWindow
            override fun scene(window: Any): Any? = if (window.objcPtr() == firstWindow.objcPtr()) firstScene else secondScene
        }
        val diagnostics = IosLifecycleDiagnostics(events::add) { host -> snapshotHost(host, access) }
        assertEquals(firstScene.objcPtr(), snapshotHost(first, access).scene?.objcPtr())
        assertEquals(secondScene.objcPtr(), snapshotHost(second, access).scene?.objcPtr())
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
    fun reattachingSameNativeHostPreservesComposeState() {
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        val diagnostics = IosLifecycleDiagnostics(events::add)
        val controller = UIViewController()
        try {
            diagnostics.attachHost("host", controller)
            diagnostics.composeState("host", Lifecycle.State.RESUMED)
            diagnostics.attachHost("host", kotlin.native.ref.WeakReference(controller).get()!!)
            assertEquals(DiagnosticState.RESUMED,
                events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>().last().composeState)
        } finally {
            diagnostics.close()
        }
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
    fun trackingDetachRecordsLastObservedOwnershipWithoutInventingUIKitState() {
        val events = mutableListOf<RuntimeDiagnosticEvent>()
        val diagnostics = IosLifecycleDiagnostics(events::add)
        val (window, unusedWindow) = DiagnosticOwnerWindows.create(UIWindowScene(), UIWindowScene())
        val host = UIViewController()
        window.rootViewController = host
        window.addSubview(host.view)
        try {
            diagnostics.install()
            diagnostics.attachHost("host", host)
            val last = events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>().last()
            assertTrue(last.attached)
            // UIKit may already have detached the view before Compose disposes its tracking.
            host.view.removeFromSuperview()
            diagnostics.composeState("host", Lifecycle.State.CREATED)
            val unattached = events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>().last()
            assertEquals(false, unattached.attached)
            assertEquals("none", unattached.windowId)
            diagnostics.detachHost("host")
            val detached = events.filterIsInstance<RuntimeDiagnosticEvent.HostTrackingDetached>().single()
            assertEquals("host", detached.hostId)
            assertEquals(last.windowId, detached.windowId)
            assertEquals(last.sceneId, detached.sceneId)
            assertEquals(unattached, events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>().last())
            assertNull(diagnostics.attachedHost("host"))
            val count = events.size
            diagnostics.detachHost("host")
            diagnostics.composeState("host", Lifecycle.State.DESTROYED)
            assertEquals(count, events.size)
            NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationDidBecomeActiveNotification, null)
            assertEquals(2, events.filterIsInstance<RuntimeDiagnosticEvent.OwnershipObserved>().size)
            assertEquals(1, events.filterIsInstance<RuntimeDiagnosticEvent.HostTrackingDetached>().size)
        } finally {
            diagnostics.close()
            DiagnosticOwnerWindows.dispose(window, unusedWindow)
        }
    }

    private fun assertNativeReleased(weak: kotlin.native.ref.WeakReference<*>) {
        val deadline = kotlin.time.TimeSource.Monotonic.markNow()
        do {
            // GC schedules ObjC releases on Main once its finalizer processor is active.
            // Do not hold a weak.get() result across collection or the run-loop drain.
            kotlinx.cinterop.autoreleasepool {
                kotlin.native.runtime.GC.collect()
                platform.Foundation.NSRunLoop.mainRunLoop.runUntilDate(
                    platform.Foundation.NSDate.dateWithTimeIntervalSinceNow(0.02)
                )
            }
            val released = kotlinx.cinterop.autoreleasepool { weak.get() == null }
            if (released) return
        } while (deadline.elapsedNow() < 5.seconds)
        assertNull(weak.get(), "Native owner remains retained after GC and main-thread finalization")
    }

    @Test
    fun nativeIdentityRegistryDoesNotRetainOwners() {
        fun identify(): kotlin.native.ref.WeakReference<NSObject> {
            val owner = NSObject()
            NativeDiagnosticIdentities.identity(owner)
            return kotlin.native.ref.WeakReference(owner)
        }
        val weak = kotlinx.cinterop.autoreleasepool { identify() }
        assertNativeReleased(weak)
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
        val weak = kotlinx.cinterop.autoreleasepool { attachAndDetach() }
        try {
            assertNativeReleased(weak)
        } finally {
            diagnostics.close()
        }
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
