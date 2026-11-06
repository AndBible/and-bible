package net.bible.sharedui.desktop.webview

import kotlinx.coroutines.runBlocking
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.epubBackend
import net.bible.service.sword.epub.isEpub
import net.bible.sharedui.webview.PageAssetEpubSource
import net.bible.sharedui.webview.PageAssetRoutes
import okio.Path as OkioPath
import okio.Source
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books

/** Installed EPUB assets; the unqualified image route belongs to the requesting pane, never the active window. */
internal class DesktopEpubAssets(
    private val currentBook: () -> Book?,
    private val installedBook: (String) -> Book? = { Books.installed().getBook(it) },
) : PageAssetEpubSource {
    override fun openResource(relativePath: String): Source? = open(currentBook(), relativePath) { it.openResource(relativePath) }

    override fun openBookResource(initials: String, relativePath: String): Source? =
        open(installedBook(initials), relativePath) { it.openResource(relativePath) }

    /** Served straight from the book's directory: the backend verified the canonical path, so no temp copy is made. */
    override fun resourceFile(relativePath: String): OkioPath? = open(currentBook(), relativePath) { it.resourceFile(relativePath) }

    override fun bookResourceFile(initials: String, relativePath: String): OkioPath? =
        open(installedBook(initials), relativePath) { it.resourceFile(relativePath) }

    /** The backend refuses anything outside the EPUB directory; unusable names (Windows) and I/O errors are a 404. */
    private fun <T> open(book: Book?, relativePath: String, read: (EpubBackend) -> T?): T? {
        val backend = book?.takeIf { it.isEpub }?.epubBackend ?: return null
        if (!PageAssetRoutes.isSafeRelative(relativePath)) return null
        return try {
            read(backend)
        } catch (_: java.io.IOException) {
            null
        } catch (_: java.nio.file.InvalidPathException) {
            null
        }
    }

    override fun styleSheet(initials: String, key: String): String? {
        val book = installedBook(initials)?.takeIf { it.isEpub } ?: return null
        val backend: EpubBackend = book.epubBackend ?: return null
        val pageKey = try { book.getKey(key) } catch (_: Exception) { return null }
        return try {
            runBlocking { backend.styleSheetContent(pageKey) }
        } catch (_: IllegalStateException) {
            null // A retired page must not read replacement assets.
        }
    }
}
