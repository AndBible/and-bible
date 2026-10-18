package net.bible.android.view.activity.nav

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun noDatabaseTouchOnConstruction() {
        resetComposeUiDispatcher()
        DatabaseContainer.ready = false
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.WELCOME),
        ).create()
        try {
            // onCreate has run; nothing has been pumped yet, so only the synchronous construction is measured.
            assertNotNull(controller.get().appHosts)
            assertFalse("constructing the app hosts opened the database", DatabaseContainer.ready)
            shadowOf(Looper.getMainLooper()).idle()
        } finally { controller.close() }
    }
}
