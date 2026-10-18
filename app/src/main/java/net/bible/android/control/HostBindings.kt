package net.bible.android.control

import net.bible.android.view.activity.nav.ports.AndroidClipboardText
import net.bible.android.view.activity.nav.ports.AndroidDebugActions
import net.bible.android.view.activity.nav.ports.AndroidDocumentInstallStarter
import net.bible.android.view.activity.nav.ports.AndroidExternalLinks
import net.bible.android.view.activity.nav.ports.AndroidPlatformText
import net.bible.android.view.activity.nav.ports.AndroidSettingsPlatform
import net.bible.android.view.activity.settings.AppSettingsServiceImpl
import net.bible.sharedcore.settings.AppSettingsService
import net.bible.sharedui.app.ports.SettingsPlatform
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.sharedui.app.ports.DebugActions
import net.bible.sharedui.app.ports.DocumentInstallStarter
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.PlatformText
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The app-scoped ports of the shared app root (`:sharedUi` `net.bible.sharedui.app.ports`). The host-scoped ones
 * (pickers, dialogs) need the Activity and arrive through `HostPorts` instead.
 * Builds a NEW Koin `Module` on every call (see [buildCoreModule]).
 */
fun buildHostBindings() = module {
    single<ExternalLinks> { AndroidExternalLinks() }
    single<ClipboardText> { AndroidClipboardText(androidContext()) }
    single<PlatformText> { AndroidPlatformText(androidContext()) }
    single<DebugActions> { AndroidDebugActions() }
    single<SettingsPlatform> { AndroidSettingsPlatform(androidContext()) }
    // A factory: the app settings service is host-held (one per host), never a process singleton; it caches four
    // JSword dictionary scans for its lifetime.
    factory<AppSettingsService> { AppSettingsServiceImpl() }
    single<DocumentInstallStarter> { AndroidDocumentInstallStarter(androidContext()) }
}
