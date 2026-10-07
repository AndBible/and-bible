package net.bible.android.control.event

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import android.widget.Toast
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import kotlin.concurrent.thread

/**
 * `BibleApplication`'s presentation of [UserMessages]. The activity is a `CalculatorComposeActivity`
 * (an `ActivityBase`, so it becomes `CurrentActivityHolder.currentActivity`), as in `DialogsShimTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class UserMessagesPresenterTest {
    private val controllers = mutableListOf<ActivityController<*>>()
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
    }

    private fun activity() =
        Robolectric.buildActivity(CalculatorComposeActivity::class.java).also { controllers += it }.setup().get()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun notifications(): List<Notification> =
        shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).allNotifications

    @Test
    fun toastByIdShowsTheResolvedStringShort() {
        activity()
        UserMessages.toast(R.string.stop)
        idle()
        assertEquals(app.getString(R.string.stop), ShadowToast.getTextOfLatestToast())
        assertEquals(Toast.LENGTH_SHORT, ShadowToast.getLatestToast().duration)
    }

    @Test
    fun aLongToastIsLong() {
        activity()
        UserMessages.toast(R.string.stop, long = true)
        idle()
        assertEquals(Toast.LENGTH_LONG, ShadowToast.getLatestToast().duration)
    }

    @Test
    fun toastByTextShowsTheText() {
        activity()
        UserMessages.toast("hello")
        idle()
        assertEquals("hello", ShadowToast.getTextOfLatestToast())
    }

    /** Review Focus 1. */
    @Test
    fun aToastFromABackgroundThreadIsShownOnMain() {
        activity()
        thread { UserMessages.toast("bg") }.join()
        assertEquals(0, ShadowToast.shownToastCount())
        idle()
        assertEquals("bg", ShadowToast.getTextOfLatestToast())
    }

    /** Review Focus 3. */
    @Test
    fun aFinishingActivityGetsNoToast() {
        val current = activity()
        UserMessages.toast("before finish")
        idle()
        assertEquals("before finish", ShadowToast.getTextOfLatestToast())
        ShadowToast.reset()
        current.finish()
        assertEquals(current, CurrentActivityHolder.currentActivity)
        assertTrue(current.isFinishing)
        UserMessages.toast("x")
        idle()
        assertEquals(0, ShadowToast.shownToastCount())
    }

    // Like DialogsShimTest: establish and restore the no-activity precondition even in a shared JVM.
    @Suppress("UNCHECKED_CAST")
    private fun holderActivities(): ArrayList<ActivityBase> =
        CurrentActivityHolder::class.java.getDeclaredField("activities").apply { isAccessible = true }
            .get(CurrentActivityHolder) as ArrayList<ActivityBase>

    @Test
    fun aMissingActivityGetsNoToast() {
        val activities = holderActivities()
        val saved = activities.toList()
        try {
            activities.clear()
            assertNull(CurrentActivityHolder.currentActivity)
            UserMessages.toast("no activity")
            idle()
            assertEquals(0, ShadowToast.shownToastCount())
            activity()
            UserMessages.toast("activity restored")
            idle()
            assertEquals("activity restored", ShadowToast.getTextOfLatestToast())
        } finally {
            activities.clear()
            activities.addAll(saved)
        }
    }

    @Test
    fun terminatingTheApplicationStopsPresentation() {
        val current = activity()
        UserMessages.toast("before terminate")
        idle()
        assertEquals("before terminate", ShadowToast.getTextOfLatestToast())
        ShadowToast.reset()
        UserMessages.errorNotification("before terminate")
        idle()
        assertEquals("before terminate", notifications().single().extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        ApplicationProvider.getApplicationContext<TestBibleApplication>().onTerminate()
        assertEquals(current, CurrentActivityHolder.currentActivity)
        assertTrue(!current.isFinishing)
        UserMessages.toast("after terminate")
        UserMessages.errorNotification("after terminate")
        idle()
        assertEquals(0, ShadowToast.shownToastCount())
        assertTrue(notifications().isEmpty())
    }

    /** Old applications must not multiply presentation in the next test's application. */
    @Test
    fun oneEmissionShowsExactlyOneToast() {
        activity()
        UserMessages.toast("once")
        idle()
        assertEquals(1, ShadowToast.shownToastCount())
    }

    @Test
    fun oneEmissionShowsExactlyOneToastAgain() {
        activity()
        UserMessages.toast("once")
        idle()
        assertEquals(1, ShadowToast.shownToastCount())
    }

    @Test
    fun errorNotificationShowsTheResolvedTextAndTheReportAction() {
        UserMessages.errorNotification(R.string.sync_error)
        idle()
        val n = notifications().single()
        assertEquals(app.getString(R.string.error_occurred), n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(app.getString(R.string.sync_error), n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(1, n.actions?.size ?: 0)
        assertEquals(app.getString(R.string.report), n.actions!![0].title.toString())
    }

    /** Review Focus 4. */
    @Test
    fun showReportButtonFalseOmitsTheAction() {
        UserMessages.errorNotification("cannot fetch", showReportButton = false)
        idle()
        val n = notifications().single()
        assertEquals("cannot fetch", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(n.actions.isNullOrEmpty())
    }
}
