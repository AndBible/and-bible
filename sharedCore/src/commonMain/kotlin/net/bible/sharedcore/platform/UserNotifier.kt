package net.bible.sharedcore.platform

/** Tells the user something went wrong, without domain code knowing about dialogs. */
interface UserNotifier {
    fun showInfo(message: String, long: Boolean = false)
    fun showError(message: String, cause: Throwable? = null)
}
