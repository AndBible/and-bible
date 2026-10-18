package net.bible.sharedlogic.startup

import androidx.annotation.VisibleForTesting
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.platform.SuspendHttpTransport
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.log.Log
import net.bible.service.sword.epub.epubBookType
import net.bible.service.sword.esword.eSwordBible
import net.bible.service.sword.mybible.myBibleBible
import net.bible.service.sword.mybible.myBibleCommentary
import net.bible.service.sword.mybible.myBibleDictionary
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mysword.mySwordBible
import net.bible.service.sword.mysword.mySwordCommentary
import net.bible.service.sword.mysword.mySwordDictionary
import org.crosswire.common.util.PropertyMap
import org.crosswire.common.util.Reporter
import org.crosswire.common.util.ReporterEvent
import org.crosswire.common.util.ReporterListener
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.book.install.InstallManager
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.internationalisation.LocaleProvider
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

/** Platform-specific pieces of the common startup sequence; see [SharedAppStartup.start]. */
class StartupHooks(
    /** Creates the module folders and points JSword at them; must run before JSword is touched. */
    val initialiseJSwordFolders: () -> Unit,
    /** Koin modules to start; only called when Koin is not already running. */
    val koinModules: () -> List<Module>,
    /** Extra Koin setup, e.g. `androidContext(app)` on Android. */
    val koinAppDeclaration: KoinAppDeclaration = {},
    /** Shows a JSword error to the user (de-duplication is the platform's business). */
    val reportJSwordError: (ReporterEvent) -> Unit,
    /** True once [initialiseJSwordFolders] actually set JSword up. */
    val isSwordLoaded: () -> Boolean,
)

/** The startup steps shared by Android and iOS, in the order Android has always run them. */
object SharedAppStartup {
    private const val TAG = "SharedAppStartup"
    // Startup runs on the main thread, so no synchronisation; @Volatile covers the test-thread reset.
    @kotlin.concurrent.Volatile private var errorListener: ReporterListener? = null

    /** Steps that need no Koin: repository site map, extra book types, jsword locale provider. Idempotent. */
    fun installEnvironment(repositories: PropertyMap, localeProvider: LocaleProvider) {
        InstallManager.installSiteMap(repositories)
        listOf(
            myBibleBible, myBibleCommentary, myBibleDictionary,
            mySwordBible, mySwordCommentary, mySwordDictionary,
            epubBookType, eSwordBible,
        ).forEach { type ->
            if (BookType.fromString(type.toString()) == null) BookType.addSupportedBookType(type)
        }
        LocaleProviderManager.setLocaleProvider(localeProvider)
    }

    /**
     * Folder init (hook), Koin start (once), download transport, store hooks, JSword error listener (once),
     * book-install watcher. Koin is started only if no Koin context exists: Robolectric reuses one JVM across
     * test classes, so this can run many times in one process.
     */
    fun start(hooks: StartupHooks) {
        // This must be done before accessing JSword to prevent default folders being used
        hooks.initialiseJSwordFolders()

        if (KoinPlatform.getKoinOrNull() == null) {
            startKoin {
                hooks.koinAppDeclaration(this)
                modules(hooks.koinModules())
            }
        }
        val koin = KoinPlatform.getKoin()

        // Folder initialisation precedes Koin; install the transport at the first safe point instead.
        installDownloadTransport(koin)

        SpeakSettings.store = koin.get()
        MyDocumentBookManager.launcher = koin.get()

        installErrorListener(hooks.reportJSwordError)

        // BookInstallWatcher is Koin-resolved, so it can only start once Koin is up (not inside initialiseJSwordFolders)
        if (hooks.isSwordLoaded()) startBookInstallWatcher(koin)
    }

    /** Installs the shared download transport; needs Koin up, and must run before any download consumer is resolved. */
    fun installDownloadTransport(koin: Koin) {
        WebResource.transport = SuspendHttpTransport(koin.get<DownloadHttp>())
    }

    /**
     * Starts the Koin-resolved [BookInstallWatcher]. Public so the Android wrapper can share it. Failures are
     * logged, not rethrown (must not stop app startup), but never silent: without it no installed book is backed up.
     */
    fun startBookInstallWatcher(koin: Koin) {
        try {
            koin.get<BookInstallWatcher>().startListening()
        } catch (e: Exception) {
            Log.e(TAG, "BookInstallWatcher failed to start; book installs will not be tracked", e)
        }
    }

    private fun installErrorListener(report: (ReporterEvent) -> Unit) {
        if (errorListener != null) return
        val listener = object : ReporterListener {
            override fun reportException(ev: ReporterEvent) = report(ev)
            override fun reportMessage(ev: ReporterEvent) = report(ev)
        }
        errorListener = listener
        Reporter.addReporterListener(listener)
    }

    @VisibleForTesting
    fun resetForTest() {
        errorListener?.let { Reporter.removeReporterListener(it) }
        errorListener = null
    }
}
