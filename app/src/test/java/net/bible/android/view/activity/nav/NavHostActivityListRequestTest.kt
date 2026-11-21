package net.bible.android.view.activity.nav

import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.activity.ActivityListRequests
import net.bible.service.device.openActivityListIntent
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.getKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #4174: the progress notification's tap target carries an open-activity-list request; `NavHostComposeActivity`
 * must forward it to [ActivityListRequests] on a cold start (onCreate), on a warm one (onNewIntent), and must NOT
 * re-forward it when the system re-delivers the launch intent on a restore (savedInstanceState != null).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class NavHostActivityListRequestTest {
    private val requests: ActivityListRequests get() = getKoin().get()

    @Before fun drain() {
        resetComposeUiDispatcher()
        // The channel is a Koin singleton shared by the whole JVM: leave it empty before and after.
        take()
    }

    @After fun cleanUp() { take() }

    /** The pending request, or null if none arrives promptly. */
    private fun take(): Unit? = runBlocking { withTimeoutOrNull(300) { requests.requests.first() } }

    private fun launchIntent(): Intent = openActivityListIntent(ApplicationProvider.getApplicationContext())

    @Test fun coldStartForwardsTheRequestOnce() {
        val controller = Robolectric.buildActivity(NavHostComposeActivity::class.java, launchIntent())
        try {
            controller.create()
            assertNotNull("onCreate must forward the open-activity-list request", take())
            assertNull("one tap, one request", take())
        } finally { controller.close() }
    }

    @Test fun warmNewIntentForwardsTheRequest() {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), net.bible.sharedcore.nav.NavRoutes.READING),
        )
        try {
            controller.create()
            assertNull("a plain launch requests nothing", take())
            controller.newIntent(launchIntent())
            assertNotNull("onNewIntent must forward the open-activity-list request", take())
        } finally { controller.close() }
    }

    @Test fun restoreWithSavedInstanceStateDoesNotReopenTheList() {
        val intent = launchIntent()
        val controller = Robolectric.buildActivity(NavHostComposeActivity::class.java, intent)
        try {
            controller.create(Bundle())
            assertNull("a re-delivered launch intent must not reopen the list", take())
            assertEquals("the extra is dropped too", false, intent.hasExtra("net.bible.android.openActivityList"))
        } finally { controller.close() }
    }
}
