package net.bible.android.view.compose

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.SDCARD_READ_REQUEST
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Slice 8 finding M2. `ReadingAppBootstrap.requestSdcardPermission` asks for READ_EXTERNAL_STORAGE on
 * API 23-28, and since the launcher flip the host that asks is this one -- which had no
 * `onRequestPermissionsResult`, so a deny never ran `turnOffManualInstallFolderSetting()` and the user
 * was asked again on every return from Settings.
 *
 * Driven on a NON-reading route on purpose: the result half must not depend on a reading view being
 * composed -- the request is also issued from `showFirstRunNotices`, before anything composes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostPermissionResultTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        CommonUtils.settings.removeBoolean(PREF)
        DatabaseResetter.resetDatabase()
    }

    private fun host(): NavHostComposeActivity {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.AI_TOOL_INFO),
        ).also { controllers += it }.create().get()
    }

    @Test
    fun aDeniedSdcardRequestTurnsTheRequestPreferenceOff() {
        val activity = host()
        CommonUtils.settings.setBoolean(PREF, true)

        activity.onRequestPermissionsResult(
            SDCARD_READ_REQUEST,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            intArrayOf(PackageManager.PERMISSION_DENIED),
        )

        assertFalse(
            "a DENY must turn request_sdcard_permission_pref off, or the dialog comes back on every " +
                "return from Settings (M2)",
            CommonUtils.settings.getBoolean(PREF, true),
        )
    }

    @Test
    fun aResultForAnotherRequestCodeLeavesThePreferenceAlone() {
        val activity = host()
        CommonUtils.settings.setBoolean(PREF, true)

        // 999 is CommonUtils.requestNotificationPermission's code, whose result nothing reads by design.
        activity.onRequestPermissionsResult(
            999,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            intArrayOf(PackageManager.PERMISSION_DENIED),
        )

        assertTrue(
            "only SDCARD_READ_REQUEST may touch the preference",
            CommonUtils.settings.getBoolean(PREF, false),
        )
    }

    private companion object {
        const val PREF = "request_sdcard_permission_pref"
    }
}
