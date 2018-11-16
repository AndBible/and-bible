package net.bible.service.device.speak.event

import net.bible.service.device.speak.SpeakCommand
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

data class SpeakProgressEvent(val book: Book, val key: Key, val synchronize: Boolean, val speakCommand: SpeakCommand?)