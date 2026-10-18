package net.bible.sharedui.app.ports

/** Feature port for the one destination that pins the screen orientation (InstallZip). Host-scoped. */
interface OrientationLock {
    /** Locks the screen to portrait and returns the lambda that restores the previous orientation. */
    fun lockPortrait(): () -> Unit
}
