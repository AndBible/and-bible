package net.bible.sharedui.app.ports

/** System document pickers. Host-scoped: the Android implementation registers launchers on the Activity. */
interface DocumentPicker {
    suspend fun openDocuments(mimeTypes: List<String>, multiple: Boolean): List<DocumentRef>
    suspend fun createDocument(suggestedName: String, mimeType: String): DocumentRef?
    suspend fun openDocumentTree(): DocumentRef?
}
