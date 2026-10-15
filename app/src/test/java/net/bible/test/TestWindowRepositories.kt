package net.bible.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import net.bible.android.control.page.window.WindowRepository

/**
 * Scopes of the window repositories tests build by hand. Their windows launch loadText/fetchDocument work on
 * that scope; a plain `CoroutineScope(Dispatchers.Main)` is never cancelled, so the work outlived its test and
 * failed in a later one (UncaughtExceptionsBeforeTest: a dead context, a stopped Koin). The fixture cancels them
 * all at teardown ([net.bible.android.TestBibleApplication.onTerminate]).
 */
object TestWindowRepositories {
    private val scopes = mutableListOf<CoroutineScope>()

    /** The scope a hand-built repository should use: `Dispatchers.Main`, cancelled when the test ends. */
    fun scope(): CoroutineScope = CoroutineScope(Dispatchers.Main).also { synchronized(scopes) { scopes += it } }

    fun cancelAll() {
        val all = synchronized(scopes) { scopes.toList().also { scopes.clear() } }
        all.forEach { it.cancel() }
    }
}

/** A window repository on a [TestWindowRepositories.scope]; use instead of `WindowRepository(CoroutineScope(Dispatchers.Main))`. */
fun testWindowRepository(): WindowRepository = WindowRepository(TestWindowRepositories.scope())
