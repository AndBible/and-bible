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
package net.bible.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.control.event.window.WorkspaceChanged
import net.bible.android.control.event.window.WorkspaceColorChanged
import net.bible.android.control.page.window.WindowControl
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedui.theme.AbTheme
import org.koin.core.context.GlobalContext

/**
 * The experimental switch that keeps the reading toolbar's LITERAL workspace colour instead of
 * the theme-derived one (§6). Derived is the default (maintainer decision, batch 4b feedback
 * 2026-08-01; made unconditional 2026-09-18 when the former master switch,
 * `workspace_color_theme`, was retired -- see `docs/superpowers/status/compose-port-status.md`); this is the opt-out
 * back to the literal colour, not an opt-in to deriving it.
 */
const val TOOLBAR_LITERAL_COLOR_FEATURE = "toolbar_literal_color"

/** [workspaceThemeSeedArgb] applied to the live singleton: the active workspace's own colour. */
fun currentWorkspaceThemeSeedArgb(): Int? = workspaceThemeSeedArgb(
    // This is the repo's idiom for a non-DI singleton lookup outside a Koin-injected constructor —
    // e.g. `net.bible.service.llm.tools.write.ManageWindowTool.windowControl` does the same
    // `GlobalContext.get().get<WindowControl>()` — not `org.koin.java.KoinJavaComponent.get`, which
    // this repo does not otherwise use.
    workspaceColorArgb = GlobalContext.get().get<WindowControl>()
        .windowRepository.workspaceSettings.workspaceColor,
)

/**
 * The seed IS the workspace colour (2026-09-18: the master switch that used to gate this,
 * `workspace_color_theme`, is retired -- workspace-colour theming is always on). Kept as its own
 * pure function so it stays unit-testable without Koin or a Context.
 */
fun workspaceThemeSeedArgb(workspaceColorArgb: Int?): Int? = workspaceColorArgb

/**
 * The reading toolbar's colour rule, as a pure function so it can be unit-tested without Koin, a
 * Context or [net.bible.android.control.page.toolbar.ToolbarStateServiceImpl]'s four collaborators
 * (A/B batch 4b feedback, 2026-08-01). Derived is the DEFAULT (unconditionally since the
 * 2026-09-18 master-switch retirement); [TOOLBAR_LITERAL_COLOR_FEATURE] is the one remaining
 * opt-OUT back to the literal workspace colour, not an opt-in to deriving it.
 */
fun deriveToolbarFromTheme(enabledFeatures: Set<String>): Boolean =
    TOOLBAR_LITERAL_COLOR_FEATURE !in enabledFeatures

/**
 * The one place `:app` reads the app-global theme inputs.
 *
 * Every Compose host used to repeat the same four reads (night mode, colour mode, animations) plus
 * `ProvideAppLocals`; none of them is a per-host decision, and A/B batch 4b added a fourth input
 * (the workspace-colour seed) that would have had to be pasted into 47 more places. `AbTheme`
 * itself stays pure and explicit — it is the iOS- and golden-facing API and must not learn about
 * Android settings. `AbThemeHostGuardTest` keeps hosts from going around this.
 *
 * The seed is held in state and refreshed on [WorkspaceColorChanged] (the event batch 4a added and
 * every writer of the workspace colour posts) and on [WorkspaceChanged] (batch 5: a workspace
 * switch replaces `workspaceSettings` wholesale and posts no colour event of its own, so without
 * this the theme kept the previous workspace's seed after a switch), so changing the colour or
 * switching workspaces re-themes the visible UI without recreating the Activity. Not unit-tested:
 * the `DisposableEffect` subscription itself is
 * composition machinery with no state seam to assert on; it is verified on the device during the
 * A/B round (change the workspace colour, watch the UI re-theme without leaving the screen).
 *
 * @param darkTheme night-mode override. `null` (the default, and every host but one) reads
 * [ScreenSettings.nightMode] fresh here, which is safe because every other host is an `Activity`
 * that gets `recreate()`d whenever night mode changes (see `ActivityBase`). The one exception is
 * `ComposeReadingViewHost` (Task 3 fix round 1, batch 4b): it is long-lived inside
 * `MainBibleActivity` and is never `recreate()`d — including on the ambient-light-sensor
 * auto-night-mode flip, which fires `ScreenSettings.NightModeChanged` with no recreate at all — so
 * it tracks night mode itself in a live `State<Boolean>` and must pass that value through verbatim
 * instead of letting this function re-read the static getter (which would only catch up on some
 * unrelated recomposition). This keeps the "read the app-global inputs in one place" rule intact
 * for colour mode / animations / the workspace-colour seed while still letting the one host with a
 * genuinely different night-mode source use them.
 */
@Composable
fun AbAppTheme(darkTheme: Boolean? = null, content: @Composable () -> Unit) {
    var seedArgb by remember { mutableStateOf(currentWorkspaceThemeSeedArgb()) }
    DisposableEffect(Unit) {
        val subscriber = Any()
        ABEventBus.register(subscriber) {
            onMain<WorkspaceColorChanged> { seedArgb = currentWorkspaceThemeSeedArgb() }
            // A workspace switch replaces workspaceSettings wholesale and posts no colour event, so
            // without this the UI keeps the previous workspace's seed.
            onMain<WorkspaceChanged> { seedArgb = currentWorkspaceThemeSeedArgb() }
        }
        onDispose { ABEventBus.unregister(subscriber) }
    }
    ProvideAppLocals {
        AbTheme(
            seedArgb = seedArgb,
            darkTheme = darkTheme ?: ScreenSettings.nightMode,
            colorMode = CommonUtils.settings.displayColorMode,
            disableAnimations = CommonUtils.settings.disableAnimations,
            content = content,
        )
    }
}
