package net.bible.sharedui.app.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocInstallStatus
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedlogic.platform.desktop.DesktopPlatformLocales
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.strings.iosStrings
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

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

    private class IntSettings(private val values: Map<String, Int> = emptyMap()) : AppSettings {
        val strings = mutableMapOf<String, String>()
        override fun getString(key: String, default: String?): String? = strings[key] ?: default
        override fun getLong(key: String, default: Long): Long = default
        override fun getInt(key: String, default: Int): Int = values[key] ?: default
        override fun getBoolean(key: String, default: Boolean): Boolean = default
        override fun getDouble(key: String, default: Double): Double = default
        override fun getFloat(key: String, default: Float): Float = default
        override fun setString(key: String, value: String?) {
            if (value == null) strings.remove(key) else strings[key] = value
        }
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

    private fun host(settings: AppSettings, language: String = "en") = DownloadHost(
        shell = shell,
        strings = iosStrings("en"),
        platform = unused(),
        notificationPermission = unused(),
        clipboard = unused(),
        openInstallZip = { _, _, _ -> },
        observeProgressJobs = { { } },
        appSettingsProvider = { settings },
        userNotifierProvider = { unused() },
        directoriesProvider = { error("unused") },
        environmentProvider = { unused() },
        coreStringsProvider = { unused() },
        downloadHttpProvider = { unused() },
        bookInstallWatcherProvider = {
            BookInstallWatcher(
                OrderedLauncher(shell.hostScope), unused(), unused(),
                DocumentSyncSettings(
                    { error("unused") }, { error("unused") }, { error("unused") },
                    OrderedLauncher(shell.hostScope), unused(),
                ),
                unused(),
            )
        },
        downloadControlProvider = { error("unused") },
        documentControlProvider = { error("unused") },
        fakeBookFactoryProvider = { error("unused") },
        customRepositoryServiceProvider = { error("unused") },
        documentSyncSettingsProvider = { error("unused") },
        platformLocalesProvider = { DesktopPlatformLocales { language } },
    )

    private val english = LangOption("en", "English", "en")
    private val finnish = LangOption("fi", "Finnish", "fi")
    private fun row(id: String, language: LangOption) = DocRow(
        id, id, id, id, language, "test", DocCategory.BIBLE, DocInstallStatus.NOT_INSTALLED,
        0, false, false, false, false, false, null,
    )
    private val rows get() = listOf(row("KJV", english), row("Fin", finnish))

    @Test
    fun firstCatalogueUsesSystemLanguage() {
        val host = host(IntSettings(), "en")
        val controller = host.downloadControllerFor(DocTypeFilter.ALL)
        host.setDownloadDocuments(rows)
        assertEquals(english, controller.selectedLanguage.value)
        assertEquals(listOf("KJV"), controller.displayed.value.map { it.docId })
    }

    @Test
    fun automaticDefaultDoesNotBecomeARememberedUserPick() {
        val settings = IntSettings()
        val first = host(settings, "en")
        first.downloadControllerFor(DocTypeFilter.ALL)
        first.setDownloadDocuments(rows)
        val afterLocaleChange = host(settings, "fi")
        val controller = afterLocaleChange.downloadControllerFor(DocTypeFilter.ALL)
        afterLocaleChange.setDownloadDocuments(rows)
        assertEquals(finnish, controller.selectedLanguage.value)
    }

    @Test
    fun pickedLanguageSurvivesANewHost() {
        val settings = IntSettings()
        val first = host(settings)
        val controller = first.downloadControllerFor(DocTypeFilter.ALL)
        first.setDownloadDocuments(rows)
        controller.setLanguage(finnish)
        val reopened = host(settings)
        val restored = reopened.downloadControllerFor(DocTypeFilter.ALL)
        reopened.setDownloadDocuments(rows)
        assertEquals(finnish, restored.selectedLanguage.value)
        assertEquals(listOf("Fin"), restored.displayed.value.map { it.docId })
    }

    @Test
    fun unavailableRememberedLanguageFallsBackToSystemThenAll() {
        val settings = IntSettings().apply { strings["selected_language_code"] = "de" }
        val host = host(settings)
        val controller = host.downloadControllerFor(DocTypeFilter.ALL)
        host.setDownloadDocuments(rows)
        assertEquals(english, controller.selectedLanguage.value)
        assertEquals("de", settings.strings["selected_language_code"])
        val unsupported = host(IntSettings(), "de")
        val all = unsupported.downloadControllerFor(DocTypeFilter.ALL)
        unsupported.setDownloadDocuments(rows)
        assertNull(all.selectedLanguage.value)
        assertEquals(2, all.resultCount.value)
    }

    @Test
    fun catalogueRefreshAndReentryDoNotResetAllOrUserLanguage() {
        val host = host(IntSettings())
        val controller = host.downloadControllerFor(DocTypeFilter.ALL)
        host.setDownloadDocuments(rows)
        controller.setLanguage(null)
        host.setDownloadDocuments(rows)
        assertNull(controller.selectedLanguage.value)
        controller.setLanguage(finnish)
        host.setDownloadDocuments(rows)
        assertEquals(finnish, controller.selectedLanguage.value)
        assertSame(controller, host.downloadControllerFor(DocTypeFilter.BIBLE))
    }

    @Test
    fun languageMatchingUsesCatalogueGroupingForIso3AndRegionVariants() {
        val settings = IntSettings().apply { strings["selected_language_code"] = "fin" }
        val host = host(settings)
        val controller = host.downloadControllerFor(DocTypeFilter.ALL)
        host.setDownloadDocuments(rows)
        assertEquals(finnish, controller.selectedLanguage.value)
        val regional = host(IntSettings(), "fi-FI")
        val regionalController = regional.downloadControllerFor(DocTypeFilter.ALL)
        regional.setDownloadDocuments(rows)
        assertEquals(finnish, regionalController.selectedLanguage.value)
    }

    @Test
    fun emptyCatalogueDoesNotConsumeInitialLanguageSelection() {
        val host = host(IntSettings(), "fi")
        val controller = host.downloadControllerFor(DocTypeFilter.ALL)
        host.setDownloadDocuments(emptyList())
        host.setDownloadDocuments(rows)
        assertEquals(finnish, controller.selectedLanguage.value)
    }

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
