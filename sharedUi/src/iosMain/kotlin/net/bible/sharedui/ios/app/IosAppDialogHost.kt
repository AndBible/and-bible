@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.ios.ports.IosExternalLinks
import platform.Foundation.NSThread
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.transitionCoordinator
import platform.UIKit.UISceneActivationStateForegroundActive
import net.bible.sharedui.host.app.AppDialogHost

/** Inspect containment as well as the Compose controller: SwiftUI may present from an ancestor. */
internal fun canRenderIosAppDialogs(
    controller: UIViewController,
    isForegroundWindow: (UIWindow) -> Boolean = {
        it.windowScene?.activationState == UISceneActivationStateForegroundActive
    },
): Boolean {
    check(NSThread.isMainThread)
    val window = controller.view.window ?: return false
    if (!isForegroundWindow(window)) return false
    var current: UIViewController? = controller
    while (current != null) {
        if (current.isBeingPresented() || current.isBeingDismissed() ||
            current.transitionCoordinator != null || current.presentedViewController != null) return false
        current = current.parentViewController
    }
    return true
}

/** iOS [AppDialogHost]: [IosExternalLinks] and the UIKit presentation-state gate of the Compose view controller. */
@kotlin.native.HiddenFromObjC
object IosAppDialogHost : AppDialogHost {
    override fun newExternalLinks(): ExternalLinks = IosExternalLinks()

    @Composable
    override fun rememberCanRenderDialogs(): () -> Boolean {
        val controller = LocalUIViewController.current
        return remember(controller) { { canRenderIosAppDialogs(controller) } }
    }
}
