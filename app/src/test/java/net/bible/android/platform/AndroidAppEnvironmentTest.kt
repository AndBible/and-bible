package net.bible.android.platform

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import net.bible.android.TestBibleApplication
import net.bible.android.control.buildCoreModule
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.platform.AppEnvironment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class AndroidAppEnvironmentTest {
    @Test fun environmentResolvesFromFreshModuleAndPreservesPathsAndFacts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val isolated = koinApplication {
            androidContext(context)
            modules(buildCoreModule())
        }
        try {
            val env = isolated.koin.get<AppEnvironment>()
            assertEquals(CommonUtils.initialized, env.isInitialized)
            assertEquals(CommonUtils.deviceIdentifier, env.deviceIdentifier)
            assertEquals(CommonUtils.isDiscrete, env.isDiscrete)
            assertEquals(CommonUtils.isBeta, env.isBeta)
            assertEquals(CommonUtils.isDebugMode, env.isDebugMode)
            assertEquals(CommonUtils.applicationNameMedium, env.applicationNameMedium)
            assertEquals(context.packageName, env.packageName)
            assertEquals(CommonUtils.applicationVersionNumber.toLong(), env.versionNumber)
            assertEquals(CommonUtils.isMeteredNetwork, env.isMeteredNetwork)
            assertEquals(CommonUtils.megabytesFree, env.megabytesFree)
            assertEquals(CommonUtils.tmpDir.path, env.tmpDir)
            assertEquals(context.cacheDir.path, env.cacheDir)
            assertEquals(CommonUtils.dbBackupPath.path, env.dbBackupPath)
            assertEquals(context.getDatabasePath("environment-test.db").path, env.databasePath("environment-test.db"))
            assertEquals(context.applicationInfo.sourceDir, env.apkPath)
        } finally {
            isolated.close()
        }
    }

    @Test fun deletingDatabaseUsesApplicationDatabaseDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val env = AndroidAppEnvironment(context)
        val name = "environment-delete-test.db"
        context.openOrCreateDatabase(name, 0, null).close()
        try {
            assertEquals(true, env.deleteDatabase(name))
            assertEquals(false, context.getDatabasePath(name).exists())
        } finally {
            context.deleteDatabase(name)
        }
    }
}
