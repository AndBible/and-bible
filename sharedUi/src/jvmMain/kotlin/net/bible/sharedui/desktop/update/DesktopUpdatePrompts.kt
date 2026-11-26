package net.bible.sharedui.desktop.update

import java.net.URI
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.ui.dialog.toAnswers
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
import net.bible.sharedui.settings.DesktopBetaUpdateSetting
import net.bible.sharedui.settings.DesktopUpdateCheckSetting
import net.bible.sharedui.settings.DesktopUpdateInterval
import net.bible.service.db.DatabaseContainer

private const val RELEASES_URL = "https://api.github.com/repos/AndBible/and-bible/releases?per_page=30"
private const val UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000L
private const val DAY_MS = 24 * 60 * 60 * 1000L

class DesktopUpdatePrompts(
    private val settings: DesktopDeviceSettings,
    scope: CoroutineScope,
    environment: AppEnvironment,
    version: String,
    build: Int,
    private val channel: DesktopChannel,
    private val dialogs: AppDialogController,
    private val strings: HostStringTableHolder,
    private val links: ExternalLinks,
    private val appReady: () -> Boolean = { DatabaseContainer.ready && environment.isInitialized },
    private val clock: () -> Long = System::currentTimeMillis,
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
    }, build, channel, updateScope, {}, consent = {
        when (val result = dialogs.await(AppDialogRequest.Localized {
            val table = strings.current
            AppDialogRequest.Options(table.text("desktop_update_consent_title"), table.text("desktop_update_consent_message"),
                listOf(SettingsItem.Choice("yes", table.text("desktop_update_consent_yes")),
                    SettingsItem.Choice("no", table.text("desktop_update_consent_no"))).toAnswers(),
                dismissText = null, asActionSheet = false, cancellable = false)
        })) {
            is AppDialogResult.Selected -> when (result.value) { "yes" -> true; "no" -> false; else -> null }
            else -> null
        }
    }, announceAndAwait = { release ->
        val result = dialogs.await(AppDialogRequest.Localized {
            desktopUpdateDialog(strings.current, release.tag, version)
        })
        val choice = when (result) {
            is AppDialogResult.Selected -> result.value to false
            is AppDialogResult.SelectedWithCheckbox -> result.value to result.checked
            else -> null
        }
        if (choice?.second == true) settings.putString(CONSENT_KEY, DesktopUpdateInterval.NEVER.value)
        val snoozeDays = when (choice?.first) { "snooze_day" -> 1; "snooze_week" -> 7; "snooze_month" -> 30; else -> 0 }
        if (snoozeDays > 0) settings.snoozeUntil(clock() + snoozeDays * DAY_MS)
        when (choice?.first) {
            "download" -> { links.open(release.htmlUrl); true }
            "whats_new" -> { links.open(DocsLinks.releaseNotes(release.version)); false }
            "skip" -> true
            else -> false
        }
    })

    init {
        updateScope.launch {
            while (!appReady()) delay(500)
            while (true) {
                checker.start()
                delay(UPDATE_CHECK_INTERVAL_MS)
            }
        }
    }

    override val interval: DesktopUpdateInterval get() = settings.updateInterval() ?: DesktopUpdateInterval.NEVER
    /** The offer-beta row; bound separately because a test build has none. */
    val offerBeta: DesktopBetaUpdateSetting = object : DesktopBetaUpdateSetting {
        override val enabled: Boolean
            get() = when (settings.getString(OFFER_BETA_KEY)) { "yes" -> true; "no" -> false; else -> channel.defaultOfferBeta }
        override fun setEnabled(enabled: Boolean) = settings.putString(OFFER_BETA_KEY, if (enabled) "yes" else "no")
        override fun observeChanges(onChange: () -> Unit): () -> Unit = settings.observeChanges(onChange)
    }
    override fun setInterval(interval: DesktopUpdateInterval) {
        settings.putString(CONSENT_KEY, interval.value)
        if (interval != DesktopUpdateInterval.NEVER) checker.start()
    }
    override fun observeChanges(onChange: () -> Unit): () -> Unit = settings.observeChanges(onChange)
    override fun close() = lifetime.cancel()
}
