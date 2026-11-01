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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import net.bible.sharedui.theme.LocalDisableAnimations

/**
 * The app's [NavHost]: navigation-compose's default crossfade between screens, or none while "Disable animations"
 * ([LocalDisableAnimations]) is on, so the new screen replaces the old one at once (osTicket 3499). Android's zero
 * motion-duration scale would also end the crossfade; iOS and desktop have no such scale, hence the explicit
 * transitions.
 *
 * ONE [NavHost] call whose transition lambdas read the setting when a transition starts: toggling it at runtime must
 * not rebuild the graph (the settings screen where it is toggled lives inside it). With animations on the lambdas
 * equal the library's own defaults (navigation-compose 2.9.2 `StandardDefaultNavTransitions`: 700 ms fade in and out,
 * the pop transitions reuse them, no size transform).
 */
@Composable
@kotlin.native.HiddenFromObjC
fun AbNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    val disabled by rememberUpdatedState(LocalDisableAnimations.current)
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (disabled) EnterTransition.None else fadeIn(animationSpec = tween(LIBRARY_FADE_MS))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (disabled) ExitTransition.None else fadeOut(animationSpec = tween(LIBRARY_FADE_MS))
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = enter,
        popExitTransition = exit,
        sizeTransform = null,
        builder = builder,
    )
}

private const val LIBRARY_FADE_MS = 700
