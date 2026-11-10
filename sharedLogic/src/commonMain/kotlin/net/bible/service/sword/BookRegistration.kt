package net.bible.service.sword

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books

/** Registers a driver book, distinguishing startup discovery from a new installation. */
fun registerBook(book: Book, startupDiscovery: Boolean = false) {
    Books.installed().addBook(book, startupDiscovery)
}
