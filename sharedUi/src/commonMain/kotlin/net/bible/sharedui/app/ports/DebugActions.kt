package net.bible.sharedui.app.ports

interface DebugActions {
    fun crashAppDelayed()
    fun showTestError(message: String)
}
