package net.bible.sharedlogic.util

actual fun Throwable.isThreadInterrupt(): Boolean = this is InterruptedException
