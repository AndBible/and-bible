package net.bible.sharedlogic.util

/** True for a JVM thread interrupt ([InterruptedException]); never true on platforms without thread interrupts. */
expect fun Throwable.isThreadInterrupt(): Boolean
