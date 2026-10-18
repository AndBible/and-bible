package net.bible.sharedui.app.ports

/** System document pickers. Host-scoped: the Android implementation registers launchers on the Activity. */
interface DocumentPicker {
    suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean): List<DocumentRef>
    suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef?
    suspend fun openDocumentTree(): DocumentRef?

    /**
     * Awaits the folder pick that was open when the previous host instance was recreated (its result is redelivered
     * to the new instance), or null when it was cancelled. Launches nothing. The default (a platform without such
     * redelivery) answers null at once.
     */
    suspend fun resumeDocumentTree(): DocumentRef? = null

    /** Like [resumeDocumentTree], for a multiple-document pick; empty when cancelled or not redelivered. */
    suspend fun resumeOpenDocuments(): List<DocumentRef> = emptyList()
}
