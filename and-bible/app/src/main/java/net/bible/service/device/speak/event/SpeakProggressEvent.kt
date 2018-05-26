package net.bible.service.device.speak.event

import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

data class SpeakProggressEvent(val book: Book, val key: Key, val synchronize: Boolean)