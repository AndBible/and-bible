package net.bible.sharedui.app.ports

interface DocumentDialogs {
    fun showAbout(documentId: String)
    fun unlock(documentId: String, onUnlocked: () -> Unit)
}
