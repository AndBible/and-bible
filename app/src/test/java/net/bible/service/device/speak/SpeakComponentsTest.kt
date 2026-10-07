package net.bible.service.device.speak

import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import net.bible.android.activity.SpeakWidgetManager
import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.speak.SpeakPlaybackState
import net.bible.service.common.CommonUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Android-component subscribers of [SpeakChanges], including notification teardown (F130). */
@RunWith(RobolectricTestRunner::class)
class SpeakComponentsTest : SpeakIntegrationTestBase() {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun title(text: String) =
        SpeakChange.Progress(book, book.getKey("Gen.1.1"), TextCommand(text, TextCommand.TextType.TITLE))
    private fun text(text: String) =
        SpeakChange.Progress(book, book.getKey("Gen.1.1"), TextCommand(text, TextCommand.TextType.NORMAL))

    @Test
    fun theWidgetFollowsTitlesAndResetsWhenSpeechStops() {
        val mgr = SpeakWidgetManager.instance ?: SpeakWidgetManager()
        val reset = mgr.titleForTest()
        SpeakChanges.notifyProgress(title("Romans"))
        assertEquals("Romans", mgr.titleForTest())
        SpeakChanges.notifyProgress(title(""))
        assertEquals(reset, mgr.titleForTest())
        SpeakChanges.notifyProgress(title("Romans"))
        SpeakChanges.notifyState(SpeakPlaybackState.SILENT)
        assertEquals(reset, mgr.titleForTest())
    }

    @Test
    fun aDestroyedWidgetManagerNoLongerReacts() {
        val mgr = SpeakWidgetManager.instance ?: SpeakWidgetManager()
        try {
            SpeakChanges.notifyProgress(title("Romans"))
            assertEquals("Romans", mgr.titleForTest())
            mgr.destroy()
            SpeakChanges.notifyProgress(title("Genesis"))
            assertEquals("Romans", mgr.titleForTest())
        } finally {
            if (SpeakWidgetManager.instance == null) SpeakWidgetManager()
        }
    }

    /** F130: a dead manager must not keep posting notifications. */
    @Test
    fun aDestroyedNotificationManagerPostsNothing() {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            CommonUtils.destroy()
            nm.cancelAll()
            SpeakChanges.notifyProgress(text("In the beginning"))
            SpeakChanges.notifyState(SpeakPlaybackState.SPEAKING)
            idle()
            assertEquals(0, shadowOf(nm).allNotifications.size)
        } finally {
            CommonUtils.initializeApp()
        }
    }
}
