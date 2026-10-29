/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.sharedui.ios.reading

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.Selection
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.assembly.awaitStartupOnMain
import net.bible.sharedui.host.app.IosHostShell
import net.bible.sharedui.host.app.IosNavigator
import net.bible.sharedui.host.app.IosReadingAppInitPlatform
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.host.ReadingHostDeps
import org.koin.mp.KoinPlatform
import net.bible.sharedui.host.reading.IosReadingHost
import net.bible.sharedui.host.reading.ReadingHostPlatform

internal object NoNavigatorForHostTests : IosNavigator {
    override fun navigate(route: String) = Unit
    override fun popBackStack(): Boolean = false
    override fun popBackStackTo(route: String, inclusive: Boolean): Boolean = false
}

internal class FakeNightForHostTests(override val nightMode: Boolean) : NightModeState

/**
 * Boots the app parts the reading host reads and runs [block] over an [IosReadingHost]; tears everything down after.
 * [scope] is the host's scope (a test that awaits the host's coroutines passes an unconfined one); [llmConfigured],
 * [llmService] and [startAgent] replace the host's AI collaborators when given.
 */
internal fun withIosReadingHost(
    scope: CoroutineScope = MainScope(),
    openUrl: (String) -> Unit = {},
    statusBar: (Boolean) -> Unit = {},
    llmConfigured: (() -> Boolean)? = null,
    llmService: (() -> ReadingLlmService)? = null,
    startAgent: ((IdType, Selection, String?, IdType?) -> Unit)? = null,
    startRegenerate: ((IdType, IdType?, String?, Boolean, Boolean, IdType?) -> Unit)? = null,
    showNotice: (windowId: String, text: String) -> Unit = { _, _ -> },
    hostPlatform: ReadingHostPlatform = IosReadingHostPlatform,
    block: (host: IosReadingHost, deps: ReadingHostDeps) -> Unit,
) {
    try {
        awaitStartupOnMain { IosAppStartup.ensureStarted(
            IosAppConfig(
                root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
            ),
        ) }
        awaitStartupOnMain { IosAppStartup.openDatabases() }
        val platform = IosReadingAppInitPlatform(IosAppStartup.stringTable)
        runBlocking {
            ReadingAppInit.initialise(platform)
            ReadingAppInit.prepareData(platform)
        }
        val koin = KoinPlatform.getKoin()
        ReadingAppInit.createWindowRepository(scope)
        val p = ColorPainter(Color.Black)
        val icons = ReadingToolbarIcons(p, p, p, p, p, p, p, p, p)
        val host = IosReadingHost(
            scope, IosHostShell(scope, NoNavigatorForHostTests), koin, hostPlatform, FakeNightForHostTests(false),
            openUrl = openUrl, statusBarHidden = statusBar,
            llmConfiguredSource = llmConfigured ?: IosReadingHost.defaultLlmConfigured(koin),
            llmService = llmService ?: { koin.get() },
            startAgent = startAgent,
            startRegenerate = startRegenerate,
            showNotice = showNotice,
        )
        try {
            block(host, host.deps(icons))
        } finally {
            host.dispose()
        }
    } finally {
        scope.cancel()
        ReadingAppInit.resetForTest()
        runBlocking { IosAppStartup.resetForTest() }
    }
}
