package net.bible.android.platform

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.cloudsync.drive.GoogleDriveRestAdapter
import net.bible.service.cloudsync.googledrive.GoogleDriveCloudAdapter
import net.bible.service.common.CommonUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidCloudAdapterFactoryDriveTest {
    private val prefs get() = CommonUtils.realSharedPreferences

    private fun withOverride(value: String?, block: () -> Unit) {
        val old = prefs.getString(DriveImplementation.PREF_KEY, null)
        try {
            prefs.edit().apply { if (value == null) remove(DriveImplementation.PREF_KEY) else putString(DriveImplementation.PREF_KEY, value) }.commit()
            block()
        } finally {
            prefs.edit().apply { if (old == null) remove(DriveImplementation.PREF_KEY) else putString(DriveImplementation.PREF_KEY, old) }.commit()
        }
    }

    private val factory get() = AndroidCloudAdapterFactory(ApplicationProvider.getApplicationContext())

    @Test fun `shared override creates the shared adapter`() = withOverride(DriveImplementation.SHARED) {
        assertTrue(factory.create("GOOGLE_DRIVE") is GoogleDriveRestAdapter)
    }

    @Test fun `legacy override creates the old adapter`() = withOverride(DriveImplementation.LEGACY) {
        assertTrue(factory.create("GOOGLE_DRIVE") is GoogleDriveCloudAdapter)
    }
}
