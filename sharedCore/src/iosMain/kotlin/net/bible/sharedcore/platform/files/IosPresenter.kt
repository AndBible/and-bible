@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedcore.platform.files

import kotlinx.cinterop.useContents
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

/** Owns UIKit's weak delegates until one terminal event; all state transitions run on Main.
 * Public so the iOS report composer can reuse the same cancellation/dismissal owner across modules.
 */
class FilePresentation<T>(
    private var controller: UIViewController?,
    private var continuation: CancellableContinuation<T>?,
    private val cancelled: T,
    private var picked: ((List<*>) -> T)? = null,
    private var release: (() -> Unit)? = null,
) : NSObject(), UIDocumentPickerDelegateProtocol,
    UIAdaptivePresentationControllerDelegateProtocol, UIPopoverPresentationControllerDelegateProtocol {
    val isPending: Boolean get() = continuation != null

    fun present(from: UIViewController) {
        val sheet = controller ?: return
        (sheet as? UIDocumentPickerViewController)?.delegate = this
        // UIAlertController owns its presentation delegate; replacing it raises an NSException.
        if (sheet !is UIAlertController) {
            sheet.presentationController?.delegate = this
            sheet.popoverPresentationController?.delegate = this
        }
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
            if (sheet !is UIAlertController) sheet.presentationController?.delegate = this
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
        if (sheet !is UIAlertController) {
            sheet?.presentationController?.delegate = null
            sheet?.popoverPresentationController?.delegate = null
        }
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

/** Where a popover-style sheet (iPad) points at on the presenting view. */
@kotlin.native.HiddenFromObjC
enum class SheetAnchor { Center, BottomCenter }

/** The popover source rectangle for [anchor] inside a view of the given size (a zero or 1pt rect: popovers only need a point). */
@kotlin.native.HiddenFromObjC
fun sheetAnchorRect(anchor: SheetAnchor, originX: Double, originY: Double, width: Double, height: Double): Triple<Double, Double, Double> =
    when (anchor) {
        SheetAnchor.Center -> Triple(originX + width / 2, originY + height / 2, 0.0)
        SheetAnchor.BottomCenter -> Triple(originX + width / 2, originY + height - 1.0, 1.0)
    }

/** Narrow bridge for host-owned sheets; terminal ownership remains in [FilePresentation].
 * [anchor] defaults to the view centre (pickers, reports); share sheets pass [SheetAnchor.BottomCenter].
 */
@kotlin.native.HiddenFromObjC
class IosSheetPresentation<T>(
    controller: UIViewController,
    continuation: CancellableContinuation<T>,
    cancelled: T,
    private val anchor: SheetAnchor = SheetAnchor.Center,
    release: () -> Unit,
) {
    private val presentation = FilePresentation(controller, continuation, cancelled, release = release)
    val isPending: Boolean get() = presentation.isPending
    fun finish(result: T) = presentation.finish(result)
    fun present(from: UIViewController) {
        controllerAnchor(from)
        presentation.present(from)
    }
    private val sheet = controller
    private fun controllerAnchor(from: UIViewController) {
        sheet.popoverPresentationController?.apply {
            sourceView = from.view
            sourceRect = from.view.bounds.useContents {
                val (x, y, side) = sheetAnchorRect(anchor, origin.x, origin.y, size.width, size.height)
                platform.CoreGraphics.CGRectMake(x, y, side, side)
            }
        }
    }
}

internal fun logFileFailure(message: String, error: Throwable? = null) {
    Log.w("IosFiles", message, error)
}
