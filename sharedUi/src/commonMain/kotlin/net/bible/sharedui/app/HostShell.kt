@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app

import kotlinx.coroutines.CoroutineScope

/** What feature hosts need from the platform host (`NavHostComposeActivity` on Android). */
@kotlin.native.HiddenFromObjC
interface HostShell {
    /** Android: `lifecycleScope`. */
    val hostScope: CoroutineScope

    /** Android: `finish()`. */
    fun exitHost()

    /** Android: `setTitle(title)`. */
    fun setWindowTitle(title: String)

    /** Android: `recreate()`. */
    fun recreateHost()

    /**
     * The host window's current title (what [setWindowTitle] last set). Android: `title`. The bookmark list reads it
     * as the description of the bookmark the user picked.
     */
    val windowTitle: String get() = ""

    /**
     * False once the host is finishing or destroyed, so results that arrive later are not delivered into it.
     * Android: `!isDestroyed && !isFinishing`.
     */
    val isHostAlive: Boolean get() = true

    /** Back to the reading destination, as `startActivity(intentFor(READING), CLEAR_TOP|SINGLE_TOP)` does today. */
    fun openReading()

    fun setHistoryRoute(owner: Any, route: String)

    fun clearHistoryRoute(owner: Any)

    fun goBackInHistory(): Boolean

    /** In-graph navigation used by hosts that today call `navigateInGraph` / `navController.navigate`. */
    fun navigate(route: String)
}
