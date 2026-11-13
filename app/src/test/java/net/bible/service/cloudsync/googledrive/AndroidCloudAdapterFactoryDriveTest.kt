package net.bible.service.cloudsync.googledrive

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidCloudAdapterFactory
import net.bible.service.cloudsync.drive.GoogleDriveRestAdapter
import net.bible.service.common.CommonUtils
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidCloudAdapterFactoryDriveTest {
    private val prefs get() = CommonUtils.realSharedPreferences

    /** Hidden pref of the phase 1-3 builds that chose the old adapter; it is no longer read. */
    private val retiredPrefKey = "google_drive_implementation"

    private fun withRetiredPref(value: String?, block: () -> Unit) {
        val old = prefs.getString(retiredPrefKey, null)
        try {
            prefs.edit().apply { if (value == null) remove(retiredPrefKey) else putString(retiredPrefKey, value) }.commit()
            block()
        } finally {
            prefs.edit().apply { if (old == null) remove(retiredPrefKey) else putString(retiredPrefKey, old) }.commit()
        }
    }

    private val factory get() = AndroidCloudAdapterFactory(ApplicationProvider.getApplicationContext())

    @Test fun `stored legacy choice still creates the shared adapter`() = withRetiredPref("legacy") {
        assertTrue(factory.create("GOOGLE_DRIVE") is GoogleDriveRestAdapter)
        assertTrue(factory.createForBackground("GOOGLE_DRIVE") is GoogleDriveRestAdapter)
    }

    @Test fun `no stored choice creates the shared adapter`() = withRetiredPref(null) {
        assertTrue(factory.create("GOOGLE_DRIVE") is GoogleDriveRestAdapter)
        assertTrue(factory.createForBackground("GOOGLE_DRIVE") is GoogleDriveRestAdapter)
    }

    @Test fun `providers share one http client`() {
        assertSame(GoogleDriveAdapterProvider.sharedClient, GoogleDriveAdapterProvider.sharedClient)
    }
}
