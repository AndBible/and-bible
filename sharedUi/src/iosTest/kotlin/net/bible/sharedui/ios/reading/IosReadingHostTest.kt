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

import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.window.WindowControl
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedui.assembly.IosAppConfig
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosAssemblyTest
import net.bible.sharedui.assembly.InMemorySecretStore
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.ios.app.IosNavigator
import net.bible.sharedui.ios.app.IosReadingAppInitPlatform
import net.bible.sharedui.reading.ReadingToolbarIcons
import org.koin.mp.KoinPlatform
import kotlin.test.Test
import kotlin.test.assertEquals


/** The iOS reading host deps build once the window repository exists, and mirror the active window. */
class IosReadingHostTest {
    private object NoNavigator : IosNavigator {
        override fun navigate(route: String) = Unit
        override fun popBackStack(): Boolean = false
        override fun popBackStackTo(route: String, inclusive: Boolean): Boolean = false
    }

    @Test
    fun depsBuildAfterWindowRepository() {
        IosAppStartup.start(
            IosAppConfig(
                root = IosAssemblyTest.sharedRoot, uiLanguage = "en",
                repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore(),
            ),
        )
        runBlocking { IosAppStartup.openDatabases() }
        val platform = IosReadingAppInitPlatform(IosAppStartup.stringTable)
        runBlocking {
            ReadingAppInit.prepareData(platform)
            ReadingAppInit.initialise(platform)
        }
        val scope = MainScope()
        try {
            val koin = KoinPlatform.getKoin()
            ReadingAppInit.createWindowRepository(scope)
            val host = IosReadingHost(scope, IosHostShell(scope, NoNavigator), koin)
            val p = ColorPainter(Color.Black)
            val deps = host.deps(ReadingToolbarIcons(p, p, p, p, p, p, p, p, p))
            assertEquals(koin.get<WindowControl>().activeWindow.id.toString(), deps.controller.layout.value.activeWindowId)
            assertEquals("Bible Study", host.navDeps {}.windowTitle)
        } finally {
            scope.cancel()
            runBlocking { IosAppStartup.resetForTest() }
        }
    }
}
