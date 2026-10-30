package net.bible.sharedcore.log

/** Writes one line to the platform's system log: NSLog on iOS, stderr on the JVM. */
expect fun nsLog(message: String)
