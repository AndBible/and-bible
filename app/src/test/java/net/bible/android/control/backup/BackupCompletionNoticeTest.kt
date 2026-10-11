package net.bible.android.control.backup

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackupCompletionNoticeTest {
    private fun complete(engine: BackupControl, text: String) {
        BackupControl::class.java.getDeclaredMethod("showMessage", String::class.java).apply {
            isAccessible = true
        }.invoke(engine, text)
    }

    @Test fun completionAfterHostStopsToastsWithoutQueuingADialog() {
        val dialogs = KoinJavaComponent.get<AppDialogController>(AppDialogController::class.java)
        dialogs.cancelAll()
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        try {
            CurrentActivityHolder.activate(controller.get())
            controller.pause().stop()
            CurrentActivityHolder.deactivate(controller.get())
            ShadowToast.reset()
            complete(net.bible.test.testBackupControl(), "Restore completed")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Restore completed", ShadowToast.getTextOfLatestToast())
            assertNull(dialogs.pending.value)
        } finally {
            controller.destroy()
            dialogs.cancelAll()
            ShadowToast.reset()
        }
    }

    @Test fun foregroundCompletionPostsANonblockingDialogInsteadOfToast() {
        val dialogs = KoinJavaComponent.get<AppDialogController>(AppDialogController::class.java)
        dialogs.cancelAll()
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        try {
            CurrentActivityHolder.activate(controller.get())
            ShadowToast.reset()
            complete(net.bible.test.testBackupControl(), "Restore completed")
            val message = dialogs.pending.value!!.request as AppDialogRequest.Message
            assertEquals("Restore completed", message.message)
            assertFalse(message.cancellable)
            assertEquals(0, ShadowToast.shownToastCount())
        } finally {
            CurrentActivityHolder.deactivate(controller.get())
            controller.pause().stop().destroy()
            dialogs.cancelAll()
            ShadowToast.reset()
        }
    }
}
