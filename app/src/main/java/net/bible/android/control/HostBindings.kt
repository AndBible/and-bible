package net.bible.android.control

import net.bible.android.view.activity.nav.ports.AndroidClipboardText
import net.bible.android.view.activity.nav.ports.AndroidDebugActions
import net.bible.android.view.activity.nav.ports.AndroidDocumentInstallStarter
import net.bible.android.view.activity.nav.ports.AndroidExternalLinks
import net.bible.android.view.activity.nav.ports.AndroidPlatformText
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
    single<DocumentInstallStarter> { AndroidDocumentInstallStarter(androidContext()) }
}
