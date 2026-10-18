package net.bible.sharedui.app

import kotlinx.coroutines.CoroutineScope

/** What feature hosts need from the platform host (`NavHostComposeActivity` on Android). */
interface HostShell {
    /** Android: `lifecycleScope`. */
    val hostScope: CoroutineScope

    /** Android: `finish()`. */
    fun exitHost()

    /** Android: `setTitle(title)`. */
    fun setWindowTitle(title: String)

    /** Android: `recreate()`. */
    fun recreateHost()

    /** Back to the reading destination, as `startActivity(intentFor(READING), CLEAR_TOP|SINGLE_TOP)` does today. */
    fun openReading()

    fun setHistoryRoute(owner: Any, route: String)

    fun clearHistoryRoute(owner: Any)

    fun goBackInHistory(): Boolean

    /** In-graph navigation used by hosts that today call `navigateInGraph` / `navController.navigate`. */
    fun navigate(route: String)
}
