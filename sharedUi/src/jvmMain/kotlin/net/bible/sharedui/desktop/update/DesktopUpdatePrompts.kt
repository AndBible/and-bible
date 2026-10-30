package net.bible.sharedui.desktop.update

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.settings.DesktopUpdateCheckSetting
import net.bible.service.db.DatabaseContainer

private const val RELEASES_URL = "https://api.github.com/repos/AndBible/and-bible/releases?per_page=30"

class DesktopUpdatePrompts(
    private val settings: DesktopDeviceSettings,
    scope: CoroutineScope,
    environment: AppEnvironment,
    version: String,
    build: Int,
    production: Boolean,
    private val dialogs: AppDialogController,
    private val strings: HostStringTableHolder,
    private val links: ExternalLinks,
    private val appReady: () -> Boolean = { DatabaseContainer.ready && environment.isInitialized },
    clock: () -> Long = System::currentTimeMillis,
    fetchOverride: (suspend () -> String)? = null,
) : DesktopUpdateCheckSetting, AutoCloseable {
    private val lifetime = Job(scope.coroutineContext[Job])
    private val updateScope = CoroutineScope(scope.coroutineContext + lifetime)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val checker = DesktopUpdateCheck(settings, clock, {
        fetchOverride?.invoke() ?: withContext(Dispatchers.IO) {
            val url = System.getProperty("andbible.updateUrl") ?: RELEASES_URL
            val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AndBible-Desktop/$version").GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() in 200..299) { "Update request failed" }
            response.body()
        }
    }, build, production, updateScope, {}, consent = {
        when (val result = dialogs.await(AppDialogRequest.Localized {
            val table = strings.current
            AppDialogRequest.Options(table.text("desktop_update_consent_title"), table.text("desktop_update_consent_message"),
                listOf(SettingsItem.Choice("yes", table.text("desktop_update_consent_yes")),
                    SettingsItem.Choice("no", table.text("desktop_update_consent_no"))),
                dismissText = null, asActionSheet = false, cancellable = false)
        })) {
            is AppDialogResult.Selected -> when (result.value) { "yes" -> true; "no" -> false; else -> null }
            else -> null
        }
    }, announceAndAwait = { release ->
        val result = dialogs.await(AppDialogRequest.Localized {
            val table = strings.current
            AppDialogRequest.Options(table.text("desktop_update_available_title"),
                table.fmt("desktop_update_available_message", release.tag, version),
                listOf(SettingsItem.Choice("download", table.text("desktop_update_download")),
                    SettingsItem.Choice("later", table.text("desktop_update_later"))),
                dismissText = null, asActionSheet = false, cancellable = false)
        })
        when (result) {
            AppDialogResult.Selected("download") -> { links.open(release.htmlUrl); true }
            AppDialogResult.Selected("later") -> true
            else -> false
        }
    })

    init {
        updateScope.launch {
            while (!appReady()) delay(500)
            checker.start()
        }
    }

    override val enabled: Boolean get() = settings.getString(CONSENT_KEY) == "yes"
    override fun setEnabled(enabled: Boolean) {
        settings.putString(CONSENT_KEY, if (enabled) "yes" else "no")
        if (enabled) checker.start()
    }
    override fun observeChanges(onChange: () -> Unit): () -> Unit = settings.observeChanges(onChange)
    override fun close() = lifetime.cancel()
}
