@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedcore.platform.files

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log
import platform.UIKit.*
import platform.darwin.NSObject
import kotlin.coroutines.resume

object IosPresenter {
    /** Call on Main, just before presenting, so the active scene and modal stack are current. */
    fun topViewController(): UIViewController? {
        val scene = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull { it.activationState == UISceneActivationStateForegroundActive } ?: return null
        var controller = scene.windows.filterIsInstance<UIWindow>()
            .firstOrNull { it.isKeyWindow() }?.rootViewController ?: return null
        while (true) {
            if (controller.isBeingDismissed() || controller.isBeingPresented() || controller.transitionCoordinator != null) return null
            controller = controller.presentedViewController ?: return controller
        }
    }
}

/** Owns UIKit's weak delegates until one terminal event; all state transitions run on Main. */
internal class FilePresentation<T>(
    private var controller: UIViewController?,
    private var continuation: CancellableContinuation<T>?,
    private val cancelled: T,
    private var picked: ((List<*>) -> T)? = null,
    private var release: (() -> Unit)? = null,
) : NSObject(), UIDocumentPickerDelegateProtocol,
    UIAdaptivePresentationControllerDelegateProtocol, UIPopoverPresentationControllerDelegateProtocol {
    fun present(from: UIViewController) {
        val sheet = controller ?: return
        (sheet as? UIDocumentPickerViewController)?.delegate = this
        sheet.presentationController?.delegate = this
        sheet.popoverPresentationController?.delegate = this
        continuation?.invokeOnCancellation {
            // Cancellation may originate off-main. This independent dispatch must survive it.
            CoroutineScope(Dispatchers.Main).launch { finish(cancelled) }
        }
        if (continuation?.isActive != true || from.isBeingDismissed() || from.isBeingPresented() ||
            from.transitionCoordinator != null || from.presentedViewController != null || from.view.window == null) {
            finish(cancelled)
            return
        }
        try {
            from.presentViewController(sheet, animated = true, completion = null)
            sheet.presentationController?.delegate = this
        } catch (error: Exception) {
            finish(cancelled)
            throw error
        }
    }

    fun finish(result: T) {
        val pending = continuation ?: return
        continuation = null
        val sheet = controller
        controller = null
        picked = null
        (sheet as? UIDocumentPickerViewController)?.delegate = null
        (sheet as? UIActivityViewController)?.completionWithItemsHandler = null
        sheet?.presentationController?.delegate = null
        sheet?.popoverPresentationController?.delegate = null
        val cleanup = release
        release = null
        val completed = {
            cleanup?.invoke()
            if (pending.isActive) pending.resume(result)
        }
        if (sheet?.presentingViewController != null) {
            sheet.dismissViewControllerAnimated(true, completed)
        } else completed()
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        finish(picked?.invoke(didPickDocumentsAtURLs) ?: cancelled)
    }
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { finish(cancelled) }
    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) { finish(cancelled) }
    override fun popoverPresentationControllerDidDismissPopover(popoverPresentationController: UIPopoverPresentationController) {
        finish(cancelled)
    }
}

internal fun logFileFailure(message: String, error: Throwable? = null) {
    Log.w("IosFiles", message, error)
}
