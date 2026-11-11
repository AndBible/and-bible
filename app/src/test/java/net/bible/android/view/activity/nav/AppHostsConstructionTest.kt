package net.bible.android.view.activity.nav

import android.os.Looper
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.speak.SpeakTransportServiceImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.AppHosts
import net.bible.sharedui.app.AppResultChannels
import net.bible.sharedui.app.AppHostsState
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.strings.AndroidStrings
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.getKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Review Focus 4: a welcome start builds the shared app hosts without opening the database. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AppHostsConstructionTest {
    @After fun restore() {
        DatabaseContainer.ready = true
        net.bible.test.DatabaseResetter.resetDatabase()
    }

    /**
     * The activity's own onCreate builds the database container for other reasons, so the container is
     * detached after the host is up and [AppHosts] is constructed on its own: that construction alone must
     * not build it (`instance` opens lazily under test even when not `ready`, hence the detach seam).
     */
    @Test
    fun noDatabaseTouchOnConstruction() = checkConstructionDoesNotTouchDatabase(holdSpecialLabelLock = false)

    /**
     * The host's start also constructs [SpeakTransportServiceImpl], whose init launches a bookmark-visibility
     * refresh on the application scope (`Dispatchers.Default`): special-label and bookmark reads. In a loaded
     * full-suite run that refresh could still be going when the container was detached, and its next read built
     * a new one, failing the check above although [AppHosts] touched nothing. Holding the special-label lock
     * through the activity start forces that ordering: the refresh is still pending when the start returns.
     */
    @Test
    fun startupSpeakRefreshStillPendingAtDetachIsNotBlamedOnTheHosts() =
        checkConstructionDoesNotTouchDatabase(holdSpecialLabelLock = true)

    private fun checkConstructionDoesNotTouchDatabase(holdSpecialLabelLock: Boolean) {
        resetComposeUiDispatcher()
        DatabaseContainer.ready = false
        val original = DatabaseContainer.containerFactory
        val builds = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val specialLabelLock = BookmarkControl::class.java.getDeclaredField("SPECIAL_LABEL_LOCK")
            .apply { isAccessible = true }.get(null) as Mutex
        if (holdSpecialLabelLock) runBlocking { specialLabelLock.lock() }
        val controller = try {
            Robolectric.buildActivity(
                NavHostComposeActivity::class.java,
                NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.WELCOME),
            ).create()
        } finally {
            if (holdSpecialLabelLock) specialLabelLock.unlock()
        }
        val speakTransport = getKoin().get<SpeakTransportServiceImpl>()
        try {
            val activity = controller.get()
            assertNotNull(activity.appHosts)
            assertEquals(AppHostsState(), activity.appHosts.saveState())
            // The host's start legitimately reads the database in the background (the speak transport's visibility
            // refresh). That is not what this test measures: let it finish against the live container first.
            runBlocking { withTimeout(10_000) { speakTransport.awaitBookmarkVisibilityRefresh() } }
            DatabaseContainer.detachInstanceForTest()
            // Witness: record who builds a container from here on, so a failure names the builder
            // (this test was red once in a full run and green alone, so the builder was a background thread).
            DatabaseContainer.containerFactory = {
                builds += Throwable("container built on thread '${Thread.currentThread().name}'")
                original()
            }
            val standalone = AppHosts(getKoin(), activity, activity.hostPorts, AndroidStrings(activity))
            assertEquals(AppHostsState(), standalone.saveState())
            // Assembling the deps (as every host launch does) must stay lazy too: no service, no database.
            val nav = NavHostController(activity)
            val graphs = standalone.graphs(nav, channels())
            listOf(
                graphs.ai, graphs.bookmark, graphs.readingPlan, graphs.search, graphs.settings, graphs.download,
                graphs.myDocuments, graphs.chooser, graphs.workspace, graphs.backup, graphs.installZip, graphs.welcome,
            ).forEach { assertNotNull(it) }
            assertFalse("constructing the app hosts opened the database", DatabaseContainer.ready)
            // Let the known startup work finish, so a read it makes after the detach is caught here, not left to luck.
            runBlocking { withTimeout(10_000) { speakTransport.awaitBookmarkVisibilityRefresh() } }
            val leaked = DatabaseContainer.detachInstanceForTest()
            if (leaked != null) {
                fail(
                    "constructing the app hosts built the database container; builders: " +
                        builds.joinToString("\n") { it.stackTraceToString().lines().take(40).joinToString("\n") },
                )
            }
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            DatabaseContainer.containerFactory = original
            controller.close()
        }
    }

    private fun channels() = AppResultChannels(
        readingProgress = NavResultChannel { },
        bookmark = NavResultChannel { },
        manageLabels = NavResultChannel { },
        labelEdit = NavResultChannel { },
        repositoryEditor = NavResultChannel { },
        myDocumentPages = NavResultChannel { },
        myDocuments = NavResultChannel { },
        keyChooser = NavResultChannel { },
        passage = NavResultChannel { },
        document = NavResultChannel { },
        installZip = NavResultChannel { },
        workspace = NavResultChannel { },
        textSettings = NavResultChannel { },
        beforeSwitchMyDocument = { },
    )

    /** The `nav_app_hosts` bundle key: restored into [AppHosts] in onCreate, written back by onSaveInstanceState. */
    @Test
    fun appHostsStateSurvivesSaveAndRestore() {
        resetComposeUiDispatcher()
        val saved = android.os.Bundle().apply {
            putString("nav_app_hosts", kotlinx.serialization.json.Json.encodeToString(AppHostsState.serializer(), AppHostsState(version = 7)))
        }
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.WELCOME),
        ).create(saved)
        try {
            assertEquals(AppHostsState(version = 7), controller.get().appHosts.saveState())
            val out = android.os.Bundle()
            controller.saveInstanceState(out)
            assertEquals(
                AppHostsState(version = 7),
                kotlinx.serialization.json.Json.decodeFromString(AppHostsState.serializer(), out.getString("nav_app_hosts")!!),
            )
        } finally { controller.close() }
    }
}
