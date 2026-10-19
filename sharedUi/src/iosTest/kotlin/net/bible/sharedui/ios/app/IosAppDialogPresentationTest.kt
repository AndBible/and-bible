@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.app

import platform.Foundation.NSThread
import platform.UIKit.*
import kotlin.test.*

/**
 * Pins the production gate's UIKit relationship traversal, not actual app lifecycle/presentation.
 * The foreground predicate is injected because standalone Native tests have no active app scene.
 * Real UIWindow attachment/parent containment are used; native modal/transition getters are doubles.
 * IncomingFilesUITests supplies the actual app-root dialog/acknowledgement integration proof.
 */
class IosAppDialogPresentationTest {
    private class Host : UIViewController(null, null) {
        var nativeModal: UIViewController? = null
        var dismissing = false
        override fun presentedViewController(): UIViewController? = nativeModal
        override fun isBeingDismissed(): Boolean = dismissing
    }

    private fun attached(block: (UIWindow, Host, UIViewController) -> Unit) {
        check(NSThread.isMainThread)
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        val parent = Host()
        val compose = UIViewController(null, null)
        window.rootViewController = parent
        parent.addChildViewController(compose)
        parent.view.addSubview(compose.view)
        compose.didMoveToParentViewController(parent)
        window.makeKeyAndVisible()
        try {
            assertTrue(parent.isEqual(compose.parentViewController), "Actual parent containment must match")
            assertTrue(window.isEqual(compose.view.window), "Actual UIWindow attachment must match")
            block(window, parent, compose)
        } finally {
            compose.willMoveToParentViewController(null)
            compose.view.removeFromSuperview()
            compose.removeFromParentViewController()
            window.hidden = true
            window.rootViewController = null
        }
    }

    @Test fun attachedForegroundHostAllowsRendering() = attached { window, _, compose ->
        assertTrue(canRenderIosAppDialogs(compose) { it.isEqual(window) })
    }

    @Test fun detachedOrBackgroundHostDefersRendering() {
        check(NSThread.isMainThread)
        assertFalse(canRenderIosAppDialogs(UIViewController(null, null)) { true })
        attached { _, _, compose ->
            assertFalse(canRenderIosAppDialogs(compose) { false })
        }
    }

    @Test fun nativeModalOnAncestorDefersUntilItLeaves() = attached { _, parent, compose ->
        parent.nativeModal = UIDocumentPickerViewController(documentTypes = listOf("public.data"), inMode = UIDocumentPickerMode.UIDocumentPickerModeOpen)
        assertFalse(canRenderIosAppDialogs(compose) { true }, "Ancestor's native picker must block app dialogs")
        parent.nativeModal = null
        assertTrue(canRenderIosAppDialogs(compose) { true }, "Queued dialog must become renderable after picker leaves")
    }

    @Test fun dismissingAncestorDefersUntilTransitionFinishes() = attached { _, parent, compose ->
        parent.dismissing = true
        assertFalse(canRenderIosAppDialogs(compose) { true }, "Ancestor dismissal is not a safe presentation state")
        parent.dismissing = false
        assertTrue(canRenderIosAppDialogs(compose) { true })
    }

    @Test fun embeddedComposeLayerDoesNotHideItsOwnDialog() = attached { _, parent, compose ->
        // Compose 1.11.1 embeds scene-layer views; it does not present another UIViewController.
        val layer = UIView(frame = windowBounds(compose))
        parent.view.addSubview(layer)
        try {
            assertNull(parent.presentedViewController)
            assertTrue(canRenderIosAppDialogs(compose) { true }, "Own embedded dialog layer must not close the gate")
        } finally { layer.removeFromSuperview() }
    }

    private fun windowBounds(controller: UIViewController) = controller.view.bounds
}
