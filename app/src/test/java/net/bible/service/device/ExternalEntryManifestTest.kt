package net.bible.service.device

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ErrorActivity
import net.bible.android.view.activity.installzip.InstallZipComposeActivity
import net.bible.service.common.BuildVariant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Check the merged variant manifest, not just intent filters (explicit intents bypass filters). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [33])
class ExternalEntryManifestTest {
    private val app = ApplicationProvider.getApplicationContext<TestBibleApplication>()

    @Test fun moduleInstallerIsExternalOnlyInStandard() {
        val info = app.packageManager.getActivityInfo(ComponentName(app, InstallZipComposeActivity::class.java), 0)
        assertEquals(!BuildVariant.Appearance.isDiscrete, info.exported)
    }

    @Test fun errorReporterIsExternalOnlyInStandard() {
        val info = app.packageManager.getActivityInfo(ComponentName(app, ErrorActivity::class.java), 0)
        assertEquals(!BuildVariant.Appearance.isDiscrete, info.exported)
    }

    @Test fun standardShareToInstallRemainsResolvable() {
        val matches = app.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SEND).setType("application/zip").addCategory(Intent.CATEGORY_DEFAULT),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        assertEquals(!BuildVariant.Appearance.isDiscrete,
            matches.any { it.activityInfo.name == InstallZipComposeActivity::class.java.name && it.activityInfo.exported })
    }
}
