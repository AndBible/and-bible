package net.bible.sharedcore.platform.desktop

/** Shared Linux detection for desktop startup and platform bindings. */
fun isLinuxDesktop(osName: String = System.getProperty("os.name").orEmpty()): Boolean =
    osName.contains("linux", ignoreCase = true)
