package net.bible.android.control.page

/** Opens the key chooser that fits [page]'s document. Android: Intents on the Activity in front. */
interface KeyChooserLauncher {
    /** Returns false when there is no host to show a chooser on (nothing is shown). */
    fun startKeyChooser(page: CurrentPage): Boolean
}
