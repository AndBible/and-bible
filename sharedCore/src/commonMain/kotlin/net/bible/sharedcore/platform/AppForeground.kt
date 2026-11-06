package net.bible.sharedcore.platform

/** Whether the app window is currently in the foreground (focused), used to suppress redundant notifications (#4012). */
fun interface AppForeground {
    fun isForeground(): Boolean
}
