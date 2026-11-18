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

package net.bible.android.view.compose.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedui.reading.SplitContent
import net.bible.sharedui.reading.WindowTabBar
import net.bible.sharedui.reading.host.paneButtonShown
import net.bible.sharedui.theme.isPureMonochrome
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy

/**
 * #4141: a maximised window ("M") with its links window ("L") open beside it. The layout carries a third, hidden window
 * ("O") as the repository's snapshot does in maximised mode, so the capture shows that only M and L are laid out, in
 * the normal split's orientation (stacked on a portrait phone, side by side in landscape). The stub window button is
 * drawn where the real host's per-pane gate ([paneButtonShown]) allows one: on L only. The rail is the real
 * [WindowTabBar] built from the same layout, showing its maximised state (the restore-all button).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class MaximisedLinksPaneGoldenTest {
    private fun win(id: String, links: Boolean, visible: Boolean) = WindowSnapshot(
        id = id, state = if (visible) WindowStateValue.VISIBLE else WindowStateValue.MINIMISED, weight = 1f,
        isVisible = visible, isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = links,
    )

    private val layout = WindowLayoutState(
        windows = listOf(win("M", false, true), win("O", false, false), win("L", true, true)),
        activeWindowId = "L", maximizedWindowId = "M", reverseSplitMode = false, restoreButtonsVisible = true,
    )

    private val settings = Proxy.newProxyInstance(
        AppSettings::class.java.classLoader, arrayOf(AppSettings::class.java),
    ) { _, _, args -> args?.getOrNull(1) } as AppSettings

    private val pane: @Composable (String) -> Unit = { id ->
        val color = if (isPureMonochrome()) MaterialTheme.colorScheme.background else when (id) {
            "M" -> MaterialTheme.colorScheme.primaryContainer
            "L" -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.errorContainer
        }
        Box(Modifier.fillMaxSize().background(color), Alignment.Center) { Text(id) }
    }

    private val buttonStub: @Composable BoxScope.(String) -> Unit = { id ->
        if (paneButtonShown(settings, layout, id)) {
            Box(Modifier.align(Alignment.TopEnd).size(40.dp).background(MaterialTheme.colorScheme.tertiary), Alignment.Center) {
                Text("☰", color = MaterialTheme.colorScheme.onTertiary)
            }
        }
    }

    private val screen: @Composable () -> Unit = {
        SplitContent(
            layout, {}, { _, _, _, _ -> }, pane,
            paneOverlay = buttonStub,
            railOverlay = {
                WindowTabBar(
                    model = buildWindowTabBar(layout),
                    onRestore = {}, onWindowLongPress = {}, onAddWindow = {}, onUnMaximise = {}, onToggleCollapse = {},
                    windowLabel = { it.id },
                )
            },
        )
    }

    @Test
    fun portraitStacked() {
        listOf(GoldenMode.LIGHT, GoldenMode.DARK).forEach { mode ->
            captureGolden("MaximisedLinksPane", "portrait", mode, content = screen)
        }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun landscapeSideBySide() {
        listOf(GoldenMode.LIGHT, GoldenMode.DARK).forEach { mode ->
            captureGolden("MaximisedLinksPane", "landscape", mode, content = screen)
        }
    }
}
