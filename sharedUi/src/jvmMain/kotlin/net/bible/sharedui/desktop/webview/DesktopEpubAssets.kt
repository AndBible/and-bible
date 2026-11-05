package net.bible.sharedui.desktop.webview

import kotlinx.coroutines.runBlocking
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.epubBackend
import net.bible.service.sword.epub.isEpub
import net.bible.sharedui.webview.PageAssetEpubSource
import net.bible.sharedui.webview.PageAssetRoutes
import okio.Source
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import java.nio.file.Files

/** Installed EPUB assets; the unqualified image route belongs to the requesting pane, never the active window. */
internal class DesktopEpubAssets(
    private val currentBook: () -> Book?,
    private val installedBook: (String) -> Book? = { Books.installed().getBook(it) },
) : PageAssetEpubSource {
    override fun openResource(relativePath: String): Source? = open(currentBook(), relativePath)

    override fun openBookResource(initials: String, relativePath: String): Source? = open(installedBook(initials), relativePath)

    private fun open(book: Book?, relativePath: String): Source? {
        val backend = book?.takeIf { it.isEpub }?.epubBackend ?: return null
        if (!PageAssetRoutes.isSafeRelative(relativePath)) return null
        // The backend's stream lease protects retirement, but it does not constrain symlinks to its root.
        val root = backend.state.rootFolder.toNioPath()
        val target = root.resolve(relativePath)
        val safe = try { Files.isRegularFile(target) && target.toRealPath().startsWith(root.toRealPath()) }
            catch (_: java.io.IOException) { false }
        return if (safe) backend.openResource(relativePath) else null
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
