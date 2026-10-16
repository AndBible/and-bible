package net.bible.android

import android.os.Environment
import net.bible.service.common.BuildVariant
import net.bible.sharedcore.platform.AppDirectories
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContextImpl
import org.robolectric.shadows.ShadowEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidAppDirectoriesTest {
    /** Makes `getExternalFilesDir` return null, as on a device without usable external storage. */
    @Implements(className = "android.app.ContextImpl", isInAndroidSdk = false)
    class ShadowNoExternalFilesDir : ShadowContextImpl() {
        @Implementation
        override fun getExternalFilesDir(type: String?): File? = null
    }

    private val port: AppDirectories = AndroidAppDirectories()

    private fun assertAllMatchSharedConstants() {
        assertEquals(SharedConstants.internalFilesDir.path, port.internalFilesDir.toString())
        assertEquals(SharedConstants.modulesDir.path, port.modulesDir.toString())
        assertEquals(SharedConstants.manualInstallDir.path, port.manualInstallDir.toString())
        assertEquals(SharedConstants.manualInstallDir2.path, port.manualInstallDir2.toString())
        assertEquals(SharedConstants.manualReadingPlanDir.path, port.manualReadingPlanDir.toString())
    }

    @Test
    fun portMatchesSharedConstantsInStandard() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        assertAllMatchSharedConstants()
        assertEquals(
            net.bible.android.BibleApplication.application.getExternalFilesDir(null)!!.path,
            port.modulesDir.toString(),
        )
    }

    /**
     * The flavor is a compile-time BuildConfig constant, so the discrete branch can only be proven
     * under a discrete flavor build; elsewhere this checks the port against the flavor's own rule.
     */
    @Test
    fun portUsesInternalModulesInDiscrete() {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        assertAllMatchSharedConstants()
        if (BuildVariant.Appearance.isDiscrete) {
            assertEquals(port.internalFilesDir / "modules", port.modulesDir)
        }
    }

    @Test
    @Config(shadows = [ShadowNoExternalFilesDir::class])
    fun portFallsBackToInternalModulesWhenNoExternalFilesDir() {
        assertEquals(null, BibleApplication.application.getExternalFilesDir(null))
        assertAllMatchSharedConstants()
        assertEquals(port.internalFilesDir / "modules", port.modulesDir)
    }
}
