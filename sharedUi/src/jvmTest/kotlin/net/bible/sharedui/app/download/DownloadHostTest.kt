package net.bible.sharedui.app.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.strings.iosStrings
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadHostTest {
    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    private class IntSettings(private val values: Map<String, Int>) : AppSettings {
        override fun getString(key: String, default: String?): String? = default
        override fun getLong(key: String, default: Long): Long = default
        override fun getInt(key: String, default: Int): Int = values[key] ?: default
        override fun getBoolean(key: String, default: Boolean): Boolean = default
        override fun getDouble(key: String, default: Double): Double = default
        override fun getFloat(key: String, default: Float): Float = default
        override fun setString(key: String, value: String?) = Unit
        override fun setLong(key: String, value: Long?) = Unit
        override fun setInt(key: String, value: Int?) = Unit
        override fun setBoolean(key: String, value: Boolean?) = Unit
        override fun setDouble(key: String, value: Double?) = Unit
        override fun setFloat(key: String, value: Float?) = Unit
    }

    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            error("unexpected ${m.name}")
        } as T

    private fun host(settings: AppSettings) = DownloadHost(
        shell = shell,
        strings = iosStrings("en"),
        platform = unused(),
        notificationPermission = unused(),
        clipboard = unused(),
        openInstallZip = { _, _, _ -> },
        observeProgressJobs = { { } },
        appSettingsProvider = { settings },
        userNotifierProvider = { error("unused") },
        directoriesProvider = { error("unused") },
        environmentProvider = { error("unused") },
        coreStringsProvider = { error("unused") },
        downloadHttpProvider = { error("unused") },
        bookInstallWatcherProvider = { error("unused") },
        downloadControlProvider = { error("unused") },
        documentControlProvider = { error("unused") },
        fakeBookFactoryProvider = { error("unused") },
        customRepositoryServiceProvider = { error("unused") },
        documentSyncSettingsProvider = { error("unused") },
    )

    @Test
    fun initialTypeFilter() {
        val settings = IntSettings(mapOf("selected_document_filter_no" to 1))
        // The addons route argument wins over the stored filter.
        assertEquals(DocTypeFilter.ADDON, host(settings).initialTypeFilter(addons = true))
        // Otherwise the stored index picks the entry.
        assertEquals(DocTypeFilter.entries[1], host(settings).initialTypeFilter(addons = false))
        // An out-of-range stored index falls back to ALL.
        val outOfRange = IntSettings(mapOf("selected_document_filter_no" to 999))
        assertEquals(DocTypeFilter.ALL, host(outOfRange).initialTypeFilter(addons = false))
    }
}
