package net.bible.android.view.compose

import android.Manifest
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.test.DatabaseResetter
import net.bible.test.resetComposeUiDispatcher
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * InstallZip asks for POST_NOTIFICATIONS through a plain permission launcher: no in-app rationale dialog first
 * (unlike `CommonUtils.requestNotificationPermission`, which the shared `NotificationPermission` port uses).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [33])
class InstallZipNotificationPermissionTest {
    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    @Test
    fun installZipAsksWithoutARationaleDialog() {
        resetComposeUiDispatcher()
        firstTime = false
        val activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.BACKUP),
        ).also { controllers += it }.create().start().resume().visible().get()
        net.bible.test.awaitHostRoute(activity, NavRoutes.BACKUP)
        val shadow = shadowOf(activity)
        shadow.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(activity.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)

        activity.openInstallZip()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        val dialogs = KoinJavaComponent.get<AppDialogController>(AppDialogController::class.java)
        assertNull("no rationale dialog is raised", dialogs.pending.value)
        assertTrue(
            "POST_NOTIFICATIONS was requested",
            shadow.lastRequestedPermission?.requestedPermissions?.contains(Manifest.permission.POST_NOTIFICATIONS) == true,
        )
    }
}
