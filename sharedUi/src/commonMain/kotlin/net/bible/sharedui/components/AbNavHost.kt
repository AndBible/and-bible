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

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import net.bible.sharedui.theme.LocalDisableAnimations

/**
 * The app's [NavHost]: navigation-compose's default crossfade between screens, or none while "Disable animations"
 * ([LocalDisableAnimations]) is on, so the new screen replaces the old one at once (osTicket 3499). Android's zero
 * motion-duration scale would also end the crossfade; iOS and desktop have no such scale, hence the explicit
 * transitions. With animations on the call is the plain library default, unchanged.
 */
@Composable
@kotlin.native.HiddenFromObjC
fun AbNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    if (LocalDisableAnimations.current) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            sizeTransform = null,
            builder = builder,
        )
    } else {
        NavHost(navController = navController, startDestination = startDestination, modifier = modifier, builder = builder)
    }
}
