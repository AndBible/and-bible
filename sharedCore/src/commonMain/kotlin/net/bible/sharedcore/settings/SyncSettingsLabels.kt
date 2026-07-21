package net.bible.sharedcore.settings

/**
 * Host-resolved strings for the cloud sync settings screen (category titles + row
 * titles/summaries), kept out of the controller so tests can supply stubs and translated strings
 * stay on the Android side (`strings.xml` / `xml/sync_settings.xml`). Mirrors `AppSettingsLabels`.
 */
data class SyncSettingsLabels(
    val screenTitle: String,

    // Category titles
    val generalCat: String,
    val categoriesCat: String,
    val documentSyncCat: String,

    // General
    val syncAdapterTitle: String,
    val cloudSyncResetTitle: String,
    val cloudSyncResetSummary: String,
    val cloudSyncInfoTitle: String,
    val cloudSyncServerUrlTitle: String,
    val cloudSyncUsernameTitle: String,
    val cloudSyncPasswordTitle: String,
    val cloudSyncFolderPathTitle: String,
    val cloudSyncFolderPathSummary: String,

    // Synchronization categories (sync_enable_* toggles)
    val syncEnableBookmarksTitle: String,
    val syncEnableBookmarksSummary: String,
    val syncEnableWorkspacesTitle: String,
    val syncEnableWorkspacesSummary: String,
    val syncEnableReadingplansTitle: String,
    val syncEnableReadingplansSummary: String,
    val syncEnableMydocumentsTitle: String,
    val syncEnableMydocumentsSummary: String,
    val syncEnableAiSettingsTitle: String,
    val syncEnableAiSettingsSummary: String,
    val syncEnableProgressTitle: String,
    val syncEnableProgressSummary: String,
    val syncEnableDocumentsTitle: String,
    val syncEnableDocumentsSummary: String,

    // Document sync (sync_documents_* toggles + manage row)
    val syncDocumentsAutoDownloadTitle: String,
    val syncDocumentsAutoDownloadSummary: String,
    val syncDocumentsAutoUploadTitle: String,
    val syncDocumentsAutoUploadSummary: String,
    val syncDocumentsAutoDeleteTitle: String,
    val syncDocumentsAutoDeleteSummary: String,
    val syncDocumentsWifiOnlyTitle: String,
    val syncDocumentsWifiOnlySummary: String,
    val documentSyncManageTitle: String,
    val documentSyncManageSummary: String,
) {
    companion object {
        fun forTest() = SyncSettingsLabels(
            screenTitle = "Device synchronization",

            generalCat = "Info & general settings",
            categoriesCat = "Synchronization categories",
            documentSyncCat = "Document sync",

            syncAdapterTitle = "Synchronization Backend",
            cloudSyncResetTitle = "Sign Out",
            cloudSyncResetSummary = "Disable synchronizing and reset related settings.",
            cloudSyncInfoTitle = "Cloud information",
            cloudSyncServerUrlTitle = "Server URL",
            cloudSyncUsernameTitle = "User name",
            cloudSyncPasswordTitle = "Password",
            cloudSyncFolderPathTitle = "Sync folder path",
            cloudSyncFolderPathSummary = "Parent folder for sync data (empty = root)",

            syncEnableBookmarksTitle = "Bookmarks",
            syncEnableBookmarksSummary = "Bookmarks, Labels and Study Pads",
            syncEnableWorkspacesTitle = "Workspaces",
            syncEnableWorkspacesSummary = "Workspaces and Windows",
            syncEnableReadingplansTitle = "Reading Plans",
            syncEnableReadingplansSummary = "Reading plans and their statuses",
            syncEnableMydocumentsTitle = "My Documents",
            syncEnableMydocumentsSummary = "My Documents and their content",
            syncEnableAiSettingsTitle = "AI Settings",
            syncEnableAiSettingsSummary = "AI prompts and provider configurations",
            syncEnableProgressTitle = "Reading Progress",
            syncEnableProgressSummary = "Memorized verses and chapter reading records",
            syncEnableDocumentsTitle = "Documents",
            syncEnableDocumentsSummary = "Installed Bibles, commentaries and other documents",

            syncDocumentsAutoDownloadTitle = "Download automatically",
            syncDocumentsAutoDownloadSummary = "Automatically download new and updated documents from the cloud to this device",
            syncDocumentsAutoUploadTitle = "Upload automatically",
            syncDocumentsAutoUploadSummary = "Automatically upload documents installed on this device to the cloud",
            syncDocumentsAutoDeleteTitle = "Apply removals automatically",
            syncDocumentsAutoDeleteSummary = "Automatically remove documents from this device when they are removed on another device",
            syncDocumentsWifiOnlyTitle = "Sync documents on Wi-Fi only",
            syncDocumentsWifiOnlySummary = "Automatic document transfers wait for an unmetered (Wi-Fi) connection",
            documentSyncManageTitle = "Synced documents",
            documentSyncManageSummary = "View and manage documents stored in the cloud",
        )
    }
}
