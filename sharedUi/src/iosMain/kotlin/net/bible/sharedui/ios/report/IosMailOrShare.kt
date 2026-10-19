@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.report

import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.coroutines.*
import net.bible.sharedcore.platform.files.FilePresentation
import net.bible.sharedcore.platform.files.IosPresenter
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.CoreGraphics.CGRectMake
import platform.Foundation.*
import platform.MessageUI.*
import platform.UIKit.*
import platform.darwin.NSObject

/** Owns staging files and UIKit's weak mail delegate until the shared terminal-event owner dismisses. */
internal class IosMailOrShare(private val presenter: () -> UIViewController? = IosPresenter::topViewController) {
    suspend fun send(to: String, subject: String, body: String, attachments: List<Attachment>) = withContext(Dispatchers.Main) {
        val directory = (NSTemporaryDirectory() + "andbible-report-" + NSUUID().UUIDString).toPath()
        var dismissed: CompletableDeferred<Unit>? = null
        try {
            val files = withContext(Dispatchers.IO) {
                FileSystem.SYSTEM.createDirectory(directory)
                attachments.map { attachment ->
                    require(attachment.name.isNotBlank() && attachment.name !in listOf(".", "..") &&
                        attachment.name.none { it == '/' || it == '\\' || it == '\u0000' })
                    val path = directory / attachment.name
                    FileSystem.SYSTEM.write(path) { write(attachment.bytes) }
                    attachment to path
                }
            }
            val host = presenter() ?: return@withContext
            suspendCancellableCoroutine<Unit> { continuation ->
                val mail = reportTransport(MFMailComposeViewController.canSendMail()) == ReportTransport.MAIL
                val sheet: UIViewController = if (mail) MFMailComposeViewController().apply {
                    setToRecipients(listOf(to))
                    setSubject(subject)
                    setMessageBody(body, isHTML = false)
                    attachments.forEach { attachment ->
                        val data = if (attachment.bytes.isEmpty()) NSData() else attachment.bytes.usePinned {
                            NSData.create(bytes = it.addressOf(0), length = attachment.bytes.size.toULong())
                        }
                        addAttachmentData(data, mimeType = attachment.mime, fileName = attachment.name)
                    }
                } else UIActivityViewController(
                    activityItems = listOf(body) + files.map { NSURL.fileURLWithPath(it.second.toString()) },
                    applicationActivities = null,
                ).apply { setValue(subject, forKey = "subject") }
                val completion = CompletableDeferred<Unit>()
                var delegate: MailDelegate? = null
                val presentation = FilePresentation(sheet, continuation, Unit, release = {
                    (sheet as? MFMailComposeViewController)?.mailComposeDelegate = null
                    delegate = null
                    completion.complete(Unit)
                })
                if (sheet is MFMailComposeViewController) {
                    delegate = MailDelegate { presentation.finish(Unit) }
                    sheet.mailComposeDelegate = delegate
                } else (sheet as UIActivityViewController).completionWithItemsHandler = { _, _, _, _ -> presentation.finish(Unit) }
                sheet.popoverPresentationController?.apply {
                    sourceView = host.view
                    sourceRect = host.view.bounds.useContents { CGRectMake(origin.x + size.width / 2, origin.y + size.height / 2, 0.0, 0.0) }
                }
                dismissed = completion
                presentation.present(host)
            }
        } finally {
            withContext(NonCancellable) {
                dismissed?.await()
                withContext(Dispatchers.IO) { FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false) }
            }
        }
    }
}

private class MailDelegate(private val finish: () -> Unit) : NSObject(), MFMailComposeViewControllerDelegateProtocol {
    override fun mailComposeController(controller: MFMailComposeViewController, didFinishWithResult: MFMailComposeResult, error: NSError?) = finish()
}
