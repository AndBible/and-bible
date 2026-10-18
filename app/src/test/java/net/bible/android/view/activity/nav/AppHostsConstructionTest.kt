package net.bible.android.view.activity.nav

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import net.bible.sharedui.app.AppHosts
import net.bible.sharedui.app.AppHostsState
import net.bible.sharedui.strings.AndroidStrings
import org.koin.java.KoinJavaComponent.getKoin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
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
    fun noDatabaseTouchOnConstruction() {
        resetComposeUiDispatcher()
        DatabaseContainer.ready = false
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.WELCOME),
        ).create()
        try {
            val activity = controller.get()
            assertNotNull(activity.appHosts)
            assertEquals(AppHostsState(), activity.appHosts.saveState())
            DatabaseContainer.detachInstanceForTest()
            val standalone = AppHosts(getKoin(), activity, activity.hostPorts, AndroidStrings(activity))
            assertEquals(AppHostsState(), standalone.saveState())
            assertFalse("constructing the app hosts opened the database", DatabaseContainer.ready)
            assertNull("constructing the app hosts built the database container", DatabaseContainer.detachInstanceForTest())
            shadowOf(Looper.getMainLooper()).idle()
        } finally { controller.close() }
    }

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
