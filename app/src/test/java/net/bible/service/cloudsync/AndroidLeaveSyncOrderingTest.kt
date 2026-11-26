package net.bible.service.cloudsync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicBoolean
import net.bible.android.TestBibleApplication
import net.bible.android.control.buildPlatformBindings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class AndroidLeaveSyncOrderingTest {
    @Test
    fun leaveStartsForegroundServiceSynchronouslyBeforeWorkspacePreparation() {
        val context = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        val saved = AtomicBoolean(false)
        val app = koinApplication {
            androidContext(context)
            modules(buildPlatformBindings(), module {
                single { SyncNowPorts(saveWorkspace = { saved.set(true) }, networkAvailable = { true }, noNetwork = {}) }
                single { org.koin.core.context.GlobalContext.get().get<CloudSync>() }
            })
        }
        val driver = app.koin.get<SyncTriggerDriver>()
        val global = org.koin.core.context.GlobalContext.get()
        val cloud = global.get<CloudSync>()
        val settings = global.get<net.bible.sharedcore.platform.AppSettings>()
        val category = SyncableDatabaseDefinition.BOOKMARKS
        val oldEnabled = category.isSyncEnabled(settings)
        val oldId = cloud.adapterId
        val adapterField = CloudSync::class.java.getDeclaredField("_adapter").apply { isAccessible = true }
        val oldAdapter = adapterField.get(cloud)
        val adapter = java.lang.reflect.Proxy.newProxyInstance(CloudAdapter::class.java.classLoader, arrayOf(CloudAdapter::class.java)) { _, method, _ ->
            if (method.name == "getSignedIn") true else throw java.io.IOException("offline test adapter")
        } as CloudAdapter
        try {
            cloud.adapterId = "WEBDAV"
            category.setSyncEnabled(settings, true)
            adapterField.set(cloud, adapter)
            while (shadowOf(context).nextStartedService != null) { }
            driver.onBackground()
            val intent = shadowOf(context).nextStartedService
            assertNotNull("Leave must start the service before returning to Android", intent)
            org.junit.Assert.assertEquals(SyncService.ACTION_LEAVE, intent.action)
            assertFalse("Workspace preparation must not precede the service start", saved.get())
        } finally {
            kotlinx.coroutines.runBlocking { driver.stopAndJoin() }
            driver.close()
            app.close()
            cloud.adapterId = oldId
            adapterField.set(cloud, oldAdapter)
            category.setSyncEnabled(settings, oldEnabled)
        }
    }
}
