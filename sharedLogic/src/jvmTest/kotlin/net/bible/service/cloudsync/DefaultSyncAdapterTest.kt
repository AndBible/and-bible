package net.bible.service.cloudsync

import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.android.database.DocumentSyncPreferencesDao
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.sync.SyncScheduler
import net.bible.sharedcore.ui.dialog.AppDialogController
import kotlin.test.Test
import kotlin.test.assertEquals

/** A fresh install (no stored `sync_adapter`) takes the first available backend, which is Google Drive wherever it is offered (#4102). */
class DefaultSyncAdapterTest {
    private inline fun <reified T : Any> stub(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, _, _ -> null } as T

    private fun cloud(stored: String?, available: List<String>): CloudSync {
        val values = mutableMapOf<String, String>()
        if (stored != null) values["sync_adapter"] = stored
        val settings = object : AppSettings by stub<AppSettings>() {
            override fun getString(key: String, default: String?) = values[key] ?: default
            override fun getBoolean(key: String, default: Boolean) = default
            override fun setString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        }
        val factory = object : CloudAdapterFactory {
            override val available = available
            override fun displayName(id: String) = id
            override fun create(id: String): CloudAdapter = error("unused")
        }
        val env = stub<AppEnvironment>()
        val scope = CoroutineScope(Job())
        return CloudSync(AppDialogController(), stub(), env, settings, factory, SyncScheduler { CompletableDeferred(true) }, stub(), { error("unused") },
            DocumentSyncSettings({ stub<DocumentSyncPreferencesDao>() }, { error("unused") }, { error("unused") }, OrderedLauncher(scope), env))
    }

    @Test fun freshSettingsDefaultToGoogleDriveWhenOffered() =
        assertEquals("GOOGLE_DRIVE", cloud(null, listOf("GOOGLE_DRIVE", "NEXT_CLOUD", "WEBDAV")).adapterId)

    @Test fun emptyStoredValueCountsAsNeverChosen() =
        assertEquals("GOOGLE_DRIVE", cloud("", listOf("GOOGLE_DRIVE", "NEXT_CLOUD", "WEBDAV")).adapterId)

    @Test fun storedChoiceIsKept() =
        assertEquals("NEXT_CLOUD", cloud("NEXT_CLOUD", listOf("GOOGLE_DRIVE", "NEXT_CLOUD", "WEBDAV")).adapterId)

    @Test fun fallsBackWhereDriveIsUnavailable() =
        assertEquals("NEXT_CLOUD", cloud(null, listOf("NEXT_CLOUD", "WEBDAV")).adapterId)
}
