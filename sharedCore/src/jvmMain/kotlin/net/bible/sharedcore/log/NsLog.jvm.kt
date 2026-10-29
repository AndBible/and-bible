package net.bible.sharedcore.log

actual fun nsLog(message: String) {
    System.err.println(message)
}
