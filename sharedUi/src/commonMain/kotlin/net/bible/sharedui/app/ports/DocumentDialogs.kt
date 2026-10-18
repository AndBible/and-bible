package net.bible.sharedui.app.ports

interface DocumentDialogs {
    fun showAbout(documentId: String)
    fun unlock(documentId: String, onUnlocked: () -> Unit)

    /** Like [unlock] but suspends until the dialog is answered; true when the document is now unlocked. */
    suspend fun awaitUnlock(documentId: String): Boolean
}
