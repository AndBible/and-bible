@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.files

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.service.sword.epub.epubInitials
import org.crosswire.jsword.book.Books
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.IosFileDelivery
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.ios.ports.IosDocumentPicker
import net.bible.sharedui.ios.ports.IosImagePicker
import net.bible.sharedui.ios.ports.IosPromptFiles
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.UIImage
import platform.posix.memcpy

/** Independent destination checks for debug app-hosted diagnostics; never supplies picker results. */
internal object IosFilesPayloadProof {
    private inline fun <T> access(folder: DocumentRef, block: (NSURL) -> T): T {
        val url = NSURL.URLWithString(folder.uri) ?: error("Invalid selected folder URL")
        check(url.isFileURL())
        val scoped = url.startAccessingSecurityScopedResource()
        try { return block(url) }
        finally { if (scoped) url.stopAccessingSecurityScopedResource() }
    }

    private fun destination(folder: DocumentRef, name: String): NSURL = access(folder) { url ->
        require(name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it < ' ' })
        url.URLByAppendingPathComponent(name)!!.also {
            check(!NSFileManager.defaultManager.fileExistsAtPath(it.path!!)) { "Destination already exists" }
        }
    }

    private fun payload(name: String) = "AndBible files port payload\n$name\nsecond line\n"

    private fun read(url: NSURL): String {
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url) ?: error("Destination is unreadable")
            return ByteArray(data.length.toInt()).also { bytes ->
                if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
            }.decodeToString()
        } finally { if (scoped) url.stopAccessingSecurityScopedResource() }
    }

    suspend fun create(document: IosDocumentPicker, name: String, folder: DocumentRef) {
        val destination = destination(folder, name)
        val ref = document.createDocument(name, "text/csv") ?: error("Save cancelled")
        val url = NSURL.URLWithString(ref.uri) ?: error("Invalid destination URL")
        check(url.isFileURL() && url.lastPathComponent == name)
        access(folder) {
            val scoped = url.startAccessingSecurityScopedResource()
            try {
                val actualPath = url.URLByStandardizingPath?.URLByResolvingSymlinksInPath?.path
                    ?: error("Cannot resolve returned destination")
                val expectedPath = destination.URLByStandardizingPath?.URLByResolvingSymlinksInPath?.path
                    ?: error("Cannot resolve selected destination")
                check(actualPath == expectedPath) {
                    "Picker returned a different destination than the selected local test folder"
                }
            } finally { if (scoped) url.stopAccessingSecurityScopedResource() }
        }
        val text = payload(name)
        IosPromptFiles().apply { write(ref, text); check(read(ref) == text) }
        access(folder) { check(read(destination) == text) { "Independent selected destination read differs" } }
    }

    suspend fun deliver(name: String, chooserTitle: String, folder: DocumentRef) {
        val destination = destination(folder, name)
        val source = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent(name)!!
        val text = payload(name)
        check(NSString.create(string = text).writeToURL(source, false, NSUTF8StringEncoding, null))
        try {
            check(IosFileDelivery().deliver(source.path!!, name, SaveOrShare.SAVE, "text/plain", "text/plain",
                null, null, chooserTitle) == DeliveryResult.DELIVERED)
            access(folder) { check(read(destination) == text) { "Selected local destination payload differs or is missing" } }
        } finally { NSFileManager.defaultManager.removeItemAtURL(source, null) }
    }

    suspend fun routingPreflight(name: String, epub: Boolean) = withContext(Dispatchers.Default) {
        val extension = if (epub) "epub" else "zip"
        require(Regex("F-OS-[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\\.$extension").matches(name))
        check(Books.installed().getBook("K4RawText") == null) { "SWORD routing fixture already registered" }
        check(Books.installed().getBook(epubInitials(name)) == null) { "Nonce EPUB already registered" }
        check(Books.installed().getBook(epubInitials("f-runtime.epub")) == null) { "EPUB routing fixture already registered" }
    }

    /** Provision only through native Save; never feed the incoming queue or install the fixture. */
    suspend fun deliverRoutingFixture(name: String, epub: Boolean, chooserTitle: String, folder: DocumentRef) {
        routingPreflight(name, epub)
        val destination = destination(folder, name)
        val fixture = if (epub) "f-runtime.epub" else "f-rawtext.zip"
        val resource = NSBundle.mainBundle.resourceURL!!.URLByAppendingPathComponent("files-port-fixtures/$fixture")!!
        val expected = NSData.dataWithContentsOfURL(resource) ?: error("Missing routing fixture")
        val staging = NSURL.fileURLWithPath(NSTemporaryDirectory())
            .URLByAppendingPathComponent("os-routing-${NSUUID().UUIDString}", true)!!
        check(NSFileManager.defaultManager.createDirectoryAtURL(staging, true, null, null))
        try {
            val source = staging.URLByAppendingPathComponent(name)!!
            check(NSFileManager.defaultManager.copyItemAtURL(resource, source, null))
            val mime = if (epub) "application/epub+zip" else "application/zip"
            check(IosFileDelivery().deliver(source.path!!, name, SaveOrShare.SAVE, mime, mime,
                null, null, chooserTitle) == DeliveryResult.DELIVERED)
            access(folder) {
                val actual = NSData.dataWithContentsOfURL(destination) ?: error("Selected routing destination is unreadable")
                check(expected.isEqualToData(actual)) { "Selected routing destination bytes differ from bundled fixture" }
            }
            routingPreflight(name, epub)
            IosOsRoutingReceipt.exported(name)
        } finally { NSFileManager.defaultManager.removeItemAtURL(staging, null) }
    }

    suspend fun image(picker: IosImagePicker) {
        val ref = picker.pickImage() ?: error("Image selection cancelled")
        val url = NSURL.URLWithString(ref) ?: error("Invalid image URL")
        check(url.isFileURL())
        try {
            // The production port has returned after dismissal and the provider callback has expired.
            val image = UIImage.imageWithContentsOfFile(url.path!!) ?: error("Copied image cannot be decoded")
            val cg = image.CGImage ?: error("No decoded image")
            check(CGImageGetWidth(cg) == 16uL && CGImageGetHeight(cg) == 16uL) { "Unexpected image dimensions" }
            val space = CGColorSpaceCreateDeviceRGB() ?: error("No RGB color space")
            try {
                val bytes = ByteArray(16 * 16 * 4)
                bytes.usePinned { pinned ->
                    val context = CGBitmapContextCreate(pinned.addressOf(0), 16uL, 16uL, 8uL, 64uL, space,
                        CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value) ?: error("No bitmap context")
                    try { CGContextDrawImage(context, CGRectMake(0.0, 0.0, 16.0, 16.0), cg) }
                    finally { CGContextRelease(context) }
                }
                val expected = listOf(listOf(255, 0, 0), listOf(0, 255, 0), listOf(0, 0, 255), listOf(255, 255, 0))
                val actual = listOf(4 to 4, 12 to 4, 4 to 12, 12 to 12).map { (x, y) ->
                    (0..2).map { bytes[(y * 16 + x) * 4 + it].toInt() and 255 }
                }
                // CoreGraphics' bitmap coordinate origin is opposite to the PNG row order.
                check(actual == expected || actual == listOf(expected[2], expected[3], expected[0], expected[1])) {
                    "Selected asset is not the expected quadrant fixture: $actual"
                }
            } finally { CGColorSpaceRelease(space) }
        } finally { NSFileManager.defaultManager.removeItemAtURL(url, null) }
    }
}
