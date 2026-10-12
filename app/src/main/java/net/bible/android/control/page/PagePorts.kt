package net.bible.android.control.page

/** Opens the key chooser that fits [page]'s document. Android: Intents on the Activity in front. */
interface KeyChooserLauncher {
    /** Returns false when there is no host to show a chooser on (nothing is shown). */
    fun startKeyChooser(page: CurrentPage): Boolean
}

/** The global display-settings row, delegated to Android global settings today; a future snapshot seam. */
interface GlobalTextDisplaySettingsSource {
    val current: net.bible.android.database.WorkspaceEntities.TextDisplaySettings
}
