@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.coroutines.delay
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedui.components.AppDialogHost
import net.bible.sharedui.ios.ports.IosExternalLinks
import org.koin.mp.KoinPlatform
import platform.Foundation.NSThread
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.transitionCoordinator
import platform.UIKit.UISceneActivationStateForegroundActive

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

/**
 * One renderer for the existing app-wide queues. Background/native coverage only withdraws rendering;
 * it never answers a caller or discards its draft. Compose 1.11 scene-layer dialogs embed views,
 * not presented controllers, so their own visibility does not close the UIKit gate.
 */
@Composable
internal fun IosAppDialogOverlay() {
    val koin = remember { KoinPlatform.getKoin() }
    val dialogs = remember { koin.get<AppDialogController>() }
    val permissions = remember { koin.get<AgentPermissionController>() }
    val links = remember { IosExternalLinks() }
    val controller = LocalUIViewController.current
    // UIKit properties are not snapshot state. Poll on Compose's Main context even with no queue changes.
    val canRender by produceState(false, controller) {
        while (true) {
            value = canRenderIosAppDialogs(controller)
            delay(100)
        }
    }
    val shown by dialogs.pending.collectAsState()
    val progress by dialogs.progress.collectAsState()
    val permission by permissions.pending.collectAsState()
    if (!canRender) return
    AppDialogHost(
        shown = shown,
        progress = progress,
        permission = permission,
        onRespond = dialogs::respond,
        onPermissionChoice = permissions::respond,
        onPermissionDismiss = permissions::dismiss,
        onOpenExternal = links::open,
        askBeforeOpeningLink = links.askBeforeOpening,
        draftFor = dialogs::draft,
        onDraftChange = dialogs::saveDraft,
    )
}
